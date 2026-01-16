import base64
import contextlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
import textwrap
import traceback
import uuid
from typing import Any, Dict, Iterable, List, Optional, Tuple

CODE_FENCE_PATTERN = re.compile(r"```(?:python|py)?\s*([\s\S]*?)\s*```", re.IGNORECASE)

_EXT_TO_MIME = {
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".gif": "image/gif",
    ".webp": "image/webp",
    ".bmp": "image/bmp",
}

_AUTO_CAPTURE_VAR_NAMES = ("img", "image", "fig", "figure")


def _configure_matplotlib_cjk_font() -> None:
    try:
        import matplotlib.pyplot as plt  # noqa: WPS433
        from matplotlib import font_manager  # noqa: WPS433
    except Exception:
        return

    font_candidates = [
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansSC-Regular.otf",
        "/system/fonts/NotoSansSC-Regular.ttf",
        "/system/fonts/NotoSansCJKsc-Regular.otf",
        "/system/fonts/NotoSansCJKsc-Regular.ttc",
        "/system/fonts/DroidSansFallback.ttf",
        "/system/fonts/DroidSansFallbackFull.ttf",
    ]

    try:
        for file_name in os.listdir("/system/fonts"):
            if "NotoSansCJK" in file_name or "DroidSansFallback" in file_name:
                font_candidates.append(os.path.join("/system/fonts", file_name))
    except Exception:
        pass

    for path in font_candidates:
        if not os.path.exists(path):
            continue
        try:
            font_manager.fontManager.addfont(path)
        except Exception:
            pass
        try:
            font_name = font_manager.FontProperties(fname=path).get_name()
        except Exception:
            continue

        plt.rcParams["font.family"] = "sans-serif"
        plt.rcParams["font.sans-serif"] = [font_name]
        plt.rcParams["axes.unicode_minus"] = False
        return

    try:
        plt.rcParams["axes.unicode_minus"] = False
    except Exception:
        pass


def _inject_default_imports(namespace: Dict[str, Any]) -> None:
    import datetime  # noqa: WPS433
    import math  # noqa: WPS433
    import random  # noqa: WPS433
    import time  # noqa: WPS433

    namespace.setdefault("os", os)
    namespace.setdefault("sys", sys)
    namespace.setdefault("json", json)
    namespace.setdefault("re", re)
    namespace.setdefault("math", math)
    namespace.setdefault("random", random)
    namespace.setdefault("time", time)
    namespace.setdefault("datetime", datetime)

    try:
        import numpy as np  # noqa: WPS433

        namespace.setdefault("np", np)
    except Exception:
        pass

    try:
        import pandas as pd  # noqa: WPS433

        namespace.setdefault("pd", pd)
    except Exception:
        pass

    try:
        from PIL import Image  # noqa: WPS433

        namespace.setdefault("Image", Image)
    except Exception:
        pass

    try:
        os.environ.setdefault("MPLBACKEND", "Agg")
        import matplotlib  # noqa: WPS433

        try:
            matplotlib.use("Agg")
        except Exception:
            pass

        import matplotlib.pyplot as plt  # noqa: WPS433

        namespace.setdefault("plt", plt)
        _configure_matplotlib_cjk_font()
    except Exception:
        pass

    try:
        import seaborn as sns  # noqa: WPS433

        namespace.setdefault("sns", sns)
    except Exception:
        pass


def _to_base64_payload(mime: str, data: bytes) -> Tuple[str, str, int]:
    base64_data = base64.b64encode(data).decode("utf-8")
    fmt = mime.split("/", 1)[1] if "/" in mime else "png"
    return base64_data, fmt, len(data)


def _parse_data_url(data_url: str) -> Optional[Tuple[str, str, int]]:
    if not data_url.startswith("data:"):
        return None
    header, _, base64_part = data_url.partition("base64,")
    if not base64_part:
        return None
    mime = header[5:].split(";", 1)[0] if header else ""
    if not mime.startswith("image/"):
        return None
    base64_part = re.sub(r"\s+", "", base64_part)
    size = _approx_base64_decoded_size(base64_part)
    if size <= 0:
        return None
    fmt = mime.split("/", 1)[1] if "/" in mime else "png"
    return base64_part, fmt, size


def _approx_base64_decoded_size(base64_text: str) -> int:
    compact = re.sub(r"\s+", "", base64_text)
    if not compact:
        return 0
    padding = 2 if compact.endswith("==") else 1 if compact.endswith("=") else 0
    return max(0, (len(compact) * 3) // 4 - padding)


def _is_supported_image_path(path: str) -> bool:
    _, ext = os.path.splitext(path.lower())
    return ext in _EXT_TO_MIME


def _looks_like_image_file(path: str) -> bool:
    _, ext = os.path.splitext(path.lower())
    if ext not in _EXT_TO_MIME:
        return False
    try:
        with open(path, "rb") as fp:
            head = fp.read(32)
    except Exception:
        return False

    if ext == ".png":
        return head.startswith(b"\x89PNG\r\n\x1a\n")
    if ext in (".jpg", ".jpeg"):
        return head.startswith(b"\xff\xd8\xff")
    if ext == ".gif":
        return head.startswith(b"GIF87a") or head.startswith(b"GIF89a")
    if ext == ".bmp":
        return head.startswith(b"BM")
    if ext == ".webp":
        return head.startswith(b"RIFF") and len(head) >= 12 and head[8:12] == b"WEBP"

    return False


def _encode_image_file(path: str) -> Optional[Tuple[str, str, int]]:
    if not _is_supported_image_path(path):
        return None
    if not _looks_like_image_file(path):
        return None

    try:
        file_size = os.path.getsize(path)
    except Exception:
        return None

    if file_size <= 0:
        return None

    try:
        with open(path, "rb") as fp:
            data = fp.read()
    except Exception:
        return None

    mime = _EXT_TO_MIME.get(os.path.splitext(path.lower())[1], "image/png")
    return _to_base64_payload(mime, data)


def _encode_image(image: Any) -> Optional[Tuple[str, str, int]]:
    """Convert different image-like objects (Pillow/matplotlib/plotly/numpy) to base64 payload."""
    if image is None:
        return None

    if isinstance(image, (bytes, bytearray)):
        return _to_base64_payload("image/png", bytes(image))

    if isinstance(image, str):
        if image.startswith("data:image"):
            parsed = _parse_data_url(image)
            if parsed:
                return parsed

        if os.path.exists(image) and _is_supported_image_path(image):
            return _encode_image_file(image)

        try:
            # Validate the string is base64 and keep it as a payload.
            compact = re.sub(r"\s+", "", image)
            data = base64.b64decode(compact, validate=True)
            if data:
                return compact, "png", len(data)
        except Exception:
            return None

    try:
        from PIL import Image  # noqa: WPS433
    except Exception:
        Image = None

    # Plotly figure
    if hasattr(image, "to_image"):
        try:
            png_bytes = image.to_image(format="png")
            if isinstance(png_bytes, str):
                png_bytes = png_bytes.encode("utf-8")
            if isinstance(png_bytes, (bytes, bytearray)) and png_bytes:
                return _to_base64_payload("image/png", bytes(png_bytes))
        except Exception:
            pass

    # Pillow image
    try:
        if Image is not None and isinstance(image, Image.Image):
            buffer = io.BytesIO()
            image.save(buffer, format="PNG")
            data = buffer.getvalue()
            if data:
                return _to_base64_payload("image/png", data)
    except Exception:
        pass

    # numpy ndarray
    try:
        import numpy as np  # noqa: WPS433
    except Exception:
        np = None

    if np is not None and isinstance(image, np.ndarray) and Image is not None:
        try:
            arr = image
            if arr.dtype != np.uint8:
                finite = np.nan_to_num(arr)
                min_val = finite.min()
                max_val = finite.max()
                if max_val > min_val:
                    scaled = ((finite - min_val) / (max_val - min_val) * 255).clip(0, 255)
                else:
                    scaled = np.zeros_like(finite)
                arr = scaled.astype(np.uint8)
            if arr.ndim == 2:
                mode = "L"
            elif arr.ndim == 3 and arr.shape[2] == 4:
                mode = "RGBA"
            else:
                mode = "RGB"
            image_obj = Image.fromarray(arr, mode=mode)
            buffer = io.BytesIO()
            image_obj.save(buffer, format="PNG")
            data = buffer.getvalue()
            if data:
                return _to_base64_payload("image/png", data)
        except Exception:
            pass

    # Matplotlib figure-like object
    if hasattr(image, "savefig"):
        buffer = io.BytesIO()
        image.savefig(buffer, format="png")
        data = buffer.getvalue()
        if data:
            return _to_base64_payload("image/png", data)

    return None


def _encode_images(images: Iterable[Any]) -> List[Tuple[str, str, int]]:
    encoded: List[Tuple[str, str, int]] = []
    for image in images:
        encoded_image = _encode_image(image)
        if encoded_image:
            encoded.append(encoded_image)
    return encoded


def _capture_matplotlib_figures() -> List[Tuple[str, str, int]]:
    """Capture all current matplotlib figures as base64 payloads."""
    try:
        import matplotlib.pyplot as plt  # noqa: WPS433
    except Exception:
        return []

    captured: List[Tuple[str, str, int]] = []
    try:
        for fig_num in plt.get_fignums():
            fig = plt.figure(fig_num)
            encoded = _encode_image(fig)
            if encoded:
                captured.append(encoded)
    finally:
        try:
            plt.close("all")
        except Exception:
            pass

    return captured


def _collect_output_dir_images(output_dir: str) -> List[Tuple[Tuple[str, str, int], str]]:
    candidates: List[Tuple[float, str]] = []
    try:
        for root, _, files in os.walk(output_dir):
            for file_name in files:
                path = os.path.join(root, file_name)
                if not _is_supported_image_path(path):
                    continue
                try:
                    mtime = os.path.getmtime(path)
                except Exception:
                    continue
                candidates.append((mtime, path))
    except Exception:
        return []

    # newest first
    candidates.sort(key=lambda it: it[0], reverse=True)
    encoded: List[Tuple[Tuple[str, str, int], str]] = []
    for _, path in candidates:
        encoded_image = _encode_image_file(path)
        if encoded_image:
            encoded.append((encoded_image, path))
    return encoded


def _select_images(
    candidates: List[Dict[str, Any]],
    max_images: Optional[int] = None,
    max_total_bytes: Optional[int] = None,
) -> List[Dict[str, Any]]:
    selected: List[Dict[str, Any]] = []
    total_bytes = 0

    for candidate in candidates:
        size = candidate.get("_size", 0)
        if max_images is not None and len(selected) >= max_images:
            continue
        if max_total_bytes is not None and size > 0 and total_bytes + size > max_total_bytes:
            continue
        selected.append(candidate)
        if size > 0:
            total_bytes += size

    return selected


def _dedupe_candidates(candidates: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    seen = set()
    unique: List[Dict[str, Any]] = []
    for candidate in candidates:
        data = candidate.get("data")
        if not data or data in seen:
            continue
        seen.add(data)
        unique.append(candidate)
    return unique


def _make_image_candidate(
    payload: Optional[Tuple[str, str, int]],
    alt_text: str,
) -> Optional[Dict[str, Any]]:
    if not payload:
        return None
    data, fmt, size = payload
    if not data:
        return None
    return {
        "type": "base64",
        "format": fmt,
        "data": data,
        "alt_text": alt_text,
        "_size": size,
    }


def _alt_text_from_value(value: Any, fallback: str) -> str:
    if isinstance(value, str) and os.path.exists(value):
        name = os.path.basename(value)
        return name if name else fallback
    return fallback


def _collect_output_dir_files(output_dir: str) -> List[str]:
    files: List[str] = []
    try:
        for root, _, file_names in os.walk(output_dir):
            for file_name in file_names:
                path = os.path.join(root, file_name)
                if _is_supported_image_path(path):
                    continue
                files.append(path)
    except Exception:
        return []
    return files


def _normalize_output_path(
    output_path: Optional[str],
    output_base_dir: Optional[str],
    output_dir: str,
) -> Optional[str]:
    if not output_path:
        return None
    path = str(output_path).strip()
    if not path:
        return None
    path = os.path.expandvars(os.path.expanduser(path))
    if not os.path.isabs(path):
        base_dir = output_base_dir or output_dir
        path = os.path.join(base_dir, path)
    return path


def _persist_output_files_to_path(
    output_dir: str,
    file_paths: List[str],
    output_path: str,
) -> List[Dict[str, str]]:
    trailing_sep = output_path.endswith(("/", "\\")) or output_path.endswith(os.sep)
    is_dir = os.path.isdir(output_path) or len(file_paths) != 1 or trailing_sep

    if is_dir:
        try:
            os.makedirs(output_path, exist_ok=True)
        except Exception:
            return []

        persisted: List[Dict[str, str]] = []
        for path in file_paths:
            try:
                rel_path = os.path.relpath(path, output_dir)
            except Exception:
                rel_path = os.path.basename(path)
            dest_path = os.path.join(output_path, rel_path)
            try:
                os.makedirs(os.path.dirname(dest_path), exist_ok=True)
                shutil.copy2(path, dest_path)
                persisted.append(
                    {
                        "path": os.path.abspath(dest_path),
                        "name": os.path.basename(dest_path),
                    }
                )
            except Exception:
                continue
        return persisted

    dest_path = output_path
    dest_dir = os.path.dirname(dest_path)
    if dest_dir:
        try:
            os.makedirs(dest_dir, exist_ok=True)
        except Exception:
            return []

    try:
        shutil.copy2(file_paths[0], dest_path)
    except Exception:
        return []
    return [{"path": os.path.abspath(dest_path), "name": os.path.basename(dest_path)}]


def _persist_output_files(
    output_dir: str,
    file_paths: List[str],
    output_base_dir: Optional[str],
    output_path: Optional[str] = None,
) -> List[Dict[str, str]]:
    if not file_paths:
        return []

    if output_path:
        return _persist_output_files_to_path(output_dir, file_paths, output_path)

    if not output_base_dir:
        return []

    try:
        os.makedirs(output_base_dir, exist_ok=True)
    except Exception:
        return []

    run_id = uuid.uuid4().hex
    dest_root = os.path.join(output_base_dir, run_id)
    try:
        os.makedirs(dest_root, exist_ok=True)
    except Exception:
        return []

    persisted: List[Dict[str, str]] = []
    for path in file_paths:
        try:
            rel_path = os.path.relpath(path, output_dir)
        except Exception:
            rel_path = os.path.basename(path)
        dest_path = os.path.join(dest_root, rel_path)
        try:
            os.makedirs(os.path.dirname(dest_path), exist_ok=True)
            shutil.copy2(path, dest_path)
            persisted.append(
                {
                    "path": os.path.abspath(dest_path),
                    "name": os.path.basename(dest_path),
                }
            )
        except Exception:
            continue
    return persisted


def _build_tool_response(
    error_message: Optional[str],
    stdout: str,
    stderr: str,
    result: Any = None,
    images: Optional[List[Dict[str, Any]]] = None,
    files: Optional[List[Dict[str, str]]] = None,
) -> str:
    payload = {
        "error_message": error_message,
        "output": {
            "stdout": stdout or "",
            "stderr": stderr or "",
            "result": result,
            "images": images or [],
            "files": files or [],
        },
    }
    return json.dumps(payload, default=_safe_default)


def _safe_default(value: Any) -> str:
    """Fallback serializer to keep JSON encoding resilient."""
    return str(value)


def _normalize_code(raw_code: Any) -> str:
    """
    Accept friendlier input formats (markdown code fences, language prefixes) and flatten indentation.
    The local tools caller often wraps Python in ```python ```; we strip those and dedent the code.
    """
    code = str(raw_code).replace("\r\n", "\n").strip()
    fence_match = CODE_FENCE_PATTERN.search(code)
    if fence_match:
        code = fence_match.group(1)
    elif code.startswith("`") and code.endswith("`") and len(code) > 1:
        code = code.strip("`")

    lowered = code.lower()
    if lowered.startswith("python") or lowered.startswith("py"):
        newline_index = code.find("\n")
        code = code[newline_index + 1 :] if newline_index != -1 else ""

    return textwrap.dedent(code).strip()


def run_python_tool(
    code: str,
    output_base_dir: Optional[str] = None,
    output_path: Optional[str] = None,
) -> str:
    """
    Execute user-provided Python code.

    Expected variables set by the code:
    - result: any serializable object returned to the caller.
    - image / images: Pillow image, matplotlib/plotly figure, numpy array, raw base64 string, or bytes.
    If no image/images are provided, current matplotlib figures (if any) will be captured automatically.
    Runtime variables:
    - OUTPUT_DIR: temp working directory for file outputs.
    - OUTPUT_PATH: resolved target path when output_path is provided.
    Images saved to files (png/jpg/webp/gif/bmp) in OUTPUT_DIR (current working directory) will be captured automatically.
    Non-image files saved in OUTPUT_DIR will be copied to output_path when provided; otherwise to output_base_dir.
    Common imports are preloaded: np, plt, pd, sns, Image.
    Matplotlib will try to auto-configure a CJK font for Chinese text when available.
    Pre-installed libraries: pillow, numpy, matplotlib, pandas, seaborn.

    Returns a JSON string with shape:
    {
        "error_message": str | null,
        "output": {
            "stdout": str,
            "stderr": str,
            "result": any,
            "images": [{ "type": "base64", "format": "png", "data": "...", "alt_text": "..." }],
            "files": [{ "path": "...", "name": "..." }]
        }
    }
    """
    # Use a shared namespace for globals/locals so that `import` and definitions
    # work like a normal Python module environment (e.g. functions can access
    # imported names).
    namespace: Dict[str, Any] = {
        "__builtins__": __builtins__,
        "__name__": "__main__",
        "__package__": None,
    }
    stdout_buffer = io.StringIO()
    stderr_buffer = io.StringIO()
    try:
        with tempfile.TemporaryDirectory(prefix="rikkahub_pytool_") as output_dir:
            resolved_output_path = _normalize_output_path(output_path, output_base_dir, output_dir)
            namespace["OUTPUT_DIR"] = output_dir
            namespace["OUTPUT_PATH"] = resolved_output_path
            namespace["WORKDIR"] = output_dir
            namespace["CWD"] = os.getcwd()

            try:
                if "matplotlib.pyplot" in sys.modules:
                    import matplotlib.pyplot as plt  # noqa: WPS433

                    plt.close("all")
            except Exception:
                pass

            normalized_code = _normalize_code(code)
            if not normalized_code:
                raise ValueError("Python code is empty after normalization")

            original_cwd = os.getcwd()
            try:
                os.chdir(output_dir)
            except Exception:
                pass

            try:
                _inject_default_imports(namespace)
                with contextlib.redirect_stdout(stdout_buffer), contextlib.redirect_stderr(stderr_buffer):
                    try:
                        exec(normalized_code, namespace, namespace)
                    except SyntaxError as syntax_error:
                        stripped = normalized_code.lstrip()
                        if stripped.startswith("return "):
                            exec("result = " + stripped[len("return ") :], namespace, namespace)
                        else:
                            raise syntax_error
            finally:
                try:
                    os.chdir(original_cwd)
                except Exception:
                    pass

            candidates: List[Dict[str, Any]] = []

            single_image = namespace.get("image")
            if isinstance(single_image, str) and single_image and not os.path.isabs(single_image):
                relative_path = os.path.join(output_dir, single_image)
                if os.path.exists(relative_path):
                    single_image = relative_path
            encoded_single = _encode_image(single_image)
            single_candidate = _make_image_candidate(
                encoded_single,
                _alt_text_from_value(single_image, "Image"),
            )
            if single_candidate:
                candidates.append(single_candidate)

            images_var = namespace.get("images")
            if isinstance(images_var, (list, tuple)):
                for index, image in enumerate(images_var, start=1):
                    encoded = _encode_image(image)
                    candidate = _make_image_candidate(
                        encoded,
                        _alt_text_from_value(image, f"Image {index}"),
                    )
                    if candidate:
                        candidates.append(candidate)

            if not candidates:
                for var_name in _AUTO_CAPTURE_VAR_NAMES:
                    if var_name not in namespace:
                        continue
                    value = namespace.get(var_name)
                    encoded = _encode_image(value)
                    candidate = _make_image_candidate(
                        encoded,
                        _alt_text_from_value(value, f"{var_name} image"),
                    )
                    if candidate:
                        candidates.append(candidate)

            for index, payload in enumerate(_capture_matplotlib_figures(), start=1):
                candidate = _make_image_candidate(payload, f"Matplotlib Plot {index}")
                if candidate:
                    candidates.append(candidate)

            for payload, path in _collect_output_dir_images(output_dir):
                candidate = _make_image_candidate(payload, os.path.basename(path) or "Image")
                if candidate:
                    candidates.append(candidate)

            candidates = _dedupe_candidates(candidates)
            images = _select_images(candidates)
            images_output = [
                {key: value for key, value in candidate.items() if key != "_size"}
                for candidate in images
            ]

            file_paths = _collect_output_dir_files(output_dir)
            files_output = _persist_output_files(
                output_dir,
                file_paths,
                output_base_dir,
                resolved_output_path,
            )

            try:
                if "matplotlib.pyplot" in sys.modules:
                    import matplotlib.pyplot as plt  # noqa: WPS433

                    plt.close("all")
            except Exception:
                pass

            return _build_tool_response(
                error_message=None,
                stdout=stdout_buffer.getvalue(),
                stderr=stderr_buffer.getvalue(),
                result=namespace.get("result"),
                images=images_output,
                files=files_output,
            )
    except Exception:
        return _build_tool_response(
            error_message=traceback.format_exc(),
            stdout=stdout_buffer.getvalue(),
            stderr=stderr_buffer.getvalue(),
            result=None,
            images=[],
            files=[],
        )

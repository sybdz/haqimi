import base64
import contextlib
import io
import json
import os
import re
import sys
import tempfile
import textwrap
import traceback
from typing import Any, Dict, Iterable, List, Optional, Tuple

CODE_FENCE_PATTERN = re.compile(r"```(?:python|py)?\s*([\s\S]*?)\s*```", re.IGNORECASE)

MAX_AUTO_IMAGES = 4
MAX_AUTO_IMAGE_BYTES = 20 * 1024 * 1024  # 20MB

_EXT_TO_MIME = {
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".gif": "image/gif",
    ".webp": "image/webp",
    ".bmp": "image/bmp",
}

_AUTO_CAPTURE_VAR_NAMES = ("img", "image", "fig", "figure", "plot")


def _to_data_url(mime: str, data: bytes) -> Tuple[str, int]:
    return "data:" + mime + ";base64," + base64.b64encode(data).decode("utf-8"), len(data)


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


def _encode_image_file(path: str) -> Optional[Tuple[str, int]]:
    if not _is_supported_image_path(path):
        return None
    if not _looks_like_image_file(path):
        return None

    try:
        file_size = os.path.getsize(path)
    except Exception:
        return None

    if file_size <= 0 or file_size > MAX_AUTO_IMAGE_BYTES:
        return None

    try:
        with open(path, "rb") as fp:
            data = fp.read()
    except Exception:
        return None

    mime = _EXT_TO_MIME.get(os.path.splitext(path.lower())[1], "image/png")
    return _to_data_url(mime, data)


def _encode_image(image: Any) -> Optional[Tuple[str, int]]:
    """Convert different image-like objects (Pillow/matplotlib/plotly/numpy) to a data URL."""
    if image is None:
        return None

    if isinstance(image, (bytes, bytearray)):
        return _to_data_url("image/png", bytes(image))

    if isinstance(image, str):
        if image.startswith("data:image"):
            base64_part = image.split("base64,", 1)[1] if "base64," in image else ""
            return image, _approx_base64_decoded_size(base64_part)

        if os.path.exists(image) and _is_supported_image_path(image):
            return _encode_image_file(image)

        try:
            # Validate the string is base64 and wrap it as a data URL.
            compact = re.sub(r"\s+", "", image)
            data = base64.b64decode(compact, validate=True)
            if data and len(data) <= MAX_AUTO_IMAGE_BYTES:
                return _to_data_url("image/png", data)
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
            if isinstance(png_bytes, (bytes, bytearray)) and len(png_bytes) <= MAX_AUTO_IMAGE_BYTES:
                return _to_data_url("image/png", bytes(png_bytes))
        except Exception:
            pass

    # Pillow image
    try:
        if Image is not None and isinstance(image, Image.Image):
            buffer = io.BytesIO()
            image.save(buffer, format="PNG")
            data = buffer.getvalue()
            if data and len(data) <= MAX_AUTO_IMAGE_BYTES:
                return _to_data_url("image/png", data)
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
            if data and len(data) <= MAX_AUTO_IMAGE_BYTES:
                return _to_data_url("image/png", data)
        except Exception:
            pass

    # Matplotlib figure-like object
    if hasattr(image, "savefig"):
        buffer = io.BytesIO()
        image.savefig(buffer, format="png")
        data = buffer.getvalue()
        if data and len(data) <= MAX_AUTO_IMAGE_BYTES:
            return _to_data_url("image/png", data)

    return None


def _encode_images(images: Iterable[Any]) -> List[Tuple[str, int]]:
    encoded: List[Tuple[str, int]] = []
    for image in images:
        encoded_image = _encode_image(image)
        if encoded_image:
            encoded.append(encoded_image)
    return encoded


def _capture_matplotlib_figures() -> List[Tuple[str, int]]:
    """Capture all current matplotlib figures as base64 data URLs."""
    try:
        import matplotlib.pyplot as plt  # noqa: WPS433
    except Exception:
        return []

    captured: List[Tuple[str, int]] = []
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


def _collect_output_dir_images(output_dir: str) -> List[Tuple[str, int]]:
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
    encoded: List[Tuple[str, int]] = []
    for _, path in candidates:
        encoded_image = _encode_image_file(path)
        if encoded_image:
            encoded.append(encoded_image)
    return encoded


def _select_images(
    candidates: List[Tuple[str, int]],
    max_images: int = MAX_AUTO_IMAGES,
    max_total_bytes: int = MAX_AUTO_IMAGE_BYTES,
) -> Tuple[List[str], Dict[str, Any]]:
    selected: List[str] = []
    total_bytes = 0
    skipped_count = 0
    skipped_bytes = 0

    for data_url, size in candidates:
        if len(selected) >= max_images:
            skipped_count += 1
            skipped_bytes += size
            continue
        if size <= 0 or total_bytes + size > max_total_bytes:
            skipped_count += 1
            skipped_bytes += max(0, size)
            continue
        selected.append(data_url)
        total_bytes += size

    return selected, {
        "max_images": max_images,
        "max_total_bytes": max_total_bytes,
        "selected_count": len(selected),
        "selected_total_bytes": total_bytes,
        "skipped_count": skipped_count,
        "skipped_total_bytes": skipped_bytes,
    }


def _dedupe_candidates(candidates: List[Tuple[str, int]]) -> List[Tuple[str, int]]:
    seen = set()
    unique: List[Tuple[str, int]] = []
    for data_url, size in candidates:
        if data_url in seen:
            continue
        seen.add(data_url)
        unique.append((data_url, size))
    return unique


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


def run_python_tool(code: str) -> str:
    """
    Execute user-provided Python code.

    Expected variables set by the code:
    - result: any serializable object returned to the model.
    - image / images: Pillow image, matplotlib/plotly figure, numpy array, raw base64 string, or bytes.
    If no image/images are provided, current matplotlib figures (if any) will be captured automatically.
    Images saved to files (png/jpg/webp/gif/bmp) in OUTPUT_DIR / current working directory will be captured automatically.
    Pre-installed libraries: pillow, numpy, matplotlib, pandas, seaborn.
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
    try:
        with tempfile.TemporaryDirectory(prefix="rikkahub_pytool_") as output_dir:
            namespace["OUTPUT_DIR"] = output_dir
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
                with contextlib.redirect_stdout(stdout_buffer):
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

            candidates: List[Tuple[str, int]] = []

            single_image = namespace.get("image")
            if isinstance(single_image, str) and single_image and not os.path.isabs(single_image):
                relative_path = os.path.join(output_dir, single_image)
                if os.path.exists(relative_path):
                    single_image = relative_path
            encoded_single = _encode_image(single_image)
            if encoded_single:
                candidates.append(encoded_single)

            images_var = namespace.get("images")
            if isinstance(images_var, (list, tuple)):
                candidates.extend(_encode_images(images_var))

            if not candidates:
                for var_name in _AUTO_CAPTURE_VAR_NAMES:
                    if var_name not in namespace:
                        continue
                    encoded = _encode_image(namespace.get(var_name))
                    if encoded:
                        candidates.append(encoded)

            candidates.extend(_capture_matplotlib_figures())
            candidates.extend(_collect_output_dir_images(output_dir))

            candidates = _dedupe_candidates(candidates)
            images, image_stats = _select_images(candidates)

            try:
                if "matplotlib.pyplot" in sys.modules:
                    import matplotlib.pyplot as plt  # noqa: WPS433

                    plt.close("all")
            except Exception:
                pass

            payload = {
                "ok": True,
                "result": namespace.get("result"),
                "stdout": stdout_buffer.getvalue(),
                "images": images,
                "image_stats": image_stats,
            }
            return json.dumps(payload, default=_safe_default)
    except Exception:
        return json.dumps(
            {
                "ok": False,
                "error": traceback.format_exc(),
                "stdout": stdout_buffer.getvalue(),
            }
        )

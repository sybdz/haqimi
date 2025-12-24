import base64
import contextlib
import io
import json
import re
import textwrap
import traceback
from typing import Any, Dict, Iterable, List, Optional

CODE_FENCE_PATTERN = re.compile(r"```(?:python|py)?\s*([\s\S]*?)\s*```", re.IGNORECASE)


def _encode_image(image: Any) -> Optional[str]:
    """Convert different image-like objects (Pillow/matplotlib/plotly/numpy) to a data URL."""
    if image is None:
        return None

    if isinstance(image, (bytes, bytearray)):
        return "data:image/png;base64," + base64.b64encode(image).decode("utf-8")

    if isinstance(image, str):
        if image.startswith("data:image"):
            return image
        try:
            # Validate the string is base64 and wrap it as a data URL.
            base64.b64decode(image)
            return "data:image/png;base64," + image
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
            return "data:image/png;base64," + base64.b64encode(png_bytes).decode("utf-8")
        except Exception:
            pass

    # Pillow image
    try:
        if Image is not None and isinstance(image, Image.Image):
            buffer = io.BytesIO()
            image.save(buffer, format="PNG")
            return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("utf-8")
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
            return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("utf-8")
        except Exception:
            pass

    # Matplotlib figure-like object
    if hasattr(image, "savefig"):
        buffer = io.BytesIO()
        image.savefig(buffer, format="png")
        return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("utf-8")

    return None


def _encode_images(images: Iterable[Any]) -> List[str]:
    encoded: List[str] = []
    for image in images:
        encoded_image = _encode_image(image)
        if encoded_image:
            encoded.append(encoded_image)
    return encoded


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
    Pre-installed libraries: pillow, numpy, matplotlib, pandas, seaborn.
    """
    locals_dict: Dict[str, Any] = {}
    stdout_buffer = io.StringIO()
    try:
        normalized_code = _normalize_code(code)
        if not normalized_code:
            raise ValueError("Python code is empty after normalization")

        with contextlib.redirect_stdout(stdout_buffer):
            try:
                exec(normalized_code, {}, locals_dict)
            except SyntaxError as syntax_error:
                stripped = normalized_code.lstrip()
                if stripped.startswith("return "):
                    exec("result = " + stripped[len("return ") :], {}, locals_dict)
                else:
                    raise syntax_error

        images = []
        single_image = _encode_image(locals_dict.get("image"))
        if single_image:
            images.append(single_image)

        images_var = locals_dict.get("images")
        if isinstance(images_var, (list, tuple)):
            images.extend(_encode_images(images_var))

        payload = {
            "ok": True,
            "result": locals_dict.get("result"),
            "stdout": stdout_buffer.getvalue(),
            "images": images,
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

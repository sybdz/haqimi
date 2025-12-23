import base64
import contextlib
import io
import json
import traceback
from typing import Any, Dict, Iterable, List, Optional


def _encode_image(image: Any) -> Optional[str]:
    """Convert different image-like objects to a data URL."""
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

    # Pillow image
    try:
        from PIL import Image  # noqa: WPS433
    except Exception:
        Image = None

    if Image is not None and isinstance(image, Image.Image):
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("utf-8")

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


def run_python_tool(code: str) -> str:
    """
    Execute user-provided Python code.

    Expected variables set by the code:
    - result: any serializable object returned to the model.
    - image / images: Pillow image, matplotlib figure, raw base64 string, or bytes.
    """
    locals_dict: Dict[str, Any] = {}
    stdout_buffer = io.StringIO()
    try:
        with contextlib.redirect_stdout(stdout_buffer):
            exec(code, {}, locals_dict)

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

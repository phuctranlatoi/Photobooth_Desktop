"""Compare promoted beauty algorithms to the accepted Phase 5 test baseline.

Run: python scripts/verify_beauty_parity.py
Only the documented model-path substitutions are permitted.
"""
import difflib
import subprocess
import sys
from pathlib import Path

BASE = "5d654605801f45103b0d46e42cd6d249e041ff09"
ROOT = Path(__file__).resolve().parents[1]
NAMES = {
    "facemask": "YuNetFaceDetector OnnxFaceParsingService CustomerFaceSelector FaceAlignment FaceOvalMaskBuilder FastGuidedMaskFilter LetterboxTransform".split(),
    "beauty": "BeautyConfig BeautyColorMath BeautyProcessor BeautyResult GuidedLuminanceFilter PixelSafeCompositor".split(),
}


def normalize(name, text):
    if name == "YuNetFaceDetector":
        text = text.replace("class YuNetFaceDetector(private val selectedModelPath: Path = modelPath)", "class YuNetFaceDetector")
        text = text.replace('val modelPath: Path get() = ModelPathResolver.resolve("face_detection_yunet_2023mar.onnx")', 'val modelPath: Path = Path.of("app-resources/models/face_detection_yunet_2023mar.onnx")')
        text = text.replace('environment.createSession(selectedModelPath.toString(), it)', 'environment.createSession(modelPath.toString(), it)')
    if name == "OnnxFaceParsingService":
        text = text.replace("class OnnxFaceParsingService(private val selectedModelPath: Path = modelPath())", "class OnnxFaceParsingService")
        text = text.replace('ModelPathResolver.resolve("bisenet_face_parsing.onnx")', 'Path.of("app-resources/models/bisenet_face_parsing.onnx")')
        text = text.replace('environment.createSession(selectedModelPath.toString(), it)', 'environment.createSession(modelPath().toString(), it)')
    return text


def main():
    failures = 0
    for group, names in NAMES.items():
        for name in names:
            relative = f"com/phuctran/photobooth/desktop/{group}/{name}.kt"
            baseline = subprocess.check_output(["git", "show", f"{BASE}:src/test/kotlin/{relative}"], cwd=ROOT).decode("utf-8")
            promoted = (ROOT / "src/main/kotlin" / relative).read_text(encoding="utf-8")
            actual = normalize(name, promoted)
            match = actual == baseline
            print(f"{'PASS' if match else 'FAIL'} {relative}" + (" (model path only)" if match and actual != promoted else ""))
            if not match:
                failures += 1
                print("".join(difflib.unified_diff(baseline.splitlines(True), actual.splitlines(True), fromfile="baseline", tofile="promoted")))
    print(f"{sum(map(len, NAMES.values())) - failures}/{sum(map(len, NAMES.values()))} identical after documented path normalization")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

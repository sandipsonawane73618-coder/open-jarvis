#!/usr/bin/env bash
set -euo pipefail

# Pinned to the inspected Voicute repository commit; runtime inference is offline.
VOICUTE_COMMIT="bc61ca98fee766d7e8dffa1a8e5b899c2b8de246"
BASE_URL="https://raw.githubusercontent.com/voicute/onnx-wakeword/${VOICUTE_COMMIT}"
ASSET_DIR="app/src/main/assets"
MODEL_ZIP="/tmp/voicute-multi-jarvis-v9.3.zip"

mkdir -p "${ASSET_DIR}"
curl --fail --location --retry 3 "${BASE_URL}/models/melspectrogram.onnx" --output "${ASSET_DIR}/melspectrogram.onnx"
curl --fail --location --retry 3 "${BASE_URL}/models/en/multi_jarvis_v9.3.zip" --output "${MODEL_ZIP}"

# These sizes are pinned to the exact Git tree entries inspected above.
test "$(stat -c%s "${ASSET_DIR}/melspectrogram.onnx")" -eq 1087958
test "$(stat -c%s "${MODEL_ZIP}")" -eq 99251
sha256sum "${ASSET_DIR}/melspectrogram.onnx" "${MODEL_ZIP}"

python3 - <<'PY'
import json
import os
import zipfile

zip_path = "/tmp/voicute-multi-jarvis-v9.3.zip"
asset_dir = "app/src/main/assets"
with zipfile.ZipFile(zip_path) as archive:
    names = archive.namelist()
    info_name = next((n for n in names if n.rsplit("/", 1)[-1] == "model_info.json"), None)
    if not info_name:
        raise SystemExit("Voicute archive is missing model_info.json")
    info = json.loads(archive.read(info_name).decode("utf-8"))
    model_name = info.get("model_file")
    if not model_name:
        raise SystemExit("Voicute model_info.json is missing model_file")
    model_entry = next((n for n in names if n == model_name or n.endswith("/" + model_name)), None)
    if not model_entry:
        raise SystemExit(f"Voicute archive is missing model file: {model_name}")
    if info.get("model_type") != "multi_keyword":
        raise SystemExit("Expected a Voicute multi_keyword model")
    keywords = [str(k).strip().lower() for k in info.get("keywords", [])]
    expected = {"jarvis", "hey jarvis", "hi jarvis"}
    if set(keywords) != expected:
        raise SystemExit(f"Unexpected demo keyword set: {keywords}")
    if int(info.get("mel_time", 0)) != 98 or int(info.get("n_mels", 0)) != 32:
        raise SystemExit("Unexpected Voicute demo mel configuration")
    with open(os.path.join(asset_dir, "model.onnx"), "wb") as out:
        out.write(archive.read(model_entry))
    info["model_file"] = "model.onnx"
    with open(os.path.join(asset_dir, "model_info.json"), "w", encoding="utf-8") as out:
        json.dump(info, out, ensure_ascii=False, indent=2)
        out.write("\n")

for name in ("melspectrogram.onnx", "model.onnx", "model_info.json"):
    path = os.path.join(asset_dir, name)
    if not os.path.isfile(path) or os.path.getsize(path) == 0:
        raise SystemExit(f"Missing/empty Voicute runtime asset: {path}")
print("Voicute model prepared:", ", ".join(sorted(expected)))
PY

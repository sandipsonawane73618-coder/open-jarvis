#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar"
AAR_SHA256="a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
MODEL_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
MODEL_DIR="app/src/main/assets/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"

mkdir -p app/libs
curl --fail --location --retry 2 "$AAR_URL" --output app/libs/sherpa-onnx.aar
echo "$AAR_SHA256  app/libs/sherpa-onnx.aar" | sha256sum --check

if [[ "$MODE" == "all" ]]; then
  mkdir -p app/src/main/assets
  curl --fail --location --retry 2 "$MODEL_URL" --output /tmp/jarvis-kws-model.tar.bz2
  tar -xjf /tmp/jarvis-kws-model.tar.bz2 -C app/src/main/assets
  if [[ ! -d "$MODEL_DIR" ]]; then
    echo "Expected sherpa KWS model directory not found: $MODEL_DIR" >&2
    exit 1
  fi
  rm -rf "$MODEL_DIR/test_wavs"
fi

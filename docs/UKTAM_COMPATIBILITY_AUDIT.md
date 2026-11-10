# Uktam.ai compatibility audit

Uktam's application source is GPL-3.0 and is not copied. The audit found IndicConformer model-family MIT declarations but the exact quantized export notices, checksums and runtime memory/latency still require verification. Sherpa-ONNX is Apache-2.0 but would duplicate Jarvis' existing ONNX Runtime and add native binaries. No model binaries or Sherpa dependency are added.

This slice adds only a dependency-free compatibility gate for language, API, ABI, model size, peak-memory estimate, license metadata, storage and conservative available-memory budget. Hindi/Marathi model loading remains disabled until licensing and Realme P3 Pro 6 GB benchmarks are complete. Voicute wake detection and existing STT remain unchanged.
#!/usr/bin/env python3
"""Fail CI if a core Jarvis feature implementation class disappears."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {
    "agent/AgentCore.kt": ["AgentCore"],
    "agent/CommandRouter.kt": ["CommandRouter"],
    "agent/LLMResponseValidator.kt": ["LLMResponseValidator"],
    "agent/BankingSafetyPolicy.kt": ["BankingSafetyPolicy"],
    "agent/SelfHealingSafetyPolicy.kt": ["SelfHealingSafetyPolicy"],
    "agent/StepPipeline.kt": ["StepHandler"],
    "vision/ScreenshotCapture.kt": ["ScreenshotCapture"],
    "ui/ScreenCapturePermissionActivity.kt": ["ScreenCapturePermissionActivity"],
    "ui/OverlayAttachmentGate.kt": ["OverlayAttachmentGate"],
    "agent/HeadroomLiteContext.kt": ["HeadroomLiteContext"],
    "headroom/HeadroomCompressor.kt": ["HeadroomCompressor"],
    "graphify/MemoryContextBuilder.kt": ["MemoryContextBuilder"],
    "graphify/GraphifyRepository.kt": ["GraphifyRepository"],
    "voice/VoiceManager.kt": ["VoiceManager"],
    "voice/AndroidSTTEngine.kt": ["AndroidSTTEngine"],
}
BASE = ROOT / "app/src/main/java/com/openjarvis"
failures = []
checked = 0
for relative, names in REQUIRED.items():
    path = BASE / relative
    if not path.is_file():
        failures.append(f"missing required feature source: {path.relative_to(ROOT)}")
        continue
    source = path.read_text(encoding="utf-8")
    for name in names:
        checked += 1
        declaration = re.compile(
            r"\\b(?:class|object|interface)\\s+" + re.escape(name) + r"\\b"
        )
        if not declaration.search(source):
            failures.append(f"missing required feature declaration {name} in {path.relative_to(ROOT)}")
if failures:
    print("\\n".join(failures), file=sys.stderr)
    raise SystemExit(1)
print(f"Required feature-class guard PASS: {checked} declarations checked.")

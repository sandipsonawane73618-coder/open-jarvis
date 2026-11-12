#!/usr/bin/env python3
"""Validate FEATURES.md mappings against named Kotlin/JUnit test methods."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "FEATURES.md"
ROW = re.compile(r"^\|\s*[^|]+\|\s*[^|]+\|\s*(.*?)\s*\|$")
MAPPING = re.compile(r"`?([^`;\s]+\.kt)::([A-Za-z_][A-Za-z0-9_]*)")

def main() -> int:
    if not DOC.is_file():
        print("FEATURES.md is missing", file=sys.stderr)
        return 1
    failures = []
    checked = 0
    for line_no, line in enumerate(DOC.read_text(encoding="utf-8").splitlines(), 1):
        if not line.startswith("|") or line.startswith("| Feature") or line.startswith("|---"):
            continue
        match = ROW.match(line)
        if not match:
            continue
        refs = MAPPING.findall(match.group(1))
        if not refs:
            failures.append(f"FEATURES.md:{line_no}: mapped feature has no test reference")
            continue
        for relative, method in refs:
            checked += 1
            test_file = ROOT / relative.strip()
            if not test_file.is_file():
                failures.append(f"FEATURES.md:{line_no}: missing test file {relative}")
                continue
            source = test_file.read_text(encoding="utf-8")
            method_pattern = re.compile(r"\bfun\s+" + re.escape(method) + r"\s*\(")
            if not method_pattern.search(source):
                failures.append(f"FEATURES.md:{line_no}: missing test method {relative}::{method}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"FEATURES.md test map valid: {checked} named test-method references checked.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

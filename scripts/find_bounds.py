#!/usr/bin/env python3
"""Find the Euronics dashboard entry and print its tap coordinates."""
import argparse
import re
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xml_file", help="uiautomator XML dump")
    args = parser.parse_args()

    try:
        root = ET.parse(args.xml_file).getroot()
    except (OSError, ET.ParseError) as exc:
        print(f"Cannot read uiautomator XML: {exc}", file=sys.stderr)
        return 2

    target = next(
        (
            node
            for node in root.iter("node")
            if "EURONICS SERVICE REPORT"
            in (node.attrib.get("text") or "").upper()
            or "EURONICS SERVICE REPORT"
            in (node.attrib.get("content-desc") or "").upper()
        ),
        None,
    )
    if target is None:
        print("Euronics dashboard entry not found in UI hierarchy", file=sys.stderr)
        return 3

    bounds = target.attrib.get("bounds", "")
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if match is None:
        print(f"Invalid Euronics button bounds: {bounds!r}", file=sys.stderr)
        return 4

    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        print(f"Empty Euronics button bounds: {bounds!r}", file=sys.stderr)
        return 5

    print((left + right) // 2, (top + bottom) // 2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

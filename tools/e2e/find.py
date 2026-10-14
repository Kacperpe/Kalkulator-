"""Wypisuje środki przycisków z zrzutu UI (uiautomator) jako: tekst<TAB>x y."""
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    t = n.get("text") or n.get("content-desc") or ""
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    if t and m:
        x1, y1, x2, y2 = map(int, m.groups())
        print(f"{t}\t{(x1 + x2) // 2} {(y1 + y2) // 2}")

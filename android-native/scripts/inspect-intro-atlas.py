"""Read-only bitmap inspection; writes frame metadata only, never edits PNG pixels."""
import hashlib
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
assets = root / "android-native/app/src/main/assets/art/jrpg"
entries = {
    "naturalist": ("companion-intro-naturalist-smooth-v2.png", 6, 4),
    "robot": ("companion-intro-robot-smooth-v2.png", 4, 4),
    "explorer": ("companion-intro-explorer-smooth-v2.png", 4, 4),
    "creature": ("companion-intro-creature-smooth-v2.png", 4, 4),
    "dogs": ("companion-intro-dogs-v1.png", 8, 4),
    "amanita": ("companion-intro-amanita-v1.png", 4, 2),
    "spider": ("companion-intro-spider-v1.png", 4, 3),
    "drone": ("companion-intro-drone-v1.png", 4, 3),
    "spirit": ("companion-intro-spirit-v1.png", 4, 3),
    "portrait": ("companion-intro-portrait-v2.png", 4, 3),
}
metadata, report = {}, {}
for key, (name, columns, rows) in entries.items():
    bitmap = Image.open(assets / name).convert("RGBA")
    width, height = bitmap.size
    frames, hashes = [], set()
    for row in range(rows):
        for col in range(columns):
            x, y = col * width // columns, row * height // rows
            right, bottom = (col + 1) * width // columns, (row + 1) * height // rows
            frames.append([x, y, right - x, bottom - y])
            hashes.add(hashlib.sha256(bitmap.crop((x, y, right, bottom)).tobytes()).hexdigest())
    metadata[key] = {"file": name, "reference_width": width / columns,
                     "reference_height": height / rows, "frames": frames}
    report[key] = {"file": name, "size": [width, height], "frame_count": len(frames),
                   "unique_cell_hashes": len(hashes), "alpha_extrema": bitmap.getchannel("A").getextrema(),
                   "corner_alpha": bitmap.getpixel((0, 0))[3]}
(assets / "companion-intro-atlas.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
(root / "docs/COMPANION_INTRO_ASSETS.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report))

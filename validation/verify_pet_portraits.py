"""Read-only validation of production pet sprite atlases; never edits images."""
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
results = []
for kind in ("naturalist", "explorer", "robot", "creature"):
    path = root / f"android-native/app/src/main/assets/art/jrpg/pet-{kind}-face-3d-v1.png"
    with Image.open(path) as image:
        width, height = image.size
        assert image.mode == "RGBA" and width % 3 == height % 3 == 0
        assert min(width // 3, height // 3) >= 384
        for row in range(3):
            for col in range(3):
                alpha = image.getchannel("A").crop((col * width // 3, row * height // 3,
                                                   (col + 1) * width // 3, (row + 1) * height // 3))
                histogram = alpha.histogram()
                assert histogram[0] > 500 and sum(histogram[201:]) > 500, (kind, row, col)
        results.append({"kind": kind, "size": image.size, "frames": 9, "bytes": path.stat().st_size})
print(json.dumps(results))

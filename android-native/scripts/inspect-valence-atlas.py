"""Measure transparent sprite bounds without changing generated image pixels.

Default: validate the packaged metadata. --write: update metadata after new art.
Requires Pillow, numpy and scipy. Retain pets; reject disconnected neighbor edges.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

root = Path(__file__).resolve().parents[1] / "app/src/main/assets/art/jrpg"
result = {}
for avatar in ("robot", "explorer", "creature", "naturalist"):
    alpha = np.array(Image.open(root / f"valence-{avatar}-emotions-v1.png").getchannel("A"))
    coverage = (alpha > 80).sum(axis=1)
    runs, start = [], None
    for y, count in enumerate(coverage):
        if count > 10 and start is None:
            start = y
        if count <= 10 and start is not None:
            runs.append((start, y))
            start = None
    if start is not None:
        runs.append((start, len(coverage)))
    assert len(runs) == 4, (avatar, runs)
    rows, references, exclusions = [], [], []
    for top, bottom in runs:
        top, bottom = max(0, top - 1), min(alpha.shape[0], bottom + 1)
        rectangles, frame_exclusions = [], []
        for col in range(6):
            labels, _ = ndimage.label(alpha[top:bottom, col*256:(col+1)*256] > 100)
            areas = np.bincount(labels.ravel())
            largest = int(areas[1:].max())
            objects = ndimage.find_objects(labels)
            retained = [sl for i, sl in enumerate(objects, 1) if areas[i] >= largest * .025]
            left = min(sl[1].start for sl in retained)
            right = max(sl[1].stop for sl in retained)
            x, y, w, h = col*256+left, top, right-left, bottom-top
            rectangles.append([x, y, w, h])
            fragments = []
            for i, sl in enumerate(objects, 1):
                if 12 < areas[i] < largest*.025 and (sl[1].start == 0 or sl[1].stop == 256):
                    l, t = col*256+sl[1].start-1, y+sl[0].start-1
                    r, b = col*256+sl[1].stop+1, y+sl[0].stop+1
                    if r > x and l < x+w:
                        fragments.append([l-x, t-y, r-x, b-y])
            frame_exclusions.append(fragments)
        rows.append(rectangles)
        references.append([max(r[2] for r in rectangles), max(r[3] for r in rectangles)])
        exclusions.append(frame_exclusions)
    # Bicycles can extend across nominal column boundaries. Measure whole
    # connected silhouettes before cropping, then retain only their own shape.
    y = rows[1][0][1]
    h = rows[1][0][3]
    labels, _ = ndimage.label(alpha[y:y+h] > 100)
    areas = np.bincount(labels.ravel())
    objects = ndimage.find_objects(labels)
    groups = [(i, sl) for i, sl in enumerate(objects, 1) if areas[i] > 1000]
    groups.sort(key=lambda pair: pair[1][1].start)
    assert len(groups) == 6, (avatar, "bike groups", len(groups))
    regions, bike_rectangles = [], []
    for label, sl in groups:
        mask = ndimage.binary_dilation(labels == label, iterations=2)
        ys, xs = np.where(mask)
        left, right = int(xs.min()), int(xs.max())+1
        top, bottom = int(ys.min()), int(ys.max())+1
        local = mask[top:bottom, left:right]
        spans = []
        active = {}
        for line, pixels in enumerate(local):
            changes = np.diff(np.concatenate(([False], pixels, [False])).astype(int))
            current = set(zip(np.where(changes == 1)[0], np.where(changes == -1)[0]))
            for interval in set(active)-current:
                begin = active.pop(interval)
                spans.append([int(interval[0]), begin, int(interval[1]), line])
            for interval in current-set(active):
                active[interval] = line
        for interval, begin in active.items():
            spans.append([int(interval[0]), begin, int(interval[1]), len(local)])
        regions.append(sorted(spans))
        bike_rectangles.append([left, y+top, right-left, bottom-top])
    # The last naturalist pose is clipped by the original sheet's right edge.
    # Reuse its complete same-emotion partner rather than displaying a cut wheel.
    for col, rectangle in enumerate(bike_rectangles):
        x, top, width, height = rectangle
        if x+width == alpha.shape[1] and np.any(alpha[top:top+height, -1] > 100):
            partner = col-1 if col%2 else col+1
            assert bike_rectangles[partner][0]+bike_rectangles[partner][2] < alpha.shape[1]
            bike_rectangles[col] = list(bike_rectangles[partner])
            regions[col] = [list(r) for r in regions[partner]]
    rows[1] = bike_rectangles
    references[1] = [max(r[2] for r in bike_rectangles), max(r[3] for r in bike_rectangles)]
    exclusions[1] = [[] for _ in range(6)]
    result[avatar] = {"reference": references, "rows": rows, "edge_exclusions": exclusions, "bike_visible_regions": regions}

metadata = root / "valence-emotions-atlas.json"
if "--write" in sys.argv:
    metadata.write_text(json.dumps(result, indent=2), encoding="utf-8")
else:
    assert result == json.loads(metadata.read_text(encoding="utf-8")), "Unsafe or stale atlas metadata"
for mode in ("walk", "bike", "pt", "car"):
    assert Image.open(root / f"valence-{mode}-background-v1.png").size == (1536, 1024)
print("96 poses checked; 24 complete bicycle silhouettes isolated; four backgrounds aligned")

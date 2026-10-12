"""Read PNG alpha bounds and register anchored frames; never modify image pixels."""
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
assets = root / 'android-native/app/src/main/assets/art/jrpg'
path = assets / 'companion-intro-atlas.json'
metadata = json.loads(path.read_text(encoding='utf-8'))
report = {}
def silhouette_bounds(alpha):
    """Ignore detached generation specks when measuring the character anchor."""
    w, h = alpha.size
    points = {(i % w, i // w) for i, a in enumerate(alpha.getdata()) if a >= 128}
    largest = set()
    while points:
        seed = points.pop()
        component, pending = {seed}, [seed]
        while pending:
            x, y = pending.pop()
            for neighbour in [(x-1,y), (x+1,y), (x,y-1), (x,y+1)]:
                if neighbour in points:
                    points.remove(neighbour); component.add(neighbour); pending.append(neighbour)
        if len(component) > len(largest):
            largest = component
    assert largest
    bounds = min(x for x,y in largest), min(y for x,y in largest), max(x for x,y in largest)+1, max(y for x,y in largest)+1
    lower = [(x,y) for x,y in largest if y >= bounds[1]+(bounds[3]-bounds[1])*.76]
    # Register feet/collar, unaffected by hands or changing facial expressions.
    anchor = ((min(x for x,y in lower)+max(x for x,y in lower)+1)/2, bounds[3])
    return bounds, anchor
for key in ['naturalist', 'robot', 'explorer', 'creature', 'portrait']:
    name = f'companion-startup-{key}-v3.png'
    im = Image.open(assets / name)
    assert im.mode == 'RGBA' and im.getchannel('A').getextrema()[0] == 0
    rows = 3 if key == 'portrait' else 4
    frames = []
    anchors = []
    for row in range(rows):
        for col in range(8):
            x, y = col * im.width // 8, row * im.height // rows
            right, bottom = (col + 1) * im.width // 8, (row + 1) * im.height // rows
            alpha = im.getchannel('A').crop((x, y, right, bottom))
            bounds, anchor = silhouette_bounds(alpha)
            assert bounds, (key, row, col)
            l, t, r, b = bounds
            frames.append([x+l, y+t, r-l, b-t])
            anchors.append([anchor[0]-l, anchor[1]-t])
    # Constant scale, horizontal centre and foot/collar baseline for every cell.
    width = max(2*max(a[0],f[2]-a[0]) for f,a in zip(frames,anchors))
    height = max(a[1] for a in anchors)
    offsets = [[width/2-a[0], height-a[1]] for a in anchors]
    metadata['startup_'+key] = dict(file=name, reference_width=width,
        reference_height=height, frames=frames, offsets=offsets)
    report[key] = dict(file=name, size=list(im.size), frames=len(frames),
        anchor='centre-bottom', reference_size=[width, height], transparent=True)
path.write_text(json.dumps(metadata, indent=2)+'\n', encoding='utf-8', newline='\r\n')
(root/'docs/COMPANION_STARTUP_04102_ASSETS.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8', newline='\n')
print(json.dumps(report))

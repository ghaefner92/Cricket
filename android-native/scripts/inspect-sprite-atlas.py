"""Read alpha geometry and emit crop metadata. Never modifies any image pixels.

Generated rows have uneven spacing. Measure each column independently, then
register all six poses to the same scale and ground baseline in the renderer.
Pillow is a development-only metadata dependency, not required by Android/build.
"""
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets/art/jrpg'
guides = {
    'robot': (270, 549, 746),
    'creature': (268, 532, 720),
    'explorer': (278, 565, 741),
    'naturalist': (272, 538, 734),
}
output = {}
for name, expected in guides.items():
    with Image.open(root / f'{name}-transport-v2.png') as image:
        assert image.mode == 'RGBA'
        width, height = image.size
        alpha = image.getchannel('A')
        frames = [[] for _ in range(4)]
        row_guides = (0, *expected, height)
        for row in range(4):
          band = alpha.crop((0, row_guides[row]+4, width, row_guides[row+1]-4)).point(lambda a: 255 if a > 128 else 0)
          x_counts = [sum(v != 0 for v in band.crop((x,0,x+1,band.height)).getdata()) for x in range(width)]
          x_cuts = [0] + [min(range(i*width//6-18,i*width//6+19), key=lambda x: sum(x_counts[max(0,x-1):x+2])+abs(x-i*width//6)*.15) for i in range(1,6)] + [width]
          for column in range(6):
            left, right = x_cuts[column:column+2]
            # Alpha >128 rejects nearly transparent antialiasing dust.
            mask = alpha.crop((left, 0, right, height)).point(lambda a: 255 if a > 128 else 0)
            counts = [sum(v != 0 for v in mask.crop((0, y, right-left, y+1)).getdata()) for y in range(height)]
            cuts = [0]
            for guide in expected:
                cut = min(range(guide-16, guide+17), key=lambda y: sum(counts[max(0,y-1):y+2]) + abs(y-guide)*0.15)
                cuts.append(cut)
            cuts.append(height)
            bounds = mask.crop((0, cuts[row], right-left, cuts[row+1])).getbbox()
            assert bounds is not None
            x0, y0, x1, y1 = bounds
            # Retain one pixel of the outline around the measured shape.
            x0=max(0,x0-1);x1=min(right-left,x1+1)
            y0=max(0,y0-1);y1=min(cuts[row+1]-cuts[row],y1+1)
            frames[row].append([left+x0,cuts[row]+y0,x1-x0,y1-y0])
        output[name] = {'size': [width,height], 'rows': frames,
                        'reference': [[max(f[2] for f in row),max(f[3] for f in row)] for row in frames]}
        print(name, output[name]['reference'])
(root/'transport-atlas.json').write_text(json.dumps(output,indent=2),encoding='utf-8')

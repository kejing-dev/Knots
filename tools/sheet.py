"""Contact sheet of factory shots: python -I tools/sheet.py OUT.png DIR name1 name2 ... (crop = rope + panel area, 3 columns, labelled)"""
import os
import sys

from PIL import Image, ImageDraw

out, src = sys.argv[1], sys.argv[2]
names = sys.argv[3:]
box = (950, 350, 2450, 1550)
w, h = (box[2] - box[0]) // 3, (box[3] - box[1]) // 3
cols = 3
rows = (len(names) + cols - 1) // cols
sheet = Image.new("RGB", (w * cols, (h + 24) * rows), (30, 30, 30))
d = ImageDraw.Draw(sheet)
for i, n in enumerate(names):
    p = os.path.join(src, n + ".png")
    if not os.path.exists(p):
        continue
    im = Image.open(p).convert("RGB").crop(box).resize((w, h), Image.LANCZOS)
    x, y = (i % cols) * w, (i // cols) * (h + 24)
    sheet.paste(im, (x, y + 24))
    d.text((x + 6, y + 4), n, fill=(255, 255, 0))
sheet.save(out)

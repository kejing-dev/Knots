"""Contact sheet of .scratch/shots/*.png (crop to the middle of each frame): python tools/contact_sheet.py OUT.png [names...]"""
import glob, os, sys
from PIL import Image, ImageDraw
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".scratch", "shots")
names = sys.argv[2:] or sorted(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(root, "*.png")))
cols = 4; tw = 640; th = 640
rows = (len(names) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw, rows * (th + 28)), (30, 30, 30))
d = ImageDraw.Draw(sheet)
for i, n in enumerate(names):
    p = os.path.join(root, n + ".png")
    if not os.path.exists(p): continue
    im = Image.open(p).convert("RGB")
    w, h = im.size
    # crop the central 60 % (where the panel and the sheet are), keep the aspect
    cw, ch = int(w * 0.6), int(h * 0.6)
    im = im.crop(((w - cw) // 2, (h - ch) // 2 + int(h * 0.08), (w + cw) // 2, (h + ch) // 2 + int(h * 0.08))).resize((tw, th), Image.LANCZOS)
    x, y = (i % cols) * tw, (i // cols) * (th + 28)
    sheet.paste(im, (x, y + 28))
    d.text((x + 8, y + 6), n, fill=(255, 255, 255))
sheet.save(sys.argv[1]); print("sheet", sys.argv[1], len(names))

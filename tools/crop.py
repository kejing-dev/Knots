"""Crop a 2560 console shot to the work area (rope + panel) and halve it: python -I tools/crop.py in.png out.png [x0 y0 x1 y1]"""
import sys
from PIL import Image
a = sys.argv
box = tuple(int(v) for v in a[3:7]) if len(a) >= 7 else (900, 300, 2500, 1700)
im = Image.open(a[1]).crop(box)
im.resize((im.width // 2, im.height // 2), Image.LANCZOS).save(a[2])

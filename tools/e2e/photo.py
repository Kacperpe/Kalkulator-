"""Zdjęcie testowe: wzór na szarawej kartce, nierówne światło, rozmycie, szum (jak z aparatu)."""
import sys
import numpy as np
from PIL import Image, ImageFilter
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

tex, out = sys.argv[1], sys.argv[2]
fig = plt.figure(figsize=(6, 2), dpi=400)
fig.text(0.5, 0.5, "$" + tex + "$", ha="center", va="center", fontsize=30)
fig.savefig(out, facecolor="white")
plt.close(fig)
a = np.asarray(Image.open(out).convert("L")).astype(np.float32) / 255
h, w = a.shape
yy, xx = np.mgrid[0:h, 0:w]
img = (0.8 - (1 - a) * 0.62) * (1 - 0.22 * xx / w - 0.12 * yy / h)
rng = np.random.default_rng(1)
im = Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8)).rotate(1.5, resample=Image.BICUBIC, fillcolor=180)
im = im.filter(ImageFilter.GaussianBlur(2))
arr = np.asarray(im).astype(np.float32) + rng.normal(0, 6, im.size[::-1])
im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert("RGB")
im.save(out)

"""Eksperyment: jakość rozpoznawania wzorów na symulowanych zdjęciach telefonem
dla różnych sposobów przygotowania obrazu i różnych wariantów kwantyzacji modelu."""
import io
import json
import os
import platform
import sys
import time

import numpy as np
from PIL import Image, ImageFilter

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

OUT = "imgs"
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

FORMULAS = [
    r"2x+5=11",
    r"\frac{x^2+3x-1}{x+1}",
    r"\sqrt{5-2x}=3",
    r"3\cdot(4+7)-\frac{1}{2}",
    r"2x^3+x^2-13x+6=0",
    r"\frac{3}{4}+\frac{5}{6}",
    r"x^2-4x+4=0",
    r"\sin(30)+\cos(60)",
    r"\frac{12}{5}-\frac{7}{3}",
    r"(x-1)^2=9",
    r"\log(100)+2^3",
    r"\frac{2x+1}{3}=5",
]
FONTS = ["cm", "stix", "dejavusans", "stixsans"]


def render(tex, font, dpi):
    plt.rcParams["mathtext.fontset"] = font
    fig = plt.figure(figsize=(6, 1.6), dpi=dpi)
    fig.text(0.5, 0.5, "$" + tex + "$", ha="center", va="center", fontsize=26)
    buf = io.BytesIO()
    fig.savefig(buf, format="png", bbox_inches="tight", pad_inches=0.15)
    plt.close(fig)
    buf.seek(0)
    return Image.open(buf).convert("L")


def photo(clean, k):
    """Udaje zdjęcie kartki: szare tło, nierówne światło, lekki obrót, rozmycie, szum, JPEG, duża rozdzielczość."""
    a = np.asarray(clean).astype(np.float32) / 255.0
    h, w = a.shape
    pad = int(0.25 * h)
    full = np.ones((h + 2 * pad, w + 2 * pad), np.float32)
    full[pad:pad + h, pad:pad + w] = a
    H, W = full.shape
    ink = 0.12 + 0.1 * rng.random()
    paper = 0.72 + 0.15 * rng.random()
    img = paper - (1 - full) * (paper - ink)
    yy, xx = np.mgrid[0:H, 0:W]
    img = img * (1 - 0.3 * rng.random() * (xx / W) - 0.2 * rng.random() * (yy / H))
    canvas = Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8))
    fill = int(np.median(img) * 255)
    canvas = canvas.rotate(rng.uniform(-3, 3), resample=Image.BICUBIC, fillcolor=fill)
    canvas = canvas.filter(ImageFilter.GaussianBlur(1.5 + 2.5 * rng.random()))
    arr = np.asarray(canvas).astype(np.float32) + rng.normal(0, 7, canvas.size[::-1])
    canvas = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    rgb = Image.merge("RGB", [canvas.point(lambda v, f=f: min(255, v * f)) for f in (1.0, 0.96, 0.88)])
    buf = io.BytesIO()
    rgb.save(buf, "JPEG", quality=80)
    buf.seek(0)
    rgb = Image.open(buf).convert("RGB")
    m = pad * rng.uniform(0.15, 0.9)
    box = (int(pad - m), int(pad - m * 0.7), int(W - pad + m), int(H - pad + m * 0.7))
    return rgb.crop(box)


def android_bilinear(img, size=384):
    """Skalowanie jak Canvas.drawBitmap z FILTER_BITMAP: bilinear z 4 pikseli, bez wygładzania przy pomniejszaniu."""
    a = np.asarray(img).astype(np.float32)
    h, w = a.shape[:2]
    ys = (np.arange(size) + 0.5) * h / size - 0.5
    xs = (np.arange(size) + 0.5) * w / size - 0.5
    y0 = np.clip(np.floor(ys).astype(int), 0, h - 1); y1 = np.clip(y0 + 1, 0, h - 1)
    x0 = np.clip(np.floor(xs).astype(int), 0, w - 1); x1 = np.clip(x0 + 1, 0, w - 1)
    fy = np.clip(ys - np.floor(ys), 0, 1)[:, None, None]
    fx = np.clip(xs - np.floor(xs), 0, 1)[None, :, None]
    r = (a[y0][:, x0] * (1 - fx) * (1 - fy) + a[y0][:, x1] * fx * (1 - fy) +
         a[y1][:, x0] * (1 - fx) * fy + a[y1][:, x1] * fx * fy)
    return Image.fromarray(np.clip(r, 0, 255).astype(np.uint8))


def normalize(img):
    """Szarość + rozciągnięcie kontrastu: tło do bieli, tusz do czerni."""
    g = np.asarray(img.convert("L")).astype(np.float32)
    # wyrównanie oświetlenia: dzielenie przez mocno rozmyte tło
    bg = np.asarray(Image.fromarray(g.astype(np.uint8)).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(15))).astype(np.float32)
    g = np.clip(g / np.maximum(bg, 1) * 255, 0, 255)
    lo, hi = np.percentile(g, 1), np.percentile(g, 60)
    g = np.clip((g - lo) / max(hi - lo, 1) * 255, 0, 255)
    return Image.fromarray(g.astype(np.uint8)).convert("RGB")


def pad_square(img):
    w, h = img.size
    s = max(w, h)
    c = Image.new("RGB", (s, s), (255, 255, 255))
    c.paste(img, ((s - w) // 2, (s - h) // 2))
    return c


def pad_aspect(img, maxr=3.0):
    """Dopełnia białym tak, by proporcje nie przekraczały maxr:1."""
    w, h = img.size
    if w > h * maxr:
        nh = int(w / maxr)
        c = Image.new("RGB", (w, nh), (255, 255, 255)); c.paste(img, (0, (nh - h) // 2)); return c
    if h > w * maxr:
        nw = int(h / maxr)
        c = Image.new("RGB", (nw, h), (255, 255, 255)); c.paste(img, ((nw - w) // 2, 0)); return c
    return img


def margin(img, f=0.06):
    w, h = img.size
    m = int(max(w, h) * f)
    c = Image.new("RGB", (w + 2 * m, h + 2 * m), (255, 255, 255))
    c.paste(img, (m, m))
    return c


def halving(img, s=384):
    while img.width >= 2 * s or img.height >= 2 * s:
        img = img.resize((img.width // 2 if img.width >= 2 * s else img.width, img.height // 2 if img.height >= 2 * s else img.height), Image.BILINEAR)
    return img


def stretch(img):
    """Jak Pre.stretchContrast w aplikacji."""
    g = (np.asarray(img).astype(np.int32) * np.array([299, 587, 114])).sum(axis=2) // 1000
    lo, hi = np.percentile(g, 2, method="lower"), np.percentile(g, 50, method="lower")
    if hi - lo < 24:
        v = g
    else:
        v = np.clip((g - lo) / (hi - lo) * 255, 0, 255)
    v = v.astype(np.uint8)
    return Image.fromarray(np.stack([v, v, v], axis=2))


PREP = {
    "P6_app_new": lambda im: stretch(android_bilinear(halving(im))),
    "P0_android_stretch": lambda im: android_bilinear(im),
    "P1_aa_stretch": lambda im: im.resize((384, 384), Image.BOX),
    "P2_norm_aa_stretch": lambda im: normalize(im).resize((384, 384), Image.BOX),
    "P3_norm_square": lambda im: pad_square(normalize(im)).resize((384, 384), Image.BOX),
    "P4_norm_aspect3": lambda im: pad_aspect(margin(normalize(im)), 3.0).resize((384, 384), Image.BOX),
    "P5_norm_aspect2": lambda im: pad_aspect(margin(normalize(im)), 2.0).resize((384, 384), Image.BOX),
}


def prepare():
    from huggingface_hub import hf_hub_download
    from onnxruntime.quantization import QuantType, quantize_dynamic

    files = {n: hf_hub_download("breezedeus/pix2text-mfr", n) for n in ["encoder_model.onnx", "decoder_model.onnx", "tokenizer.json"]}
    os.makedirs("models", exist_ok=True)
    import shutil
    shutil.copy(files["encoder_model.onnx"], "models/fp32_enc.onnx")
    shutil.copy(files["decoder_model.onnx"], "models/fp32_dec.onnx")
    shutil.copy(files["tokenizer.json"], "models/tokenizer.json")
    for name, kw in [("q_matmul", {"op_types_to_quantize": ["MatMul", "Gemm"]}), ("q_full", {})]:
        e, d = f"models/{name}_enc.onnx", f"models/{name}_dec.onnx"
        quantize_dynamic(files["encoder_model.onnx"], e, weight_type=QuantType.QUInt8, **kw)
        quantize_dynamic(files["decoder_model.onnx"], d, weight_type=QuantType.QUInt8, **kw)
        print(name, os.path.getsize(e) // 1024, os.path.getsize(d) // 1024, "KB")
    for i, f in enumerate(FORMULAS):
        clean = render(f, FONTS[i % len(FONTS)], 650)
        photo(clean, i).save(os.path.join(OUT, f"photo{i}.png"))


def evaluate(only):
    import onnxruntime as ort

    print("platform", platform.machine(), "ort", ort.__version__)
    vocab = json.load(open("models/tokenizer.json", encoding="utf-8"))["model"]["vocab"]
    tokens = [None] * len(vocab)
    for t, i in vocab.items():
        tokens[i] = t
    samples = [(f, Image.open(os.path.join(OUT, f"photo{i}.png")).convert("RGB")) for i, f in enumerate(FORMULAS)]

    def run(sess, x):
        enc, dec = sess
        h = enc.run(None, {"pixel_values": x})[0]
        ids = [2]
        for _ in range(150):
            logits = dec.run(None, {"input_ids": np.array([ids], dtype=np.int64), "encoder_hidden_states": h})[0]
            n = int(logits[0, -1].argmax())
            if n == 2:
                break
            ids.append(n)
        return "".join(tokens[t] for t in ids[1:] if t > 4).replace("Ġ", " ")

    def norm(s):
        return s.replace(" ", "").replace("\\left", "").replace("\\right", "").replace("{", "").replace("}", "")

    for vname in only:
        try:
            sess = (ort.InferenceSession(f"models/{vname}_enc.onnx", providers=["CPUExecutionProvider"]),
                    ort.InferenceSession(f"models/{vname}_dec.onnx", providers=["CPUExecutionProvider"]))
        except Exception as ex:
            print(f"== {vname}: NIE WCZYTUJE SIĘ: {ex}")
            continue
        for pname, prep in PREP.items():
            ok = 0
            t0 = time.time()
            lines = []
            for i, (truth, ph) in enumerate(samples):
                im = prep(ph)
                x = ((np.asarray(im).astype(np.float32) / 255.0 - 0.5) / 0.5).transpose(2, 0, 1)[None]
                try:
                    got = run(sess, x)
                except Exception as ex:
                    got = "WYJĄTEK " + str(ex)[:200]
                good = norm(got) == norm(truth)
                ok += good
                lines.append(f"   {'OK ' if good else 'BAD'} {truth}  ->  {got}")
            dt = (time.time() - t0) / len(samples)
            print(f"== {vname} {pname}: {ok}/{len(samples)}  ({dt:.2f}s/img)")
            print("\n".join(lines))
            sys.stdout.flush()


if __name__ == "__main__":
    if sys.argv[1] == "prepare":
        prepare()
    else:
        evaluate(sys.argv[2:] or ["q_full"])

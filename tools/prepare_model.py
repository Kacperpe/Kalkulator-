"""Pobiera model Pix2Text-MFR (rozpoznawanie wzorów), zmniejsza go kwantyzacją i zapisuje do zasobów aplikacji.

Uruchamiane w GitHub Actions przed budowaniem. Tworzy też obrazki testowe dla testów jednostkowych.
"""
import json
import os
import shutil

import numpy as np
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

REPO = "breezedeus/pix2text-mfr"
ASSETS = "app/src/main/assets"
TESTS = "app/src/test/resources/mfr"
os.makedirs(ASSETS, exist_ok=True)
os.makedirs(TESTS, exist_ok=True)

files = {n: hf_hub_download(REPO, n) for n in ["encoder_model.onnx", "decoder_model.onnx", "tokenizer.json"]}

for src, dst in [("encoder_model.onnx", "mfr_encoder.onnx"), ("decoder_model.onnx", "mfr_decoder.onnx")]:
    out = os.path.join(ASSETS, dst)
    quantize_dynamic(files[src], out, weight_type=QuantType.QUInt8)
    print(dst, os.path.getsize(out) // 1024, "KB")

vocab = json.load(open(files["tokenizer.json"], encoding="utf-8"))["model"]["vocab"]
tokens = [None] * len(vocab)
for tok, idx in vocab.items():
    tokens[idx] = tok
assert all(t is not None and t != "" and "\n" not in t and "\r" not in t for t in tokens), "nieoczekiwany token"
with open(os.path.join(ASSETS, "mfr_vocab.txt"), "w", encoding="utf-8", newline="") as f:
    f.write("\n".join(tokens))
print("vocab", len(tokens))

FORMULAS = [r"2x+5=11", r"\frac{x^2+3x-1}{x+1}", r"\sqrt{5-2x}=3", r"3\cdot(4+7)-\frac{1}{2}", r"2x^3+x^2-13x+6=0"]
for i, s in enumerate(FORMULAS):
    fig = plt.figure(figsize=(4, 1.2), dpi=150)
    fig.text(0.5, 0.5, "$" + s + "$", ha="center", va="center", fontsize=22)
    fig.savefig(os.path.join(TESTS, f"t{i}.png"))
    plt.close(fig)

# kontrola: zmniejszony model musi czytać obrazki testowe
enc = ort.InferenceSession(os.path.join(ASSETS, "mfr_encoder.onnx"), providers=["CPUExecutionProvider"])
dec = ort.InferenceSession(os.path.join(ASSETS, "mfr_decoder.onnx"), providers=["CPUExecutionProvider"])
for i in range(len(FORMULAS)):
    img = Image.open(os.path.join(TESTS, f"t{i}.png")).convert("RGB").resize((384, 384), Image.BILINEAR)
    # surowe piksele RGB dla testu jednostkowego (bez zależności od bibliotek graficznych)
    with open(os.path.join(TESTS, f"t{i}.rgb"), "wb") as raw:
        raw.write(np.asarray(img).astype(np.uint8).tobytes())
    x = ((np.asarray(img).astype(np.float32) / 255.0 - 0.5) / 0.5).transpose(2, 0, 1)[None]
    h = enc.run(None, {"pixel_values": x})[0]
    ids = [2]
    for _ in range(200):
        logits = dec.run(None, {"input_ids": np.array([ids], dtype=np.int64), "encoder_hidden_states": h})[0]
        n = int(logits[0, -1].argmax())
        if n == 2:
            break
        ids.append(n)
    print(i, "".join(tokens[t] for t in ids[1:] if t > 4).replace("Ġ", " "))

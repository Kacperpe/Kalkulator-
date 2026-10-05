import json, os, time, glob, sys
import numpy as np, onnx, onnxruntime as ort
from PIL import Image
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import quantize_dynamic, QuantType
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt

R = "breezedeus/pix2text-mfr"
f = {n: hf_hub_download(R, n) for n in ["encoder_model.onnx", "decoder_model.onnx", "tokenizer.json", "config.json", "tokenizer_config.json", "special_tokens_map.json"]}
tok = json.load(open(f["tokenizer.json"]))
print("TOKENIZER keys", list(tok.keys()))
for k in ["normalizer", "pre_tokenizer", "post_processor", "decoder"]:
    print(k, "=", json.dumps(tok.get(k))[:600])
print("model.type", tok["model"].get("type"), {k: v for k, v in tok["model"].items() if k not in ("vocab", "merges")})
vocab = tok["model"]["vocab"]
print("vocab size", len(vocab), "merges", len(tok["model"].get("merges", [])))
inv = {v: k for k, v in vocab.items()}
print("added_tokens", json.dumps(tok.get("added_tokens"))[:800])
print("first 120 tokens:", [inv.get(i) for i in range(120)])
print("sample tokens:", [inv.get(i) for i in range(300, 420)])
cfg = json.load(open(f["config.json"]))
print("decoder cfg:", {k: cfg["decoder"].get(k) for k in ["vocab_size", "d_model", "decoder_layers", "max_length", "decoder_start_token_id", "eos_token_id", "pad_token_id", "bos_token_id"]})
print("encoder cfg:", {k: cfg["encoder"].get(k) for k in ["image_size", "patch_size", "hidden_size", "num_hidden_layers", "model_type"]})

def io(path):
    s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    print(os.path.basename(path), os.path.getsize(path) // 1024, "KB")
    for i in s.get_inputs(): print("   in ", i.name, i.type, i.shape)
    for o in s.get_outputs(): print("   out", o.name, o.type, o.shape)
    return s
enc = io(f["encoder_model.onnx"]); dec = io(f["decoder_model.onnx"])

from tokenizers import Tokenizer
T = Tokenizer.from_file(f["tokenizer.json"])

def prep(img, resample=Image.BICUBIC):
    img = img.convert("RGB").resize((384, 384), resample)
    a = (np.asarray(img).astype(np.float32) / 255.0 - 0.5) / 0.5
    return a.transpose(2, 0, 1)[None]

def run(enc, dec, x, maxlen=200):
    h = enc.run(None, {enc.get_inputs()[0].name: x})[0]
    ids = [2]
    names = [i.name for i in dec.get_inputs()]
    for _ in range(maxlen):
        feed = {"input_ids": np.array([ids], dtype=np.int64), "encoder_hidden_states": h}
        feed = {k: v for k, v in feed.items() if k in names}
        logits = dec.run(None, feed)[0]
        n = int(logits[0, -1].argmax())
        if n == 2: break
        ids.append(n)
    return ids[1:]

def simple(ids):
    return "".join(inv[i] for i in ids if i > 3)

os.makedirs("imgs", exist_ok=True)
forms = [r"2x^3+x^2-13x+6=0", r"\frac{x^2+3x-1}{x+1}", r"\sqrt{5-2x}=3", r"\sin(2x+1)+\cos(1-x)", r"x=\frac{-b\pm\sqrt{b^2-4ac}}{2a}", r"3\cdot(4+7)-\frac{1}{2}", r"2x+5=11", r"\log_{2}8+e^{2x}", r"\sqrt[3]{27}+|x-2|"]
paths = []
for i, s in enumerate(forms):
    fig = plt.figure(figsize=(4, 1.2), dpi=150); fig.text(0.5, 0.5, "$" + s + "$", ha="center", va="center", fontsize=22)
    p = f"imgs/r{i}.png"; fig.savefig(p); plt.close(fig); paths.append(p)
os.system("git clone -q --depth 1 https://github.com/breezedeus/Pix2Text p2t 2>&1 | tail -1")
ex = sorted(glob.glob("p2t/docs/examples/*"))
print("examples:", [os.path.basename(e) for e in ex])
paths += [e for e in ex if any(k in os.path.basename(e).lower() for k in ["formula", "hw", "math"]) and e.lower().endswith((".jpg", ".png", ".jpeg"))][:8]

variants = {"fp32": (enc, dec)}
for tag, kw in [("q_all", {}), ("q_matmul", {"op_types_to_quantize": ["MatMul", "Gemm"]})]:
    try:
        qe, qd = f"enc_{tag}.onnx", f"dec_{tag}.onnx"
        quantize_dynamic(f["encoder_model.onnx"], qe, weight_type=QuantType.QUInt8, **kw)
        quantize_dynamic(f["decoder_model.onnx"], qd, weight_type=QuantType.QUInt8, **kw)
        print(tag, "sizes KB", os.path.getsize(qe) // 1024, os.path.getsize(qd) // 1024)
        variants[tag] = (ort.InferenceSession(qe, providers=["CPUExecutionProvider"]), ort.InferenceSession(qd, providers=["CPUExecutionProvider"]))
        print(tag, "ops", sorted({n.op_type for n in onnx.load(qe).graph.node} | {n.op_type for n in onnx.load(qd).graph.node}))
    except Exception as e:
        print(tag, "QUANT FAILED", repr(e)[:300])

for p in paths:
    img = Image.open(p)
    print("\n###", p, img.size)
    for tag, (e, d) in variants.items():
        t0 = time.time(); ids = run(e, d, prep(img)); dt = time.time() - t0
        print(f"  {tag:9s} {dt:5.1f}s n={len(ids):3d} | {T.decode(ids, skip_special_tokens=True)}")
    ids = run(enc, dec, prep(img, Image.BILINEAR))
    print("  bilinear ", T.decode(ids, skip_special_tokens=True))
    print("  simple   ", simple(run(enc, dec, prep(img))))
    print("  rawtokens", [inv[i] for i in run(enc, dec, prep(img))][:40])

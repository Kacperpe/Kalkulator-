# ONNX Runtime wywołuje te klasy z kodu natywnego, więc nie wolno ich usuwać ani zmieniać nazw.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

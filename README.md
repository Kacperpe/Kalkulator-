# Kalkulator

Naukowy kalkulator na Androida (Kotlin, bez zewnętrznych bibliotek), układ wzorowany na Casio.

**Funkcje:** + − × ÷, potęgi, pierwiastki (√, ³√, ˣ√), x⁻¹, silnia, %, mod, log/ln/logₓy/10ˣ/eˣ,
sin/cos/tan/cot i funkcje odwrotne, hiperboliczne (hyp), nPr/nCr, GCD/LCM, Ceil/Floor, π, e, Ans/PreAns,
całka ∫(f, a, b), pochodna d/dx(f, a), Σ i Π (po zmiennej x), zmienne a–d, f, x, y, t i pamięć M (STO/RCL, M+/M−),
tryby DEG/RAD/GRAD, S⇔D (ułamek ↔ dziesiętna), ENG, historia, kopiuj/wklej, krzyżak (◀ ▶ kursor, ▲ ▼ historia).
Żółte napisy nad klawiszami to funkcje po **SHIFT**, fioletowe po **ALPHA**.
Klawisze bez implementacji (MATRIX, STAT, CMPLX, SOLVE itd.) pokazują „Funkcja niedostępna".

## Narzędzia (przycisk ≡)

Wykres funkcji (przeciąganie i uszczypnięcie), rozwiązywanie równań z x (także SHIFT+CALC), własne funkcje f, g, h,
teoria liczb (rozkład na czynniki, dzielniki, systemy liczbowe, zapis rzymski), postacie liczby (ułamek, okres, %, DMS),
statystyka jedno- i dwuwymiarowa z regresją liniową, macierze (wyznacznik, rząd, transpozycja, odwrotność, działania), skórki.

## Wzór ze zdjęcia

Panel boczny (≡) → „Zrób zdjęcie równania" albo „Wybierz zdjęcie z galerii", zaznacz ramką jeden wzór i stuknij „Rozpoznaj".
Rozpoznawanie działa w telefonie, bez internetu: model [Pix2Text-MFR](https://huggingface.co/breezedeus/pix2text-mfr) (licencja MIT)
uruchamiany przez ONNX Runtime. Model jest pobierany i zmniejszany podczas budowania (`tools/prepare_model.py`), nie leży w repozytorium.
Obsługiwane: działania, ułamki, potęgi, pierwiastki, wartość bezwzględna, funkcje trygonometryczne i logarytmy, równania z jedną niewiadomą.
Nieobsługiwane: całki, sumy, macierze, układy równań, nierówności, znak ±.

## Pobranie APK

Każdy push na `main` uruchamia GitHub Actions, które testuje silnik obliczeń, buduje APK
i dodaje go do zakładki **Releases** repozytorium (plik `Kalkulator.apk`).
Na telefonie: otwórz Releases → pobierz `Kalkulator.apk` → zainstaluj
(przy pierwszej instalacji Android poprosi o zgodę na instalację z nieznanych źródeł).

Build można też odpalić ręcznie: zakładka **Actions → Build APK → Run workflow**.

## Podpisywanie

`app/release.jks` to klucz do własnego użytku (hasło `kalkulator`) - dzięki niemu kolejne wersje
instalują się jako aktualizacja. Jeśli repo ma być publiczne i zależy Ci na bezpieczeństwie
podpisu, wygeneruj własny klucz i przenieś hasła do GitHub Secrets.

## Lokalny build

```
gradle assembleRelease
```
(wymaga Android SDK i JDK 17)

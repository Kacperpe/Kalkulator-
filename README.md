# Kalkulator

Naukowy kalkulator na Androida (Kotlin, bez zewnętrznych bibliotek), układ wzorowany na Casio.

**Funkcje:** + − × ÷, potęgi, pierwiastki (√, ∛, ʸ√x), x⁻¹, silnia, %, log/ln/10ˣ/eˣ,
sin/cos/tan i funkcje odwrotne (SHIFT), hiperboliczne (hyp), nPr/nCr, π, e, Ans, notacja ×10ˣ,
tryby DEG/RAD/GRAD, przełącznik S⇔D (ułamek ↔ liczba dziesiętna), kursor ◀ ▶, DEL/AC.
Żółte napisy nad klawiszami to funkcje po wciśnięciu **SHIFT**.

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

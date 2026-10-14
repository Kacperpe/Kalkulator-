#!/bin/bash
# Test na emulatorze: klikanie klawiszy, ułamki, rozpoznanie wzoru ze „zdjęcia" udostępnionego do aplikacji.
OUT=e2e
mkdir -p $OUT
PKG=pl.kacper.kalkulator
log() { echo "[e2e] $*" | tee -a $OUT/report.txt; }
shot() { adb exec-out screencap -p > $OUT/$1.png; }
dumpui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > $OUT/$1.xml; python3 tools/e2e/find.py $OUT/$1.xml > $OUT/$1.txt; }
tapk() {
  local xy
  xy=$(awk -F'\t' -v k="$1" '$1==k {print $2; exit}' $OUT/keys.txt)
  if [ -z "$xy" ]; then log "BRAK KLAWISZA: $1"; return 1; fi
  adb shell input tap $xy
  sleep 0.5
}
tapui() {
  dumpui tmp
  local xy
  xy=$(awk -F'\t' -v k="$1" 'index(tolower($1), tolower(k))==1 {print $2; exit}' $OUT/tmp.txt)
  if [ -z "$xy" ]; then log "BRAK NA EKRANIE: $1"; return 1; fi
  adb shell input tap $xy
}
waitfor() { # tekst, sekundy
  for i in $(seq 1 $2); do
    dumpui tmp
    if grep -qi "$1" $OUT/tmp.txt; then return 0; fi
    sleep 1
  done
  return 1
}

adb install -r e2e.apk > $OUT/install.txt 2>&1 || { log "INSTALACJA NIEUDANA"; cat $OUT/install.txt; exit 0; }
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity > /dev/null
sleep 5
shot 01_start
dumpui keys
log "klawisze: $(wc -l < $OUT/keys.txt)"

# 1/2 + 3/4 = 5/4
for k in 1 a/b 2 + 3 a/b 4; do tapk "$k"; done
shot 02_ulamki_wpisane
tapk "="
sleep 1
shot 03_ulamki_wynik
tapk "S⇔D"
sleep 1
shot 04_ulamek_wynik
tapk "AC"

# pusty ułamek: najpierw klawisz ułamka, potem licznik, ▶ (krzyżak), mianownik
tapk "a/b"; tapk 7; shot 05_pusty_ulamek
tapk "AC"

# potęga i pierwiastek
for k in 2 "xʸ" 1 0 − "√x" 9; do tapk "$k"; done
shot 06_potega_pierwiastek
tapk "="
sleep 1
shot 07_potega_wynik
tapk "AC"

# rozpoznawanie: obraz udostępniony do aplikacji
adb push e2e_photo.png /sdcard/Pictures/e2e_photo.png > /dev/null
adb shell touch /sdcard/Pictures/e2e_photo.png
adb push e2e_photo.png /sdcard/Download/e2e_photo.png > /dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/e2e_photo.png > /dev/null
sleep 4
ID=$(adb shell content query --uri content://media/external/images/media --projection _id:_display_name | grep e2e_photo | sed -E 's/.*_id=([0-9]+).*/\1/' | head -1)
log "media id: $ID"
adb shell am start -a android.intent.action.SEND -t image/png --eu android.intent.extra.STREAM content://media/external/images/media/$ID --grant-read-uri-permission -n $PKG/.MainActivity > /dev/null
sleep 4
if ! (dumpui tmp; grep -qi "Rozpoznaj" $OUT/tmp.txt); then
  log "udostępnienie z MediaStore bez dostępu - plik w katalogu aplikacji"
  adb shell am start -W -n $PKG/.MainActivity > /dev/null
  D=/sdcard/Android/data/$PKG/files
  adb shell mkdir -p $D
  adb push e2e_photo.png $D/e2e.png > /dev/null
  adb shell am start -a android.intent.action.SEND -t image/png --eu android.intent.extra.STREAM file://$D/e2e.png -n $PKG/.MainActivity > /dev/null
  sleep 4
fi
if ! (dumpui tmp; grep -qi "Rozpoznaj" $OUT/tmp.txt); then
  log "udostępnienie z powłoki nie dało dostępu do pliku - przez galerię"
  adb shell am start -W -n $PKG/.MainActivity > /dev/null
  sleep 2
  tapk "≡"; sleep 1
  tapui "Wybierz zdjęcie z galerii"
  sleep 3
  tapui "Show roots" && sleep 2
  dumpui roots
  tapui "Downloads" && sleep 3
  shot 08a_galeria
  if waitfor "e2e_photo" 20; then
    sleep 1
    for t in 1 2 3; do
      sleep 2
      if [ $t = 1 ]; then tapui "e2e_photo.png"; else
        dumpui tmp
        xy=$(awk -F'\t' 'index($1,"e2e_photo.png")==1 {print $2; exit}' $OUT/tmp.txt)
        adb shell input swipe $xy $xy 900
        sleep 1
        dumpui sel
        tapui "Select" || adb shell input keyevent KEYCODE_ENTER
      fi
      sleep 3
      dumpui tmp
      grep -qi "Rozpoznaj" $OUT/tmp.txt && break
    done
    shot 08b_po_wyborze
  else
    shot 08a_galeria
    log "w galerii nie widać pliku"
  fi
  sleep 4
fi
shot 08_ramka
T0=$(date +%s)
tapui "Rozpoznaj" || log "nie ma przycisku Rozpoznaj"
if waitfor "Rozpoznany wzór\|Błąd rozpoznawania\|Nie rozpoznano" 240; then
  log "rozpoznanie po $(( $(date +%s) - T0 )) s"
else
  log "ROZPOZNANIE NIE SKOŃCZYŁO SIĘ W 240 s"
fi
shot 09_rozpoznany
cp $OUT/tmp.txt $OUT/rozpoznany.txt
tapui "Wstaw" && sleep 2
shot 10_wstawiony

adb logcat -d > $OUT/logcat.txt
if grep -q "FATAL EXCEPTION\|Fatal signal" $OUT/logcat.txt; then log "AWARIA APLIKACJI - zob. logcat"; grep -A20 "FATAL EXCEPTION\|Fatal signal" $OUT/logcat.txt | head -60 >> $OUT/report.txt; fi
log "koniec"

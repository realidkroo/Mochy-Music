# open a playlist from Home, fling down its songs and back up, back out; twice
# needs a playlist card on Home at PLAYLIST_TAP (default 280 1250): Home keeps its place, so scroll it there once before tracing
TAP=${PLAYLIST_TAP:-"280 1250"}
for i in 1 2; do
  adb shell input tap ${=TAP}; sleep 4
  for j in 1 2 3; do adb shell input swipe 540 1700 540 500 250; sleep 1.2; done
  for j in 1 2 3; do adb shell input swipe 540 600 540 1800 250; sleep 1.2; done
  adb shell input keyevent 4; sleep 2.5
done

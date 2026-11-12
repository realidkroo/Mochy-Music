# open the player, open the lyrics page from the bottom card, scroll it, try a swipe up for the queue, back out
for i in 1; do
adb shell input tap 400 2010; sleep 3
adb shell input tap 540 2330; sleep 3
adb shell input swipe 540 1800 540 900 300; sleep 1.5
adb shell input swipe 540 900 540 1800 300; sleep 1.5
adb shell input swipe 540 2280 540 1500 500; sleep 3
adb shell input keyevent 4; sleep 2.5
adb shell input keyevent 4; sleep 2.5
done

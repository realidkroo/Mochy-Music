# settings -> customisation: scroll the long page, open the player styles studio, swipe styles, back out to Home
adb shell input tap 870 2223; sleep 2.5
adb shell input swipe 540 1700 540 500 300; sleep 1.5
adb shell input tap 376 1818; sleep 3
adb shell input tap 384 1606; sleep 3
for i in 1 2 3; do adb shell input swipe 540 1800 540 400 300; sleep 1.2; done
for i in 1 2 3 4; do adb shell input swipe 540 600 540 1900 300; sleep 1; done
adb shell input swipe 540 1700 540 720 1000; sleep 1.5
adb shell input tap 540 1400; sleep 4
for i in 1 2 3 4; do adb shell input swipe 800 900 150 900 250; sleep 1.8; done
adb shell input keyevent 4; sleep 3
adb shell input keyevent 4; sleep 3
adb shell input keyevent 4; sleep 2.5
adb shell input keyevent 4; sleep 2.5
adb shell input tap 195 2223; sleep 2

# profile tab -> open settings -> audio lab -> transition (crossmix) -> app behaviour, back out to Home
adb shell input tap 870 2223; sleep 2.5
adb shell input swipe 540 1700 540 500 300; sleep 1.5
adb shell input tap 376 1818; sleep 3
adb shell input tap 344 1190; sleep 3
adb shell input swipe 540 1700 540 500 300; sleep 1.2; adb shell input swipe 540 1700 540 500 300; sleep 1.5
adb shell input tap 228 1488; sleep 3
adb shell input swipe 540 1700 540 500 300; sleep 1.2; adb shell input swipe 540 500 540 1700 300; sleep 1.5
adb shell input keyevent 4; sleep 2; adb shell input keyevent 4; sleep 2.5
adb shell input tap 384 1398; sleep 3
adb shell input swipe 540 1700 540 500 300; sleep 1.2; adb shell input swipe 540 1700 540 500 300; sleep 1.2; adb shell input swipe 540 500 540 1700 300; sleep 1.5
adb shell input keyevent 4; sleep 2.5
adb shell input keyevent 4; sleep 2.5
adb shell input tap 195 2223; sleep 2

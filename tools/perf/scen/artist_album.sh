# library tab -> artists -> an artist page, scroll it, open its album, scroll, back out to Home
adb shell input tap 654 2223; sleep 2.5
adb shell input tap 654 1482; sleep 2.5
adb shell input tap 360 864; sleep 5
for i in 1 2 3; do adb shell input swipe 540 1700 540 500 250; sleep 1.2; done
for i in 1 2 3; do adb shell input swipe 540 500 540 1700 250; sleep 1.2; done
adb shell input tap 528 1446; sleep 5
for i in 1 2 3; do adb shell input swipe 540 1700 540 600 250; sleep 1.2; done
for i in 1 2 3; do adb shell input swipe 540 600 540 1700 250; sleep 1.2; done
adb shell input keyevent 4; sleep 2
adb shell input keyevent 4; sleep 2
adb shell input keyevent 4; sleep 2
adb shell input tap 195 2223; sleep 2

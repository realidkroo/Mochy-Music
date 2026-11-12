# search tab: focus the field, type a word, run it, scroll the results, back out to Home
adb shell input tap 425 2223; sleep 2.5
adb shell input tap 500 430; sleep 2
adb shell input text "blues"; sleep 4
adb shell input keyevent 66; sleep 6
for i in 1 2 3; do adb shell input swipe 540 1700 540 600 250; sleep 1.2; done
for i in 1 2 3; do adb shell input swipe 540 600 540 1700 250; sleep 1.2; done
adb shell input keyevent 4; sleep 2
adb shell input keyevent 4; sleep 1.5
adb shell input tap 195 2223; sleep 2

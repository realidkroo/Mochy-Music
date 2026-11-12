# open the player and swipe the cover left and right (8 swipes), then drag the player closed
adb shell input tap 400 2010; sleep 3
for i in 1 2 3 4; do adb shell input swipe 900 700 200 700 200; sleep 2.5; adb shell input swipe 200 700 900 700 200; sleep 2.5; done
adb shell input keyevent 4; sleep 2.5

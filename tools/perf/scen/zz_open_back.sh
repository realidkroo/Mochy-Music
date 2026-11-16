# open the player from the mini player and close it with back, three times
for i in 1 2 3; do adb shell input tap 400 2010; sleep 3; adb shell input keyevent 4; sleep 3; done

# open the player from the mini player, wait, back out, five times
for i in 1 2 3 4 5; do adb shell input tap 400 2010; sleep 3; adb shell input keyevent 4; sleep 2.5; done

# open the player, expand the chevron buttons, open the other menu sheet, close it, three times
adb shell input tap 400 2010; sleep 3
for i in 1 2 3; do adb shell input tap 944 1370; sleep 2; adb shell input tap 944 823; sleep 2.5; adb shell input keyevent 4; sleep 2.5; done
adb shell input keyevent 4; sleep 2.5

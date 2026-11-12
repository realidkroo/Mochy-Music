# tv: open the first card of the second row, wait for its page, go back, three times
k() { adb shell input keyevent $1; sleep ${2:-0.4}; }
for i in 1 2 3; do k DPAD_DOWN 0.8; k DPAD_CENTER 2.5; k BACK 2.5; k DPAD_UP 0.8; done

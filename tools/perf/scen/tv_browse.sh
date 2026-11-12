# tv: walk down home's rows with the remote, along a row and back, then up again
k() { adb shell input keyevent $1; sleep ${2:-0.35}; }
for i in 1 2 3 4 5 6; do k DPAD_DOWN 0.6; done
for i in 1 2 3 4 5 6 7 8; do k DPAD_RIGHT 0.25; done
for i in 1 2 3 4 5 6 7 8; do k DPAD_LEFT 0.25; done
for i in 1 2 3 4 5 6; do k DPAD_UP 0.6; done
sleep 1

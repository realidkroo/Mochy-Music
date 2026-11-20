# the first flings of a freshly started app: rows and cards only play their reveal the first time a process shows them
# start the app fresh before tracing: am force-stop, launch, wait for Home to land (about 12 s with the intro)
for i in 1 2 3 4 5; do adb shell input swipe 540 1700 540 500 250; sleep 1.2; done
for i in 1 2; do adb shell input swipe 540 600 540 1800 250; sleep 1.2; done

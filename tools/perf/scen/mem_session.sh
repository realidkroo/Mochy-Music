# cold start after the trace is running (heapprofd only sees a process it met at birth), play from home, then two song changes with the ui hidden
PKG=${PERF_PKG:-com.mochyy.mochymusic.profileable}
adb shell am force-stop $PKG; sleep 1
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 12
adb shell input keyevent KEYCODE_MEDIA_PLAY; sleep 20
adb shell input keyevent KEYCODE_HOME; sleep 60
adb shell input keyevent KEYCODE_MEDIA_NEXT; sleep 30
adb shell input keyevent KEYCODE_MEDIA_NEXT; sleep 30

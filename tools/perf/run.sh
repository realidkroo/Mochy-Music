#!/bin/zsh
# records one perfetto trace while a scenario script drives the device
# usage: tools/perf/run.sh <trace-name> <scenario>   (scenario = a file in tools/perf/scen without .sh)
# traces land in $PERF_OUT (default: $TMPDIR/mochy-perf); PERF_CFG=heap.cfg swaps the frame config for the native heap one
HERE=${0:A:h}
OUT=${PERF_OUT:-${TMPDIR:-/tmp}/mochy-perf}
PKG=${PERF_PKG:-com.mochyy.mochymusic.profileable}
mkdir -p $OUT
adb push $HERE/${PERF_CFG:-trace.cfg} /data/local/tmp/mtrace.cfg >/dev/null
# compose tracing has to be switched on again after every app start
adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING $PKG/androidx.tracing.perfetto.TracingReceiver >/dev/null 2>&1
adb shell rm -f /data/misc/perfetto-traces/$1.pftrace
PID=$(adb shell "cat /data/local/tmp/mtrace.cfg | perfetto --txt -c - -o /data/misc/perfetto-traces/$1.pftrace --background" | tr -d '\r' | tail -1)
sleep 1.5
zsh $HERE/scen/$2.sh
sleep 1
adb shell kill -TERM $PID
sleep 3
adb pull /data/misc/perfetto-traces/$1.pftrace $OUT/$1.pftrace >/dev/null && echo $OUT/$1.pftrace

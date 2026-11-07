#!/bin/zsh
# helpers for leak and battery checks on a device: memory counts, per-thread cpu over a window, wake locks and sensors; source it, then call mem, heap, cpu or holds
PKG=${PKG:-com.mochyy.mochymusic.debug}
OUT=${PERF_OUT:-${TMPDIR:-/tmp}/mochy-perf}
mkdir -p $OUT

pid() { adb shell pidof $PKG | tr -d '\r'; }

# the meminfo lines that matter: heap sizes and the object counts that give a leak away
mem() {
  adb shell dumpsys meminfo $PKG | tr -d '\r' | grep -E "TOTAL PSS|Java Heap|Native Heap|Graphics|Activities|ViewRootImpl|AppContexts|Views:|WebViews|Death Recipients|Local Binders" | sed 's/^ *//'
}

# forces a gc by asking for a heap dump, then pulls it as a standard hprof
heap() {
  local name=$1
  adb shell am dumpheap -g $(pid) /data/local/tmp/$name.hprof >/dev/null 2>&1
  sleep 6
  adb pull /data/local/tmp/$name.hprof $OUT/$name.raw.hprof >/dev/null 2>&1
  hprof-conv $OUT/$name.raw.hprof $OUT/$name.hprof && rm $OUT/$name.raw.hprof
  echo $OUT/$name.hprof
}

# cpu ticks (utime+stime, 1/100 s) each thread used over [seconds], busiest first
cpu() {
  local secs=${1:-10}
  local p=$(pid)
  local before=$(adb shell "for t in /proc/$p/task/*; do echo \$(basename \$t) \$(cat \$t/comm | tr ' ' _) \$(cut -d' ' -f14,15 \$t/stat); done" | tr -d '\r')
  sleep $secs
  local after=$(adb shell "for t in /proc/$p/task/*; do echo \$(basename \$t) \$(cat \$t/comm | tr ' ' _) \$(cut -d' ' -f14,15 \$t/stat); done" | tr -d '\r')
  python3 - "$before" "$after" "$secs" <<'EOF'
import sys
def read(text):
    out = {}
    for line in text.splitlines():
        parts = line.split()
        if len(parts) >= 4:
            out[parts[0]] = (parts[1], int(parts[2]) + int(parts[3]))
    return out
b, a, secs = read(sys.argv[1]), read(sys.argv[2]), float(sys.argv[3])
rows = []
for tid, (name, ticks) in a.items():
    used = ticks - b.get(tid, (name, ticks))[1]
    if used > 0:
        rows.append((used, name, tid))
rows.sort(reverse=True)
total = sum(r[0] for r in rows)
print(f"total {total} ticks in {secs:.0f}s ({total / secs:.1f}% of one core)")
for used, name, tid in rows[:12]:
    print(f"  {used:5d}  {name} ({tid})")
EOF
}

# wake locks, alarms and sensors the app holds right now
holds() {
  echo "-- wake locks"; adb shell dumpsys power | tr -d '\r' | grep -i "mochy\|ExoPlayer" | head
  echo "-- sensors"; adb shell dumpsys sensorservice | tr -d '\r' | grep -i "mochy" | head
  echo "-- jobs/alarms"; adb shell dumpsys alarm | tr -d '\r' | grep -i "mochy" | head -5
}

"""Spike S7: wall time of a command, N runs, from exec to exit; prints every run, the median and min.

    python3 -I timeit.py <runs> <command> [args...]

The first run is reported but counted like the others (no warm-up discarded: the file cache is warm after the
build anyway). Output of the command goes to /dev/null except for the first run, which is echoed.
"""
import statistics
import subprocess
import sys
import time

runs = int(sys.argv[1])
cmd = sys.argv[2:]
times = []
for i in range(runs):
    t = time.perf_counter()
    p = subprocess.run(cmd, capture_output=True, text=True)
    dt = (time.perf_counter() - t) * 1000.0
    times.append(dt)
    if i == 0:
        sys.stdout.write(p.stdout)
        sys.stdout.write(p.stderr)
    if p.returncode != 0:
        print(f"run {i + 1}: exit {p.returncode}")
print("runs ms: " + " ".join(f"{x:.1f}" for x in times))
print(f"median {statistics.median(times):.1f} ms  min {min(times):.1f} ms  max {max(times):.1f} ms  (n={runs})")

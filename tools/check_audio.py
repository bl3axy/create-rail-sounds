#!/usr/bin/env python3
"""
Checks the actual audio in the resource pack, not just that the files exist.

check_sounds.py proves every sound is declared and the right length. This decodes them and
checks they are worth playing: that they are not silent, not clipped, that the looping ones
join cleanly at the wrap, and that the startup clip lands at the level the idle picks up so
the handoff has no step in it.

A silent or clipped file is the kind of thing that ships unnoticed because the build is green
and the file is clearly there.

Needs ffmpeg and numpy; skips cleanly if either is missing so it can't block a build.

Usage: python3 tools/check_audio.py [resource-pack-dir]
"""
import os
import struct
import subprocess
import sys
import tempfile
import wave

try:
    import numpy as np
except ImportError:
    print("  skip audio checks (no numpy)")
    sys.exit(0)

PACK = sys.argv[1] if len(sys.argv) > 1 else "server-resourcepack/assets/railsounds"
SOUNDS = os.path.join(PACK, "sounds")

# the ones the mod replays back to back; their wrap has to be inaudible
LOOPING = ("diesel_idle", "diesel_run", "loco_bell", "crossing_bell", "clack")
# played at engineVolume, then the idle takes over at idleVolume - these must match
START, IDLE = "diesel_start", "diesel_idle"
START_VOL, IDLE_VOL = 0.70, 0.25

fail = []


def decode(path, tmp):
    out = os.path.join(tmp, os.path.basename(path) + ".wav")
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path,
                        "-ac", "1", "-ar", "44100", out], capture_output=True)
    if r.returncode != 0 or not os.path.exists(out):
        return None, 0
    w = wave.open(out)
    n = w.getnframes()
    d = np.array(struct.unpack("<%dh" % n, w.readframes(n)), dtype=float) / 32768
    return d, w.getframerate()


if subprocess.run(["which", "ffmpeg"], capture_output=True).returncode != 0:
    print("  skip audio checks (no ffmpeg)")
    sys.exit(0)

files = sorted(f for f in os.listdir(SOUNDS) if f.endswith(".ogg"))
if not files:
    print("  FAIL no .ogg files in " + SOUNDS)
    sys.exit(1)

levels = {}
with tempfile.TemporaryDirectory() as tmp:
    for f in files:
        name = f[:-4]
        d, sr = decode(os.path.join(SOUNDS, f), tmp)
        if d is None or len(d) == 0:
            fail.append("%s will not decode - the file is corrupt" % name)
            continue
        peak = float(np.max(np.abs(d)))
        rms = float(np.sqrt((d ** 2).mean()))
        levels[name] = (d, sr, peak, rms)

        if rms < 1e-4:
            fail.append("%s is silent (rms %.6f) - nothing will be heard" % (name, rms))
        if peak >= 0.999:
            # count how much sits at full scale; a few samples is rounding, a lot is clipping
            flat = int(np.sum(np.abs(d) >= 0.999))
            if flat > sr * 0.001:
                fail.append("%s is clipped (%d samples at full scale)" % (name, flat))

    # looping clips: the wrap must not step
    for name in LOOPING:
        if name not in levels:
            continue
        d, sr, _, rms = levels[name]
        step = abs(float(d[0] - d[-1]))
        if step > 0.10:
            fail.append("%s has a %.3f jump at the loop point - that is an audible click"
                        % (name, step))
        # the end should not be wildly quieter or louder than the start either
        k = int(0.02 * sr)
        a = float(np.sqrt((d[-k:] ** 2).mean())) + 1e-9
        b = float(np.sqrt((d[:k] ** 2).mean())) + 1e-9
        ratio = max(a, b) / min(a, b)
        if ratio > 25:
            fail.append("%s jumps %.0fx in level across the loop point" % (name, ratio))

    # the start -> idle handoff
    if START in levels and IDLE in levels:
        ds, srs = levels[START][0], levels[START][1]
        di = levels[IDLE][0]
        tail = float(np.sqrt((ds[-int(1.2 * srs):-int(0.35 * srs)] ** 2).mean()))
        idle_rms = float(np.sqrt((di ** 2).mean()))
        want = idle_rms * IDLE_VOL / START_VOL
        if want > 0:
            off = tail / want
            if off < 0.5 or off > 2.0:
                fail.append("%s ends at rms %.4f but the idle picks up at an effective %.4f "
                            "(%.1fx out) - the fade will step" % (START, tail, want, off))

for f in fail:
    print("  FAIL " + f)
if not fail:
    loud = max(levels.items(), key=lambda kv: kv[1][3])
    print("  ok   %d clips decode, none silent or clipped, %d loops join cleanly, "
          "start->idle levels match" % (len(levels), len([n for n in LOOPING if n in levels])))
sys.exit(1 if fail else 0)

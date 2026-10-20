#!/usr/bin/env python3
"""
Runs the real notch maths out of Engines.java and checks it behaves.

stepOf() and pitchFor() decide what the engine sounds like at every speed, and nothing tested
them - the in-game suite can place blocks but it has no train, so this is the only way to
exercise them short of driving one. The bodies are lifted from the source and compiled, same
trick as check_migrations.py, so this cannot drift from what actually ships.

What it checks:
  - a stopped train is notch 0 and flat out is the top notch, with nothing out of range
  - the notch never goes DOWN as speed goes up, and every notch is reachable
  - pitch rises with notch, starts at enginePitchMin, ends at enginePitchMax
  - pitch always stays inside Minecraft's own 0.5-2.0, including with silly config values
  - reverse (negative speed) sounds the same as forward
  - a zero or broken maxSpeed doesn't divide by zero or throw
"""
import os
import re
import subprocess
import sys
import tempfile

ENG = "src/main/java/com/railsounds/Engines.java"
CFG = "src/main/java/com/railsounds/RailConfig.java"
eng = open(ENG).read()
cfg = open(CFG).read()


def body(name, src):
    m = re.search(r"private static \w+ %s\([^)]*\) \{(.*?)\n    \}" % name, src, re.S)
    if not m:
        sys.exit("could not find %s() in the source" % name)
    return m.group(1)


steps_b = body("steps", eng)
step_of = body("stepOf", eng)
pitch_for = body("pitchFor", eng)

# the three config values the maths reads, taken from their real defaults
defaults = {}
for key in ("dieselNotches",):
    defaults[key] = re.search(r"public int %s = (\d+)" % key, cfg).group(1)
for key in ("enginePitchMin", "enginePitchMax"):
    defaults[key] = re.search(r"public float %s = ([\d.]+)f" % key, cfg).group(1) + "f"

java = """
public class NotchCheck {
    static class Values {
        int dieselNotches = %s;
        float enginePitchMin = %s;
        float enginePitchMax = %s;
    }
    static class Train { double speed; float top = 1.0f; float maxSpeed() { return top; } }

    static int steps(Values c) {%s
    }
    static int stepOf(Train train, Values c) {%s
    }
    static float pitchFor(int step, Values c) {%s
    }

    static int fail = 0;
    static void check(String what, boolean ok) {
        if (!ok) { System.out.println("  FAIL " + what); fail++; }
    }

    static void sweep(Values c, float top, String label) {
        Train t = new Train(); t.top = top;
        int n = steps(c);
        int last = -1;
        boolean[] seen = new boolean[n];
        for (int i = 0; i <= 2000; i++) {
            t.speed = top * i / 1000.0;            // 0 up to twice top speed
            int s = stepOf(t, c);
            check(label + ": step " + s + " out of range at speed " + t.speed, s >= 0 && s < n);
            check(label + ": notch went DOWN as speed rose (" + last + "->" + s + ")", s >= last);
            if (s >= 0 && s < n) seen[s] = true;
            last = s;
            float p = pitchFor(s, c);
            check(label + ": pitch " + p + " outside Minecraft's 0.5-2.0", p >= 0.5f && p <= 2.0f);
            // reverse must sound identical
            double fwd = t.speed; t.speed = -fwd;
            check(label + ": reverse differs from forward at " + fwd, stepOf(t, c) == s);
            t.speed = fwd;
        }
        for (int i = 0; i < n; i++) check(label + ": notch " + i + " is unreachable", seen[i]);
        t.speed = 0;
        check(label + ": stopped is not notch 0", stepOf(t, c) == 0);
        t.speed = top;
        check(label + ": flat out is not the top notch", stepOf(t, c) == n - 1);
    }

    public static void main(String[] a) {
        Values c = new Values();
        int n = steps(c);
        check("default notches should be 8, got " + n, n == 8);
        check("pitch at notch 0 should be enginePitchMin",
              Math.abs(pitchFor(0, c) - c.enginePitchMin) < 1e-5);
        check("pitch at the top notch should be enginePitchMax",
              Math.abs(pitchFor(n - 1, c) - c.enginePitchMax) < 1e-5);
        for (int i = 1; i < n; i++)
            check("pitch did not rise from notch " + (i - 1) + " to " + i,
                  pitchFor(i, c) > pitchFor(i - 1, c));

        sweep(c, 1.0f, "default");
        sweep(c, 0.25f, "slow line");
        sweep(c, 4.0f, "fast line");

        // config values a person could plausibly type in
        Values one = new Values(); one.dieselNotches = 1;
        check("one notch should still give a legal pitch",
              pitchFor(stepOf(new Train(), one), one) >= 0.5f);
        Values many = new Values(); many.dieselNotches = 999;
        check("notches should be capped, got " + steps(many), steps(many) <= 32);
        Values zero = new Values(); zero.dieselNotches = 0;
        check("zero notches must not crash", steps(zero) >= 1);
        Values wide = new Values(); wide.enginePitchMin = 0.01f; wide.enginePitchMax = 9.0f;
        for (int i = 0; i < steps(wide); i++) {
            float p = pitchFor(i, wide);
            check("silly pitch config escaped the clamp: " + p, p >= 0.5f && p <= 2.0f);
        }
        // a train whose top speed is nonsense must not divide by zero
        Train broken = new Train(); broken.top = 0f; broken.speed = 5;
        int s = stepOf(broken, c);
        check("zero maxSpeed gave " + s, s >= 0 && s < steps(c));
        broken.top = Float.NaN;
        s = stepOf(broken, c);
        check("NaN maxSpeed gave " + s, s >= 0 && s < steps(c));

        if (fail == 0) {
            System.out.println("  ok   notch maths: " + n + " notches, monotonic, all reachable,");
            System.out.println("       pitch " + pitchFor(0, c) + " to " + pitchFor(n - 1, c)
                               + ", clamped, reverse matches forward, no divide-by-zero");
        } else {
            System.out.println("  " + fail + " failures");
            System.exit(1);
        }
    }
}
""" % (defaults["dieselNotches"], defaults["enginePitchMin"], defaults["enginePitchMax"],
       steps_b, step_of, pitch_for)

with tempfile.TemporaryDirectory() as tmp:
    path = os.path.join(tmp, "NotchCheck.java")
    open(path, "w").write(java)
    r = subprocess.run(["java", path], capture_output=True, text=True)
    for line in (r.stdout + r.stderr).strip().splitlines():
        if not line.startswith("Picked up"):
            print(line)
    sys.exit(r.returncode)

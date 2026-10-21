#!/usr/bin/env python3
"""
Finds z-fighting in the block models.

Two elements whose faces sit at exactly the same coordinate on an axis, while overlapping on
the other two, give the renderer two surfaces at identical depth. It can't decide which is in
front, so the pixels swap back and forth as you move - what you see in game is a texture
flickering or shimmering on the block.

That is what the searchlight signal's backplate was doing: two slabs both running z 6->7 over
the same patch. Eyeballing fifty models for it is hopeless, so it's checked here.

Elements that merely TOUCH (one ends where the next begins) are fine - that's ordinary
adjacency, not two surfaces fighting for the same pixel.

Rotated elements are reported separately: a rotation moves the plane, so a pair that looks
coplanar in raw coordinates may be fine in practice. Those are warnings, not failures.

Usage: python3 tools/check_models.py [models-dir]
"""
import json
import os
import sys

MODELS = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/railsounds/models/block"
AXES = ("x", "y", "z")
EPS = 1e-9

fail = []
warn = []


def overlap(a0, a1, b0, b1):
    """length of the overlap between two intervals; 0 if they only touch"""
    lo, hi = max(min(a0, a1), min(b0, b1)), min(max(a0, a1), max(b0, b1))
    return max(0.0, hi - lo)


def faces_of(el):
    return set(el.get("faces", {}))


# which faces live on which axis, and at the min or the max end
SIDE = {"west": ("x", 0), "east": ("x", 1), "down": ("y", 0),
        "up": ("y", 1), "north": ("z", 0), "south": ("z", 1)}

checked = 0
for name in sorted(os.listdir(MODELS)):
    if not name.endswith(".json"):
        continue
    model = json.load(open(os.path.join(MODELS, name)))
    els = model.get("elements")
    if not els:
        continue
    checked += 1
    for i in range(len(els)):
        for j in range(i + 1, len(els)):
            a, b = els[i], els[j]
            af, bf = faces_of(a), faces_of(b)
            if not af or not bf:
                continue
            a0, a1 = a["from"], a["to"]
            b0, b1 = b["from"], b["to"]
            rotated = "rotation" in a or "rotation" in b
            for ax_i, ax in enumerate(AXES):
                others = [k for k in range(3) if k != ax_i]
                # must genuinely overlap on BOTH other axes, not just touch
                o1 = overlap(a0[others[0]], a1[others[0]], b0[others[0]], b1[others[0]])
                o2 = overlap(a0[others[1]], a1[others[1]], b0[others[1]], b1[others[1]])
                if o1 <= EPS or o2 <= EPS:
                    continue
                for end in (0, 1):
                    av = a0[ax_i] if end == 0 else a1[ax_i]
                    bv = b0[ax_i] if end == 0 else b1[ax_i]
                    if abs(av - bv) > EPS:
                        continue
                    # both elements must actually draw a face on that plane
                    side = [s for s, (sax, sEnd) in SIDE.items() if sax == ax and sEnd == end]
                    if not side:
                        continue
                    s = side[0]
                    if s not in af or s not in bf:
                        continue
                    msg = ("%s: elements %d and %d both draw their '%s' face at %s=%s "
                           "and overlap %.2fx%.2f there" % (name, i, j, s, ax, av, o1, o2))
                    (warn if rotated else fail).append(msg)

# A nudge (or a typo) can turn a thin element inside out or push it past what Minecraft will
# load, and either way the model silently renders wrong, so both are checked here too.
for name in sorted(os.listdir(MODELS)):
    if not name.endswith(".json"):
        continue
    model = json.load(open(os.path.join(MODELS, name)))
    for i, el in enumerate(model.get("elements", [])):
        for ax_i, ax in enumerate(AXES):
            lo, hi = el["from"][ax_i], el["to"][ax_i]
            if hi - lo <= EPS:
                fail.append("%s: element %d is inside out or flat on %s (%s -> %s)"
                            % (name, i, ax, lo, hi))
            if lo < -16 - EPS or hi > 32 + EPS:
                fail.append("%s: element %d is outside Minecraft's -16..32 limit on %s "
                            "(%s -> %s) and the model will not load"
                            % (name, i, ax, lo, hi))

for w in warn:
    print("  warn " + w + " (one is rotated - check it in game)")
for f in fail:
    print("  FAIL " + f)
if not fail:
    print("  ok   %d models checked, no coplanar faces%s"
          % (checked, " (%d rotated pairs to eyeball)" % len(warn) if warn else ""))
sys.exit(1 if fail else 0)

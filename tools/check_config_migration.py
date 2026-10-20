#!/usr/bin/env python3
"""
Checks a config that the real server has just loaded and migrated.

CI plants an old-format config before the first boot; this runs after. It proves three things
at once: migrations actually applied, hand-tuned values were left alone, and fields missing
from the old file came back as their defaults rather than zeros (which is what would happen if
Gson ever stopped using the no-arg constructor - the mod would go silent and nothing would say so).

Usage: python3 tools/check_config_migration.py run/config/railsounds.json
"""
import json
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "run/config/railsounds.json"
c = json.load(open(path))
bad = []


def want(key, expected):
    if key not in c:
        bad.append("%s missing entirely" % key)
        return
    got = c[key]
    if isinstance(expected, bool):
        if got != expected:
            bad.append("%s is %r, expected %r" % (key, got, expected))
    elif abs(float(got) - float(expected)) > 1e-6:
        bad.append("%s is %s, expected %s" % (key, got, expected))


# migrations must have applied to values still sitting on their old defaults
want("configVersion", 4)
want("engineSounds", True)      # v3
want("clackVolume", 0.65)       # v4
want("couplerVolume", 0.85)     # v4
want("idleVolume", 0.25)        # v4
want("uncoupleMaxSpeed", 0.75)  # v2
want("cutCoastFactor", 0.06)    # v2

# a value the player changed by hand must NOT be touched
want("brakeVolume", 0.9)

# fields absent from the old file must come back as real defaults, not zeros
for key in ("idleHearingRange", "engineHearingRange", "runClipTicks",
            "locoBellVolume", "hornHearingRange", "idleClipTicks"):
    if key not in c:
        bad.append("new field %s missing" % key)
    elif not c[key]:
        bad.append("new field %s came back as %r - defaults did not apply" % (key, c[key]))

for b in bad:
    print("  FAIL " + b)
if not bad:
    print("  ok   old config migrated, hand-tuned values kept, new fields defaulted")
sys.exit(1 if bad else 0)

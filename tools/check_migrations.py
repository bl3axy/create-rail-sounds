#!/usr/bin/env python3
"""
Guards the config migration chain. Each step ends by setting configVersion to its own number,
so a step placed out of order bumps the version past an earlier one and that earlier step
silently never runs. This has slipped through twice by hand, so CI checks it now.
"""
import re, sys

src = open('src/main/java/com/railsounds/RailConfig.java').read()
body = re.search(r'static void migrate\(Values v\) \{(.*?)\n    \}', src, re.S)
if not body:
    sys.exit("could not find migrate()")
body = body.group(1)

guards = [int(n) for n in re.findall(r'if \(v\.configVersion < (\d+)\)', body)]
sets   = [int(n) for n in re.findall(r'v\.configVersion = (\d+);', body)]

fail = []
if guards != sorted(guards):
    fail.append(f"steps are out of order: guards run {guards}, must be ascending")
if guards != sets:
    fail.append(f"each step must set the version it guards: guards {guards} vs sets {sets}")
if len(set(guards)) != len(guards):
    fail.append(f"duplicate step numbers: {guards}")
if guards and guards != list(range(guards[0], guards[0] + len(guards))):
    fail.append(f"gap in the chain: {guards}")

default = re.search(r'public int configVersion = (\d+)', src)
if default and guards and int(default.group(1)) >= guards[0]:
    fail.append(f"configVersion default {default.group(1)} skips the first step (< {guards[0]})")

for f in fail:
    print("  FAIL " + f)
if not fail:
    print(f"  ok   migration chain {guards} is ordered, complete and self-consistent")
sys.exit(1 if fail else 0)

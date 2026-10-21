#!/usr/bin/env python3
"""
Checks the config the real server just loaded and migrated.

CI plants an old-format config before the first boot; this runs after it. It proves migrations
applied, hand-tuned values were left alone, and fields missing from the old file came back as
their defaults rather than zeros (which is what would happen if Gson ever stopped using the
no-arg constructor - the mod would go quiet and nothing would say so).

NOTHING HERE IS A TYPED-IN EXPECTED VALUE. This check hardcoded configVersion == 4 once and
broke a good build the day a v5 step was added; then it hardcoded idleVolume == 0.25 and broke
another when that default changed. So instead it lifts the real migrate() and the real field
defaults out of RailConfig.java, runs them over the SAME planted config the server was given,
and compares the server's answer against that. The expectations move with the source.

Usage: check_config_migration.py <migrated config> <the planted original>
"""
import json
import os
import re
import subprocess
import sys
import tempfile

migrated_path = sys.argv[1] if len(sys.argv) > 1 else "run/config/railsounds.json"
planted_path = sys.argv[2] if len(sys.argv) > 2 else "planted-config.json"

SRC = "src/main/java/com/railsounds/RailConfig.java"
src = open(SRC).read()
actual = json.load(open(migrated_path))
planted = json.load(open(planted_path))

body = re.search(r"static void migrate\(Values v\) \{(.*?)\n    \}", src, re.S)
vals = re.search(r"public static class Values \{(.*?)\n    \}", src, re.S)
helper = re.search(r"(private static boolean eq\(float a, float b\) \{.*?\n    \})", src, re.S)
if not (body and vals and helper):
    sys.exit("could not read RailConfig.java")

fields = re.findall(r"public (boolean|int|double|float|String) (\w+) = ([^;]+);", vals.group(1))
types = {n: t for t, n, _ in fields}


def literal(name, value):
    t = types[name]
    if t == "boolean":
        return "true" if value else "false"
    if t == "float":
        return repr(float(value)) + "f"
    if t == "double":
        return repr(float(value))
    if t == "int":
        return str(int(value))
    return json.dumps(value)


sets = "\n".join("        v.%s = %s;" % (k, literal(k, v))
                 for k, v in planted.items() if k in types)
decls = "\n".join("        %s %s = %s;" % (t, n, d) for t, n, d in fields)
dumps = "".join('        System.out.println("%s\\t" + v.%s);\n' % (n, n) for t, n, d in fields)

java = """
public class ExpectCheck {
    static class Values {
%s
    }
    %s
    static void migrate(Values v) {%s
    }
    public static void main(String[] a) {
        Values v = new Values();
%s
        migrate(v);
%s    }
}
""" % (decls, helper.group(1), body.group(1), sets, dumps)

with tempfile.TemporaryDirectory() as tmp:
    path = os.path.join(tmp, "ExpectCheck.java")
    open(path, "w").write(java)
    r = subprocess.run(["java", path], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[:2000])
        sys.exit("could not run the real migrate()")

expected = {}
for line in r.stdout.splitlines():
    if "\t" not in line or line.startswith("Picked up"):
        continue
    k, v = line.split("\t", 1)
    expected[k] = v.strip()

bad = []


def same(name, want, got):
    if types.get(name) == "boolean":
        return str(got).lower() == want.lower()
    if types.get(name) == "String":
        return str(got) == want
    try:
        return abs(float(got) - float(want)) < 1e-6
    except (TypeError, ValueError):
        return str(got) == want


for name, want in sorted(expected.items()):
    if name not in actual:
        bad.append("%s is missing from the config the server wrote" % name)
    elif not same(name, want, actual[name]):
        bad.append("%s is %r but the real migrate() gives %s" % (name, actual[name], want))

# the planted file deliberately holds a hand-tuned value no migration should touch
if "brakeVolume" in planted and not same("brakeVolume", str(planted["brakeVolume"]), actual.get("brakeVolume")):
    bad.append("a hand-tuned brakeVolume of %s was overwritten with %r"
               % (planted["brakeVolume"], actual.get("brakeVolume")))

# fields absent from the old file must come back as real defaults, not zeros
for key in ("idleHearingRange", "engineHearingRange", "runClipTicks",
            "locoBellVolume", "hornHearingRange", "idleClipTicks"):
    if key in planted:
        continue
    if key not in actual:
        bad.append("new field %s missing" % key)
    elif not actual[key]:
        bad.append("new field %s came back as %r - defaults did not apply" % (key, actual[key]))

for b in bad:
    print("  FAIL " + b)
if not bad:
    print("  ok   all %d options match what the real migrate() produces from the planted file; "
          "hand-tuned values kept, absent fields defaulted" % len(expected))
sys.exit(1 if bad else 0)

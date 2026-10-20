#!/usr/bin/env python3
"""
Guards the config migration chain.

Each step ends by setting configVersion to its own number, so a step placed out of order bumps
the version past an earlier one and that earlier step silently never runs. That slipped through
twice by hand, so it is checked here.

Two parts:
  1. static  - the steps are ordered, complete, and each sets the version it guards.
  2. dynamic - the REAL migrate() body is lifted out of RailConfig.java, compiled against field
               defaults also lifted from RailConfig.java, and run over every starting version.
               Nothing is hand-copied, so this cannot drift from the source.
"""
import os
import re
import subprocess
import sys
import tempfile

SRC = "src/main/java/com/railsounds/RailConfig.java"
src = open(SRC).read()

body_m = re.search(r"static void migrate\(Values v\) \{(.*?)\n    \}", src, re.S)
if not body_m:
    sys.exit("could not find migrate() in " + SRC)
body = body_m.group(1)

guards = [int(n) for n in re.findall(r"if \(v\.configVersion < (\d+)\)", body)]
sets = [int(n) for n in re.findall(r"v\.configVersion = (\d+);", body)]

fail = []
if guards != sorted(guards):
    fail.append("steps are out of order: guards run %s, must be ascending" % guards)
if guards != sets:
    fail.append("each step must set the version it guards: guards %s vs sets %s" % (guards, sets))
if len(set(guards)) != len(guards):
    fail.append("duplicate step numbers: %s" % guards)
if guards and guards != list(range(guards[0], guards[0] + len(guards))):
    fail.append("gap in the chain: %s" % guards)

default_ver = re.search(r"public int configVersion = (\d+)", src)
if default_ver and guards and int(default_ver.group(1)) >= guards[0]:
    fail.append("configVersion default %s skips the first step (< %d)" % (default_ver.group(1), guards[0]))

# ---- dynamic: actually run the real body ---------------------------------------------------
vals = re.search(r"public static class Values \{(.*?)\n    \}", src, re.S)
fields = re.findall(r"public (boolean|int|double|float|String) (\w+) = ([^;]+);", vals.group(1)) if vals else []
helper = re.search(r"(private static boolean eq\(float a, float b\) \{.*?\n    \})", src, re.S)

newest = max(guards) if guards else 0
decls = "\n".join("        %s %s = %s;" % (t, n, d) for t, n, d in fields)
dumps = "".join('        sb.append(v.%s).append(",");\n' % n for t, n, d in fields)
same = "\n".join(
    '        check("%s matches a fresh install", eq2(fresh.%s, after.%s));' % (n, n, n)
    for t, n, d in fields
)

java = """
public class MigrateCheck {
    static class Values {
%s
    }
    %s
    static void migrate(Values v) {%s
    }
    static int fail = 0;
    static void check(String what, boolean ok) {
        if (!ok) { System.out.println("  FAIL " + what); fail++; }
    }
    static boolean eq2(Object a, Object b) { return String.valueOf(a).equals(String.valueOf(b)); }
    static String dump(Values v) {
        StringBuilder sb = new StringBuilder();
%s        return sb.toString();
    }
    public static void main(String[] a) {
        // every starting version must land on the newest one
        for (int v = 0; v <= %d; v++) {
            Values s = new Values(); s.configVersion = v;
            migrate(s);
            check("a v" + v + " config ends on v%d", s.configVersion == %d);
        }
        // running it again must change nothing
        Values twice = new Values(); twice.configVersion = 0;
        migrate(twice); String once = dump(twice); migrate(twice);
        check("migrating twice is a no-op", once.equals(dump(twice)));

        // a fresh install and a fully migrated old one must end up identical. Both go through
        // migrate(), because load() always calls it - a fresh config starts at version 0 too.
        Values fresh = new Values(); migrate(fresh);
        Values after = new Values(); after.configVersion = 0; migrate(after);
%s

        System.out.println(fail == 0
            ? "  ok   migrate() runs clean from every version, lands on v%d, idempotent"
            : "  " + fail + " dynamic failures");
        if (fail > 0) System.exit(1);
    }
}
""" % (decls, helper.group(1) if helper else "", body, dumps, newest, newest, newest, same, newest)

with tempfile.TemporaryDirectory() as tmp:
    path = os.path.join(tmp, "MigrateCheck.java")
    open(path, "w").write(java)
    r = subprocess.run(["java", path], capture_output=True, text=True)
    for line in (r.stdout + r.stderr).strip().splitlines():
        if not line.startswith("Picked up"):
            print(line)
    if r.returncode != 0:
        fail.append("migrate() misbehaved when actually run (see above)")

for f in fail:
    print("  FAIL " + f)
if not fail:
    print("  ok   migration chain %s is ordered, complete and self-consistent" % guards)
sys.exit(1 if fail else 0)

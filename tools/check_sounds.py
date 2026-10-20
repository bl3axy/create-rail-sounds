#!/usr/bin/env python3
"""
Guards the sound wiring.

A sound the code plays that isn't in sounds.json, or is in sounds.json with no .ogg behind it,
fails silently: the client logs one line nobody reads and the feature is simply mute. That
already happened once - the engine sounds were written, shipped and heard by nobody because
sounds.json had no entries for them.

Three checks:
  1. every entry in sounds.json has an .ogg file
  2. every .ogg file is referenced by sounds.json (nothing shipped dead)
  3. every sound name the Java code plays is declared in sounds.json

Check 3 looks at the places a sound name can actually come from - the argument list of a
SoundPlayer call, the argument list of any helper that takes a `String sound`, and any
`String ... = ...` holding a literal - rather than every string in the file, so command names
and the like don't come into it. Names reached through a constant are followed back to it.
Variants built by concatenation ("diesel_horn" + "_hold") aren't literals anywhere, so those
rest on checks 1 and 2.
"""
import json
import os
import re
import sys

PACK = "server-resourcepack/assets/railsounds"
SRC = "src/main/java/com/railsounds"

fail = []

j = json.load(open(os.path.join(PACK, "sounds.json")))
files = {f[:-4] for f in os.listdir(os.path.join(PACK, "sounds")) if f.endswith(".ogg")}

# ---- 1 + 2: sounds.json against the files on disk -----------------------------------------
referenced = set()
for key, entry in j.items():
    for s in entry.get("sounds", []):
        name = s if isinstance(s, str) else s.get("name", "")
        ns, _, path = name.partition(":")
        if not path:
            ns, path = "railsounds", name
        if ns != "railsounds":
            continue  # pointing at vanilla or another pack on purpose
        referenced.add(path.split("/")[-1])

missing = sorted(referenced - files)
if missing:
    fail.append("declared in sounds.json but no .ogg file: %s" % missing)

dead = sorted(files - referenced)
if dead:
    fail.append("shipped .ogg files nothing references: %s" % dead)

# ---- 3: what the code actually plays ------------------------------------------------------
CONST = re.compile(r'(?:static\s+final\s+)?String\s+(\w+)\s*=\s*"([a-z][a-z0-9_]*)"')
# a sound name parked in a variable called sound / xSound on its way to a call
ASSIGN = re.compile(r'\bString\s+(?:sound|\w+Sound)\s*=\s*([^;]*"[a-z][a-z0-9_]*"[^;]*);')
# a helper that takes a sound name is a sink just like SoundPlayer itself
SINK_DECL = re.compile(r"\b(\w+)\s*\([^)]*String\s+sound\b")


def args_at(text, open_paren):
    """the text between this '(' and its matching ')'"""
    depth, i = 0, open_paren
    while i < len(text):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren + 1:i]
        i += 1
    return ""


sources = []
constants = {}
helpers = set()

for root, _, names in os.walk(SRC):
    for n in sorted(names):
        if not n.endswith(".java"):
            continue
        text = open(os.path.join(root, n)).read()
        constants.update({k: v for k, v in CONST.findall(text)})
        helpers.update(SINK_DECL.findall(text))
        sources.append((n, text))

helpers -= {"play", "playLater", "stop"}       # those only count behind SoundPlayer.
played = set()
named_by_const = set()
CALL = re.compile(r"(?:SoundPlayer\.(?:play|playLater|stop)%s)\s*\(" % (
    "".join("|" + re.escape(h) for h in sorted(helpers))))

for n, text in sources:
    flat = re.sub(r"\s+", " ", text)
    for m in CALL.finditer(flat):
        inner = args_at(flat, m.end() - 1)
        played.update(re.findall(r'"([a-z][a-z0-9_]*)"', inner))
        named_by_const.update(re.findall(r"\b(SoundPlayer\.\w+|[A-Z][A-Z0-9_]{2,})\b", inner))
    # a name parked in a String variable on its way to one of those calls
    for rhs in ASSIGN.findall(flat):
        played.update(re.findall(r'"([a-z][a-z0-9_]*)"', rhs))

# follow the ones named through a constant
for ref in named_by_const:
    key = ref.rsplit(".", 1)[-1]
    if key in constants:
        played.add(constants[key])

if not played:
    fail.append("found no sound names in the code at all - this check has stopped working")

undeclared = sorted(played - set(j))
if undeclared:
    fail.append("played by the code but not in sounds.json: %s" % undeclared)

for f in fail:
    print("  FAIL " + f)
if not fail:
    print("  ok   %d sounds declared, %d files, %d names played by the code - all wired up"
          % (len(j), len(files), len(played)))
sys.exit(1 if fail else 0)

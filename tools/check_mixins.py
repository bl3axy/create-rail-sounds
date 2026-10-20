#!/usr/bin/env python3
"""
Every mixin class must be registered in a mixin config, and every config must be declared in
fabric.mod.json.

An unregistered mixin class compiles, ships, and does nothing at all - no warning anywhere. The
same silent-no-op shape as a sound missing from sounds.json, so it gets the same treatment.

Also checks the other direction: a config naming a class that no longer exists fails startup with
"required": true, which is a crash rather than a silent miss, but it's as easy to catch here.
"""
import json
import os
import sys

RES = "src/main/resources"
MIXIN_SRC = "src/main/java/com/railsounds/mixin"

fail = []

fabric = json.load(open(os.path.join(RES, "fabric.mod.json")))
configs = []
for entry in fabric.get("mixins", []):
    configs.append(entry if isinstance(entry, str) else entry["config"])

on_disk = {f for f in os.listdir(RES) if f.endswith("mixins.json")}
undeclared = sorted(on_disk - set(configs))
if undeclared:
    fail.append("mixin config not declared in fabric.mod.json: %s" % undeclared)

# class name -> package path, from the source tree
classes = {}
for root, _, names in os.walk(MIXIN_SRC):
    for n in names:
        if n.endswith(".java"):
            pkg = os.path.relpath(root, MIXIN_SRC).replace(os.sep, ".")
            pkg = "com.railsounds.mixin" + ("" if pkg == "." else "." + pkg)
            classes[n[:-5]] = pkg

registered = {}
for cfg in configs:
    path = os.path.join(RES, cfg)
    if not os.path.exists(path):
        fail.append("fabric.mod.json names a mixin config that isn't there: %s" % cfg)
        continue
    j = json.load(open(path))
    pkg = j.get("package", "")
    for key in ("mixins", "client", "server"):
        for name in j.get(key, []):
            registered[name] = (cfg, pkg)

missing = sorted(set(classes) - set(registered))
if missing:
    fail.append("mixin classes that exist but are registered nowhere, so they do nothing: %s"
                % missing)

ghosts = sorted(set(registered) - set(classes))
if ghosts:
    fail.append("registered in a mixin config but no such class: %s" % ghosts)

# a class registered under the wrong config's package won't resolve
for name, (cfg, pkg) in sorted(registered.items()):
    if name in classes and classes[name] != pkg:
        fail.append("%s is in package %s but %s declares package %s"
                    % (name, classes[name], cfg, pkg))

for f in fail:
    print("  FAIL " + f)
if not fail:
    print("  ok   %d mixin classes, all registered across %d configs, all packages match"
          % (len(classes), len(configs)))
sys.exit(1 if fail else 0)

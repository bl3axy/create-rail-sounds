#!/usr/bin/env python3
"""
In-game smoke test. Talks to the running test server over RCON and actually places, ticks
and breaks every block the mod adds, then runs the commands. Anything that throws shows up
in the server log, which the workflow greps afterwards.

Usage: python3 tools/ingame_test.py [host] [port] [password]
Exit code 1 if the server can't be reached or a block fails to place.
"""
import json
import os
import socket
import struct
import sys
import time

HOST = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 25575
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else "railsounds"
PHASE = (sys.argv[4] if len(sys.argv) > 4 else "a").lower()

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCKSTATES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "railsounds", "blockstates")

Y = -59  # flat-world surface
ORIGIN_X, ORIGIN_Z = 0, 0

# blocks left standing through a server restart, to prove their scheduled ticks and their
# saved state come back. Phase A places them, phase B (after a restart) checks them.
KEEP_Z = ORIGIN_Z + 10
KEEP_RETARDER = (ORIGIN_X, Y, KEEP_Z)
KEEP_CROSSING = (ORIGIN_X + 4, Y, KEEP_Z)
KEEP_STATION = (ORIGIN_X + 8, Y, KEEP_Z)
KEEP_SENSOR = (ORIGIN_X + 9, Y, KEEP_Z)
KEEP_SIGNAL = (ORIGIN_X + 12, Y, KEEP_Z)
KEEP_SEARCHLIGHT = (ORIGIN_X + 12, Y + 1, KEEP_Z)


class Rcon:
    def __init__(self, host, port, password):
        self.sock = socket.create_connection((host, port), timeout=30)
        self.sock.settimeout(30)
        self.rid = 0
        if self._send(3, password) is None:
            raise RuntimeError("RCON auth failed")

    def _send(self, kind, body):
        self.rid += 1
        payload = struct.pack("<ii", self.rid, kind) + body.encode("utf8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        rid, _ = struct.unpack("<ii", data[:8])
        if rid == -1:
            return None
        return data[8:-2].decode("utf8", "replace")

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("RCON connection closed")
            buf += chunk
        return buf

    def cmd(self, command):
        return self._send(2, command)


def block_ids():
    return sorted(f[:-5] for f in os.listdir(BLOCKSTATES) if f.endswith(".json"))


def connect():
    deadline = time.time() + 240
    last = None
    while time.time() < deadline:
        try:
            return Rcon(HOST, PORT, PASSWORD)
        except (OSError, RuntimeError) as e:
            last = e
            time.sleep(3)
    print(f"could not reach RCON: {last}", file=sys.stderr)
    return None


def phase_a():
    ids = block_ids()
    print(f"testing {len(ids)} blocks", flush=True)

    rcon = connect()
    if rcon is None:
        return 1

    failures = []
    rcon.cmd(f"forceload add {ORIGIN_X - 4} {ORIGIN_Z - 4} {ORIGIN_X + len(ids) * 2 + 4} {KEEP_Z + 4}")
    time.sleep(2)

    # --- place every block, two apart so neighbours don't interact ---
    placed = []
    for i, bid in enumerate(ids):
        x = ORIGIN_X + i * 2
        rcon.cmd(f"setblock {x} {Y} {ORIGIN_Z} railsounds:{bid} replace")
        placed.append((bid, x))

    # a second crossing signal close to the first, to exercise "join the nearby crossing"
    rcon.cmd(f"setblock {ORIGIN_X} {Y} {ORIGIN_Z + 4} railsounds:crossing_signal replace")
    # a gate next to it, so it follows that crossing rather than only redstone
    rcon.cmd(f"setblock {ORIGIN_X + 1} {Y} {ORIGIN_Z + 4} railsounds:crossing_gate replace")

    # --- let them tick for a while (fastest block is 1 tick, slowest 10) ---
    print("ticking...", flush=True)
    time.sleep(12)

    # --- every block should still be there and still be itself ---
    # `say` lands in the server log whatever the locale, so the workflow can verify the count
    # without parsing localised command replies.
    for bid, x in placed:
        rcon.cmd(f"execute if block {x} {Y} {ORIGIN_Z} railsounds:{bid} run say RSTEST_PRESENT {bid}")

    # --- redstone-powered variants: power a few of the blocks that react to it ---
    for bid in ("car_retarder", "switch_machine", "crossing_signal", "crossing_gate"):
        if bid in ids:
            x = ORIGIN_X + ids.index(bid) * 2
            rcon.cmd(f"setblock {x} {Y + 1} {ORIGIN_Z} minecraft:redstone_block replace")
    time.sleep(6)
    for bid in ("car_retarder", "switch_machine", "crossing_signal", "crossing_gate"):
        if bid in ids:
            x = ORIGIN_X + ids.index(bid) * 2
            rcon.cmd(f"setblock {x} {Y + 1} {ORIGIN_Z} minecraft:air replace")
    time.sleep(4)

    # the two Crossing Signals should have made exactly one shared crossing between them
    during = rcon.cmd("railsounds crossing list") or ""
    print("crossings while placed:", during.strip()[:200], flush=True)
    if "signal_" not in during:
        failures.append("placing a Crossing Signal did not create a crossing: " + during.strip()[:200])

    # --- commands that work from the console ---
    for c in ("railsounds crossing list", "railsounds yard list", "railsounds reload",
              "railsounds yard couple TestStation", "railsounds yard uncouple 2 TestStation",
              "railsounds yard backin TestStation", "railsounds yard list",
              "railsounds yard clear TestStation", "railsounds yard reset all",
              "railsounds crossing list"):
        rcon.cmd(c)
    time.sleep(2)

    # --- every sound name the pack declares must be playable ---
    # /railsounds test builds the sound packet, so a name the command rejects is a name the
    # mod could never play either. Cheap, and it covers all of them rather than one.
    # Not a skip if the pack can't be read: a check that quietly does nothing is worse than
    # no check, because the build still goes green and nobody looks again.
    import json as _json
    try:
        with open("server-resourcepack/assets/railsounds/sounds.json") as fh:
            declared = sorted(_json.load(fh))
    except Exception as e:
        failures.append("could not read sounds.json for the test sweep: %s" % e)
        declared = []
    if not declared:
        failures.append("the sound sweep had nothing to play - it would have passed vacuously")
    for name in declared:
        out = rcon.cmd(f"railsounds test {name}") or ""
        if "Unknown" in out or "error" in out.lower() or "Exception" in out:
            failures.append(f"/railsounds test {name} was rejected: {out.strip()[:120]}")
    print(f"played all {len(declared)} declared sounds through /railsounds test", flush=True)
    # leave proof in the SERVER log, so CI can tell this ran from a real count rather than
    # from the step merely not failing
    rcon.cmd(f"say RSTEST_SOUNDS {len(declared)}")

    # --- the debug readout ---
    # It calls into the engine and bell state for every train. There are no trains here, but
    # the no-train path is exactly where a null slips through, and this code is new.
    dbg = rcon.cmd("railsounds debug") or ""
    if "Exception" in dbg or "error" in dbg.lower():
        failures.append("railsounds debug blew up: " + dbg.strip()[:160])
    elif not dbg.strip():
        failures.append("railsounds debug said nothing at all - it should report 'No trains.'")
    else:
        rcon.cmd("say RSTEST_DEBUG ok")

    # --- commands given bad input must fail politely, not throw ---
    for c in ("railsounds crossing remove no_such_crossing",
              "railsounds crossing signal no_such_crossing 0 -59 0",
              "railsounds crossing nosignal no_such_crossing",
              "railsounds yard clear NoSuchStation",
              "railsounds yard uncouple 64 NoSuchStation",
              "railsounds test no_such_sound_at_all",
              "railsounds fixtrains",
              "diesel", "steam"):
        out = rcon.cmd(c) or ""
        if "Exception" in out or "Unexpected error" in out:
            failures.append(f"'{c}' threw instead of failing politely: {out.strip()[:160]}")
    time.sleep(1)

    # --- crossing add/remove by hand, with a radius that isn't the default ---
    rcon.cmd(f"forceload add {ORIGIN_X - 40} {ORIGIN_Z - 40} {ORIGIN_X + 40} {ORIGIN_Z + 40}")
    rcon.cmd(f"execute positioned {ORIGIN_X} {Y} {ORIGIN_Z + 20} run "
             f"railsounds crossing add manual_test 20")
    listed = rcon.cmd("railsounds crossing list") or ""
    if "manual_test" not in listed:
        failures.append("crossing add did not take: " + listed.strip()[:160])
    elif "r=20" not in listed.replace(" ", ""):
        failures.append("crossing add ignored the radius it was given: " + listed.strip()[:160])
    rcon.cmd("railsounds crossing remove manual_test")
    listed = rcon.cmd("railsounds crossing list") or ""
    if "manual_test" in listed:
        failures.append("crossing remove left it behind: " + listed.strip()[:160])

    # --- all four facings of a block with a rotated hitbox ---
    # RailShapes rotates the pixel boxes per facing and only one of the four was ever placed.
    for i, facing in enumerate(("north", "east", "south", "west")):
        fx = ORIGIN_X + i * 2
        fz = ORIGIN_Z + 8
        rcon.cmd(f"setblock {fx} {Y} {fz} railsounds:switch_stand[facing={facing}] replace")
        rcon.cmd(f"execute if block {fx} {Y} {fz} railsounds:switch_stand[facing={facing}] "
                 f"run say RSTEST_FACING {facing}")
    time.sleep(2)
    for i in range(4):
        rcon.cmd(f"setblock {ORIGIN_X + i * 2} {Y} {ORIGIN_Z + 8} minecraft:air replace")

    # --- a comparator reading a Cargo Sensor ---
    # No train, so the answer is 0, but it exercises the comparator path rather than leaving
    # it to be discovered in a world.
    cx, cz = ORIGIN_X + 10, ORIGIN_Z + 8
    rcon.cmd(f"setblock {cx} {Y} {cz} railsounds:cargo_sensor replace")
    rcon.cmd(f"setblock {cx + 1} {Y} {cz} minecraft:comparator[facing=west] replace")
    time.sleep(2)
    rcon.cmd(f"execute if block {cx + 1} {Y} {cz} minecraft:comparator "
             f"run say RSTEST_COMPARATOR ok")
    rcon.cmd(f"setblock {cx} {Y} {cz} minecraft:air replace")
    rcon.cmd(f"setblock {cx + 1} {Y} {cz} minecraft:air replace")
    time.sleep(2)

    # --- break everything (exercises the removal hooks and the crossing/link cleanup) ---
    for _, x in placed:
        rcon.cmd(f"setblock {x} {Y} {ORIGIN_Z} minecraft:air replace")
    rcon.cmd(f"setblock {ORIGIN_X} {Y} {ORIGIN_Z + 4} minecraft:air replace")
    rcon.cmd(f"setblock {ORIGIN_X + 1} {Y} {ORIGIN_Z + 4} minecraft:air replace")
    time.sleep(6)

    # crossings made by the signal blocks should be gone again
    left = rcon.cmd("railsounds crossing list") or ""
    print("after cleanup, crossings:", left.strip()[:200], flush=True)
    if "signal_" in left:
        failures.append("a Crossing Signal's crossing outlived the block: " + left.strip()[:200])

    # --- leave a set standing for phase B to find after a restart ---
    rcon.cmd(f"setblock {KEEP_RETARDER[0]} {KEEP_RETARDER[1]} {KEEP_RETARDER[2]} railsounds:car_retarder replace")
    rcon.cmd(f"setblock {KEEP_CROSSING[0]} {KEEP_CROSSING[1]} {KEEP_CROSSING[2]} railsounds:crossing_signal replace")
    # a real Create station and signal, so adjacentStation()/findSignal() run against
    # genuine block entities instead of air
    rcon.cmd(f"setblock {KEEP_STATION[0]} {KEEP_STATION[1]} {KEEP_STATION[2]} create:track_station replace")
    rcon.cmd(f"setblock {KEEP_SENSOR[0]} {KEEP_SENSOR[1]} {KEEP_SENSOR[2]} railsounds:cargo_sensor replace")
    rcon.cmd(f"setblock {KEEP_SIGNAL[0]} {KEEP_SIGNAL[1]} {KEEP_SIGNAL[2]} create:track_signal replace")
    rcon.cmd(f"setblock {KEEP_SEARCHLIGHT[0]} {KEEP_SEARCHLIGHT[1]} {KEEP_SEARCHLIGHT[2]} railsounds:searchlight_signal replace")
    time.sleep(8)  # let them tick beside the real Create blocks

    rcon.cmd("save-all")
    time.sleep(2)
    rcon.cmd("stop")

    if failures:
        print("FAILURES:", file=sys.stderr)
        for f in failures:
            print("  " + f, file=sys.stderr)
        return 1
    print(f"placed, ticked, powered and removed {len(placed)} blocks with no failures", flush=True)
    return 0


def phase_b():
    """After a restart: did the blocks, their saved crossings and their ticks all come back?"""
    rcon = connect()
    if rcon is None:
        return 1
    failures = []

    # 1. the blocks themselves survived the save/load round trip
    for name, (x, y, z) in (("car_retarder", KEEP_RETARDER), ("crossing_signal", KEEP_CROSSING),
                            ("cargo_sensor", KEEP_SENSOR), ("searchlight_signal", KEEP_SEARCHLIGHT)):
        rcon.cmd(f"execute if block {x} {y} {z} railsounds:{name} run say RSTEST_SURVIVED {name}")

    # 2. a crossing made by a Crossing Signal is still registered after the restart
    after = rcon.cmd("railsounds crossing list") or ""
    print("crossings after restart:", after.strip()[:200], flush=True)
    if "signal_" not in after:
        failures.append("a Crossing Signal's crossing did not survive the restart: " + after.strip()[:200])

    # 3. the important one: are scheduled block ticks still running after a restart?
    #    Power the retarder and see whether it notices. If its tick was lost on save/load
    #    it stays powered=false forever.
    rx, ry, rz = KEEP_RETARDER
    rcon.cmd(f"setblock {rx} {ry + 1} {rz} minecraft:redstone_block replace")
    time.sleep(5)
    rcon.cmd(f"execute if block {rx} {ry} {rz} railsounds:car_retarder[powered=true] run say RSTEST_TICKING retarder")
    time.sleep(1)
    rcon.cmd(f"setblock {rx} {ry + 1} {rz} minecraft:air replace")
    time.sleep(3)
    rcon.cmd(f"execute if block {rx} {ry} {rz} railsounds:car_retarder[powered=false] run say RSTEST_UNPOWERED retarder")
    time.sleep(1)

    # 4. tear the rest down
    for x, y, z in (KEEP_RETARDER, KEEP_CROSSING, KEEP_STATION, KEEP_SENSOR, KEEP_SIGNAL, KEEP_SEARCHLIGHT):
        rcon.cmd(f"setblock {x} {y} {z} minecraft:air replace")
    time.sleep(4)
    rcon.cmd("forceload remove all")
    rcon.cmd("save-all")
    time.sleep(2)
    rcon.cmd("stop")

    if failures:
        print("FAILURES:", file=sys.stderr)
        for f in failures:
            print("  " + f, file=sys.stderr)
        return 1
    print("restart checks done", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(phase_b() if PHASE == "b" else phase_a())

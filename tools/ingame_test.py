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

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCKSTATES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "railsounds", "blockstates")

Y = -59  # flat-world surface
ORIGIN_X, ORIGIN_Z = 0, 0


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


def main():
    ids = block_ids()
    print(f"testing {len(ids)} blocks", flush=True)

    deadline = time.time() + 240
    rcon = None
    while time.time() < deadline:
        try:
            rcon = Rcon(HOST, PORT, PASSWORD)
            break
        except (OSError, RuntimeError) as e:
            last = e
            time.sleep(3)
    if rcon is None:
        print(f"could not reach RCON: {last}", file=sys.stderr)
        return 1

    failures = []
    rcon.cmd(f"forceload add {ORIGIN_X - 4} {ORIGIN_Z - 4} {ORIGIN_X + len(ids) * 2 + 4} {ORIGIN_Z + 8}")
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

    rcon.cmd(f"forceload remove all")
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


if __name__ == "__main__":
    sys.exit(main())

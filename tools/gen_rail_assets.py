#!/usr/bin/env python3
"""
Generates every asset for the railroad blocks: textures (drawn pixel by pixel here, no outside
images), block models, blockstates, item definitions, loot tables, recipes, mining tags and
the English names. Run from the repo root:  python3 tools/gen_rail_assets.py
Re-running overwrites what it made; hand edits to those files will be lost.
"""
import copy
import json
import os
import random

from PIL import Image

NS = "railsounds"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", NS)
DATA = os.path.join(ROOT, "data", NS)

FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# --------------------------------------------------------------------------------------------
# textures
# --------------------------------------------------------------------------------------------

FONT = {  # 3x5 unless noted, '#' = ink
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", ".##", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "C": [".##", "#..", "#..", "#..", ".##"],
    "D": ["##.", "#.#", "#.#", "#.#", "##."], "G": [".##", "#..", "#.#", "#.#", ".##"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "L": ["#..", "#..", "#..", "#..", "###"],
    "N": ["#.#", "###", "###", "#.#", "#.#"], "O": [".#.", "#.#", "#.#", "#.#", ".#."],
    "P": ["##.", "#.#", "##.", "#..", "#.."], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "E": ["###", "#..", "##.", "#..", "###"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    "W": ["#...#", "#...#", "#.#.#", "##.##", "#...#"],
    " ": ["...", "...", "...", "...", "..."],
}


def text_width(s, scale=1, gap=None):
    gap = scale if gap is None else gap
    return sum(len(FONT[c][0]) * scale + gap for c in s) - gap


def draw_text(img, s, x, y, color, scale=1, gap=None):
    gap = scale if gap is None else gap
    for c in s:
        g = FONT[c]
        for gy, row in enumerate(g):
            for gx, ch in enumerate(row):
                if ch == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            img.putpixel((x + gx * scale + sx, y + gy * scale + sy), color)
        x += len(g[0]) * scale + gap


def draw_text_centered(img, s, cx, y, color, scale=1, gap=None):
    draw_text(img, s, cx - (text_width(s, scale, gap) + 1) // 2 + (1 if text_width(s, scale, gap) % 2 == 0 else 0),
              y, color, scale, gap)


def clamp(v):
    return max(0, min(255, int(v)))


def noisy(size, base, amount, seed, streak=None):
    """flat colour with per-pixel noise; streak='h'/'v' adds wood-grain lines"""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    line = [rnd.uniform(-amount, amount) for _ in range(size)]
    for y in range(size):
        for x in range(size):
            n = rnd.uniform(-amount, amount) * 0.6
            if streak == "h":
                n += line[y]
            elif streak == "v":
                n += line[x]
            img.putpixel((x, y), (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n), 255))
    return img


def rect(img, x1, y1, x2, y2, color):
    """filled, inclusive"""
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            img.putpixel((x, y), color)


def border(img, color, inset=0):
    w, h = img.size
    for x in range(inset, w - inset):
        img.putpixel((x, inset), color)
        img.putpixel((x, h - 1 - inset), color)
    for y in range(inset, h - inset):
        img.putpixel((inset, y), color)
        img.putpixel((w - 1 - inset, y), color)


def disc(img, cx, cy, r, color):
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                img.putpixel((x, y), color)


BLACK = (24, 24, 26, 255)
INK = (20, 20, 20, 255)
WHITE = (236, 236, 232, 255)

TEX = {}


def tex(name, img):
    TEX[name] = img


def make_textures():
    tex("black_paint", noisy(16, (32, 32, 35), 4, 1))
    tex("galvanized", noisy(16, (165, 168, 170), 9, 2))
    tex("aluminum", noisy(16, (196, 198, 200), 6, 3))
    tex("concrete", noisy(16, (198, 196, 190), 10, 4))
    tex("white_paint", noisy(16, (232, 232, 228), 4, 5))
    tex("yellow_paint", noisy(16, (222, 178, 32), 7, 6))
    tex("red_paint", noisy(16, (168, 32, 30), 7, 7))
    tex("dark_steel", noisy(16, (72, 70, 70), 7, 8))
    tex("rust", noisy(16, (118, 64, 36), 14, 9))
    tex("creosote", noisy(16, (64, 44, 30), 8, 10, "h"))
    tex("pole_wood", noisy(16, (112, 92, 70), 10, 11, "v"))
    tex("glass_insulator", noisy(16, (88, 160, 120), 10, 12))

    # tie / timber end grain
    end = noisy(16, (70, 50, 34), 6, 13)
    for r in range(2, 8, 2):
        for a in range(0, 360, 6):
            import math
            x = int(8 + r * math.cos(math.radians(a)))
            y = int(8 + r * math.sin(math.radians(a)))
            if 0 <= x < 16 and 0 <= y < 16:
                end.putpixel((x, y), (52, 36, 24, 255))
    tex("creosote_end", end)

    # signal lamps: lit = bright with hot centre, off = dark glass
    for name, lit, off in (("red", (240, 40, 30), (70, 18, 16)),
                           ("yellow", (255, 200, 40), (78, 62, 18)),
                           ("green", (60, 235, 120), (16, 60, 34))):
        on = Image.new("RGBA", (16, 16), BLACK)
        disc(on, 8, 8, 7.5, lit + (255,))
        disc(on, 8, 8, 4, (min(255, lit[0] + 60), min(255, lit[1] + 60), min(255, lit[2] + 60), 255))
        disc(on, 6.5, 6.5, 1.5, (255, 255, 240, 255))
        tex("lamp_" + name, on)
        dim = Image.new("RGBA", (16, 16), BLACK)
        disc(dim, 8, 8, 7.5, off + (255,))
        disc(dim, 6, 6, 1.5, (off[0] + 30, off[1] + 30, off[2] + 30, 255))
        tex("lamp_" + name + "_off", dim)
    dark = Image.new("RGBA", (16, 16), BLACK)
    disc(dark, 8, 8, 7.5, (36, 36, 40, 255))
    disc(dark, 6, 6, 1.5, (70, 70, 76, 255))
    tex("lamp_dark", dark)

    # Signal backplate. The rim used to be a circle at radius 7.0-7.8, which sits right on the
    # edge of the texture - UV'd onto the 4x5 head it got squashed into a clipped ellipse and
    # read as a broken ring. A rim inset from the edge with the corners knocked off keeps its
    # shape whatever aspect it lands on, and that is what a real backplate looks like: a dark
    # plate a bit bigger than the lamp, with a pale outline.
    back = Image.new("RGBA", (16, 16), BLACK)
    rim = (210, 210, 210, 255)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            corner = (x in (1, 14) and y in (1, 14))
            if edge == 1 and not corner:
                back.putpixel((x, y), rim)
    for x, y in ((2, 1), (13, 1), (2, 14), (13, 14), (1, 2), (14, 2), (1, 13), (14, 13)):
        back.putpixel((x, y), rim)
    tex("signal_backplate", back)

    # mile post: white face, black numbers stacked
    mp = noisy(16, (232, 232, 228), 3, 20)
    draw_text(mp, "4", 6, 2, INK)
    draw_text(mp, "2", 6, 9, INK)
    tex("mile_post_face", mp)

    # whistle post: big W
    w = noisy(16, (232, 232, 228), 3, 21)
    border(w, INK)
    draw_text_centered(w, "W", 8, 3, INK, 2)
    tex("whistle_post_face", w)

    # yard limit: 32px wide sign text in the top 14 rows
    yl = Image.new("RGBA", (32, 32), WHITE)
    rect(yl, 0, 0, 31, 13, WHITE)
    for x in range(32):
        yl.putpixel((x, 0), INK)
        yl.putpixel((x, 13), INK)
    for y in range(14):
        yl.putpixel((0, y), INK)
        yl.putpixel((31, y), INK)
    draw_text_centered(yl, "YARD", 16, 1 + 1, INK)
    draw_text_centered(yl, "LIMIT", 16, 7 + 0, INK)
    tex("yard_limit_face", yl)

    # Crossbuck boards. These used to carry RAILROAD / CROSSING in a 5px font, but each board
    # face is only 1.5 blocks-pixels tall and showed a 3px slice of the texture, so what you
    # actually saw was fragments of letters - black and white noise. No lettering survives at
    # this size, so the boards are plain white with a dark edge, which is what reads.
    # Rows 0-3 are the whole board; both faces UV to (0,0,16,4).
    cb = Image.new("RGBA", (16, 16), WHITE)
    for x in range(16):
        cb.putpixel((x, 0), INK)
        cb.putpixel((x, 3), INK)
    tex("crossbuck_boards", cb)

    # speed limit plates
    for i, mph in enumerate(["10", "15", "20", "25", "30", "40", "50", "60", "R"]):
        s = noisy(16, (232, 232, 228), 3, 40 + i)
        for x in range(16):  # top/bottom rule only - two big digits need the full width
            s.putpixel((x, 0), INK)
            s.putpixel((x, 15), INK)
        draw_text_centered(s, mph, 8, 3, INK, 2, 1)
        tex("speed_" + mph.lower(), s)

    # relay case door
    rc = noisy(16, (196, 198, 200), 6, 50)
    for y in range(1, 15):
        rc.putpixel((7, y), (120, 122, 124, 255))
        rc.putpixel((8, y), (225, 226, 228, 255))
    border(rc, (140, 142, 144, 255))
    rect(rc, 5, 7, 5, 9, BLACK)
    rect(rc, 10, 7, 10, 9, BLACK)
    tex("relay_case_door", rc)

    # battery box lid: hinges and handle
    bb = noisy(16, (160, 163, 165), 8, 51)
    border(bb, (110, 112, 114, 255))
    rect(bb, 3, 2, 4, 3, BLACK)
    rect(bb, 11, 2, 12, 3, BLACK)
    rect(bb, 6, 12, 9, 12, BLACK)
    tex("battery_box_lid", bb)

    # phone box door
    pb = noisy(16, (222, 178, 32), 7, 52)
    border(pb, (150, 118, 20, 255))
    rect(pb, 11, 6, 11, 9, BLACK)
    rect(pb, 4, 3, 9, 4, BLACK)
    tex("phone_box_door", pb)

    # bumper face: red/white chevrons
    bf = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            band = ((x + y) // 4) % 2
            bf.putpixel((x, y), (190, 30, 28, 255) if band else (235, 235, 230, 255))
    tex("buffer_face", bf)

    # yellow/black hazard stripes
    hz = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            band = ((x - y) // 3) % 2
            hz.putpixel((x, y), (225, 180, 30, 255) if band else (30, 30, 30, 255))
    tex("hazard_stripes", hz)

    # keg staves + hoops, and keg top with spike heads
    kg = noisy(16, (130, 96, 60), 8, 53, "v")
    for x in range(0, 16, 4):
        for y in range(16):
            kg.putpixel((x, y), (96, 70, 44, 255))
    for y in (2, 3, 12, 13):
        for x in range(16):
            kg.putpixel((x, y), (60, 60, 62, 255))
    tex("keg_side", kg)
    kt = noisy(16, (120, 88, 56), 8, 54)
    border(kt, (60, 60, 62, 255))
    rnd = random.Random(55)
    for _ in range(14):
        x, y = rnd.randint(3, 12), rnd.randint(3, 12)
        kt.putpixel((x, y), (55, 52, 50, 255))
        kt.putpixel((x + 1, y), (90, 88, 86, 255))
    tex("keg_top", kt)

    # rail steel (side) with shiny head line
    rs = noisy(16, (84, 80, 78), 6, 56)
    for x in range(16):
        rs.putpixel((x, 0), (160, 160, 165, 255))
        rs.putpixel((x, 1), (130, 128, 128, 255))
    tex("rail_steel", rs)

    # wheel face: steel disc with hub
    wf = noisy(16, (80, 78, 78), 6, 57)
    disc(wf, 8, 8, 3, (110, 108, 106, 255))
    disc(wf, 8, 8, 1.5, (60, 58, 58, 255))
    border(wf, (150, 150, 152, 255))
    tex("wheel_face", wf)

    # yard light lens (underside)
    ll = Image.new("RGBA", (16, 16), (255, 244, 210, 255))
    disc(ll, 8, 8, 5, (255, 255, 245, 255))
    border(ll, (120, 120, 120, 255))
    tex("yard_light_lens", ll)

    # detector cabinet door with vent and lamp
    dd = noisy(16, (170, 172, 174), 7, 58)
    border(dd, (110, 112, 114, 255))
    for y in (3, 5, 7):
        rect(dd, 4, y, 11, y, (90, 92, 94, 255))
    rect(dd, 7, 11, 8, 12, BLACK)
    tex("detector_door", dd)

    # cargo sensor / car counter fronts: dark panel with a bar gauge and a lamp
    for powered in (False, True):
        cs = noisy(16, (60, 64, 66), 5, 59)
        border(cs, (150, 152, 154, 255))
        rect(cs, 3, 3, 12, 9, (24, 30, 26, 255))
        for i, x in enumerate(range(4, 12)):
            h = 2 + i // 2
            col = (60, 200, 90, 255) if i < 6 else (230, 190, 40, 255) if i < 7 else (230, 60, 40, 255)
            rect(cs, x, 9 - h + 1, x, 8, col)
        lamp = (255, 60, 40, 255) if powered else (80, 30, 26, 255)
        rect(cs, 7, 11, 8, 12, lamp)
        tex("cargo_sensor_front" + ("_on" if powered else ""), cs)

        cc = noisy(16, (60, 64, 66), 5, 60)
        border(cc, (150, 152, 154, 255))
        rect(cc, 2, 3, 13, 9, (24, 30, 26, 255))
        draw_text(cc, "C" if not powered else "8", 3, 4, (255, 170, 40, 255))
        draw_text(cc, "0" if not powered else "8", 7, 4, (255, 170, 40, 255))
        rect(cc, 11, 4, 12, 8, (40, 46, 42, 255))
        lamp = (60, 220, 90, 255) if powered else (24, 60, 34, 255)
        rect(cc, 7, 11, 8, 12, lamp)
        tex("car_counter_front" + ("_on" if powered else ""), cc)

    # loading hatch: grate top, riveted sides
    gt = Image.new("RGBA", (16, 16), (28, 28, 30, 255))
    for i in range(0, 16, 3):
        for j in range(16):
            gt.putpixel((i, j), (110, 108, 104, 255))
            gt.putpixel((j, i), (110, 108, 104, 255))
    border(gt, (150, 146, 140, 255))
    tex("loading_hatch_top", gt)
    hs = noisy(16, (108, 104, 98), 8, 61)
    border(hs, (80, 76, 72, 255))
    for x in (2, 13):
        for y in (2, 13):
            hs.putpixel((x, y), (160, 156, 150, 255))
    tex("loading_hatch_side", hs)

    # interlocking panel front: maroon cabinet with numbered plates
    ip = noisy(16, (104, 30, 28), 6, 62)
    border(ip, (60, 18, 16, 255))
    for c in range(4):
        cx = 1 + 4 * c
        rect(ip, cx, 2, cx + 2, 8, (215, 210, 196, 255))
        draw_text(ip, str(c + 1), cx, 3, INK)
        rect(ip, cx, 9, cx + 2, 15, (215, 210, 196, 255))
        draw_text(ip, str(c + 5), cx, 10, INK)
    tex("interlocking_front", ip)
    tex("maroon_paint", noisy(16, (104, 30, 28), 6, 63))

    # switch stand target colours
    tex("target_green", noisy(16, (40, 150, 70), 6, 64))
    tex("target_red", noisy(16, (190, 34, 30), 6, 65))
    tex("target_gray", noisy(16, (120, 120, 120), 6, 66))

    # retarder: yellow rails with black grip teeth
    rt = noisy(16, (210, 170, 30), 7, 70)
    for x in range(1, 15, 3):
        rect(rt, x, 2, x + 1, 13, (38, 38, 38, 255))
    border(rt, (120, 96, 18, 255))
    tex("retarder_top", rt)
    rs2 = noisy(16, (180, 145, 26), 7, 71)
    for y in (4, 11):
        for x in range(16):
            rs2.putpixel((x, y), (38, 38, 38, 255))
    tex("retarder_side", rs2)

    # switch machine: grey motor case with a rod slot and a position window
    sm = noisy(16, (150, 152, 154), 7, 72)
    border(sm, (96, 98, 100, 255))
    rect(sm, 2, 6, 13, 8, (60, 62, 64, 255))
    rect(sm, 3, 7, 12, 7, (190, 192, 194, 255))
    rect(sm, 11, 10, 13, 12, (40, 180, 90, 255))
    tex("switch_machine_front", sm)

    # route sensor: dark pad with an orange eye
    rsn = noisy(16, (58, 58, 62, 255)[:3], 5, 73)
    border(rsn, (140, 142, 144, 255))
    disc(rsn, 8, 8, 4, (235, 140, 30, 255))
    disc(rsn, 8, 8, 2, (255, 210, 120, 255))
    tex("route_sensor_top", rsn)

    # switch linker item: a brass key-ish wand with a green tip
    sl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(10):
        sl.putpixel((3 + i, 12 - i), (176, 142, 54, 255))
        sl.putpixel((4 + i, 12 - i), (208, 176, 80, 255))
        sl.putpixel((3 + i, 13 - i), (120, 96, 34, 255))
    rect(sl, 2, 11, 4, 13, (96, 72, 28, 255))
    rect(sl, 11, 1, 13, 3, (60, 220, 110, 255))
    sl.putpixel((12, 2), (210, 255, 225, 255))
    tex("switch_linker", sl)

    # crossing gate arm: red/white stripes along its length
    ga = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            ga.putpixel((x, y), (200, 30, 28, 255) if (x // 4) % 2 else (240, 240, 236, 255))
    tex("gate_arm", ga)

    # switch machine lid with motor label, route sensor face
    sm = noisy(16, (150, 152, 150), 7, 70)
    border(sm, (90, 92, 90, 255))
    rect(sm, 3, 3, 12, 6, (60, 62, 60, 255))
    rect(sm, 6, 9, 9, 12, (200, 170, 40, 255))
    tex("switch_machine_lid", sm)
    rs = noisy(16, (222, 178, 32), 7, 71)
    border(rs, (150, 118, 20, 255))
    disc(rs, 8, 8, 3, (30, 30, 34, 255))
    disc(rs, 7, 7, 1, (120, 200, 255, 255))
    tex("route_sensor_face", rs)

    # switch linker item: brass key with a green/red target flag (transparent background)
    li = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    brass = (214, 170, 72, 255)
    dark = (130, 96, 34, 255)
    for i in range(3, 13):
        li.putpixel((i, 15 - i), brass)
        li.putpixel((i + 1, 15 - i), dark)
    for (x, y) in ((2, 12), (3, 13), (2, 13), (4, 11), (4, 13)):
        li.putpixel((x, y), dark)
    rect(li, 10, 1, 14, 3, (40, 160, 70, 255))
    rect(li, 10, 4, 14, 5, (200, 40, 34, 255))
    rect(li, 11, 1, 11, 6, (60, 60, 60, 255))
    item_out = os.path.join(ASSETS, "textures", "item")
    os.makedirs(item_out, exist_ok=True)
    li.save(os.path.join(item_out, "switch_linker.png"))

    out = os.path.join(ASSETS, "textures", "block")
    os.makedirs(out, exist_ok=True)
    for name, img in TEX.items():
        img.save(os.path.join(out, name + ".png"))


# --------------------------------------------------------------------------------------------
# models
# --------------------------------------------------------------------------------------------

def t(name):
    return f"{NS}:block/{name}"


def el(frm, to, faces, rot=None, shade=True):
    """faces: dict face -> texture key (string) or (key, uv); 'all' fills every face"""
    out = {}
    if "all" in faces:
        base = faces["all"]
        for f in ("north", "south", "east", "west", "up", "down"):
            out[f] = base
    for k, v in faces.items():
        if k != "all":
            out[k] = v
    face_json = {}
    for f, v in out.items():
        if v is None:
            continue
        if isinstance(v, tuple):
            face_json[f] = {"texture": "#" + v[0], "uv": list(v[1])}
        else:
            face_json[f] = {"texture": "#" + v}
    e = {"from": list(frm), "to": list(to), "faces": face_json}
    if rot:
        e["rotation"] = rot
    if not shade:
        e["shade"] = False
    return e


_SIDES = {"west": ("x", 0), "east": ("x", 1), "down": ("y", 0),
          "up": ("y", 1), "north": ("z", 0), "south": ("z", 1)}
_AXES = ("x", "y", "z")
_NUDGE = 0.02


def _overlap(a0, a1, b0, b1):
    lo, hi = max(min(a0, a1), min(b0, b1)), min(max(a0, a1), max(b0, b1))
    return max(0.0, hi - lo)


def deconflict(elements):
    """
    Pull apart element faces that sit at exactly the same depth.

    Two surfaces at an identical coordinate give the renderer no way to decide which is in
    front, so the pixels swap as the camera moves - in game that reads as the texture
    flickering. The searchlight signal's backplate did it most obviously, but the same thing
    was quietly happening on the wheelset, the switch stand, the crossing gate and others.

    Rather than hand-tune coordinates in every model and have the next one reintroduce it,
    every model goes through this: wherever two elements draw the same face on the same plane
    AND overlap there, the later element is moved a hair along that axis. 0.02 of a
    block-pixel is far too small to see and is enough to settle the depth test.

    Elements that merely touch are left alone - that's ordinary adjacency.
    Rotated elements are skipped: a rotation moves the plane, so the raw coordinates don't
    tell us whether they really clash, and nudging could make it worse.

    Works on a copy. Every caller currently builds a fresh list, but if one ever passed the
    same list to two models, mutating in place would nudge it twice and the second model would
    come out subtly wrong - the kind of thing nobody would spot.
    """
    elements = copy.deepcopy(elements)
    for _ in range(8):                      # a nudge can expose a new pair; settle by iterating
        moved = False
        for i in range(len(elements)):
            for j in range(i + 1, len(elements)):
                a, b = elements[i], elements[j]
                if "rotation" in a or "rotation" in b:
                    continue
                af, bf = a.get("faces", {}), b.get("faces", {})
                for ax_i, ax in enumerate(_AXES):
                    others = [k for k in range(3) if k != ax_i]
                    if _overlap(a["from"][others[0]], a["to"][others[0]],
                                b["from"][others[0]], b["to"][others[0]]) <= 1e-9:
                        continue
                    if _overlap(a["from"][others[1]], a["to"][others[1]],
                                b["from"][others[1]], b["to"][others[1]]) <= 1e-9:
                        continue
                    for end in (0, 1):
                        key = "from" if end == 0 else "to"
                        if abs(a[key][ax_i] - b[key][ax_i]) > 1e-9:
                            continue
                        side = next((s for s, (sax, se) in _SIDES.items()
                                     if sax == ax and se == end), None)
                        if side is None or side not in af or side not in bf:
                            continue
                        # shrink the later element inward so its face clears the other's
                        b[key][ax_i] += _NUDGE if end == 0 else -_NUDGE
                        moved = True
        if not moved:
            break
    return elements


def model(name, textures, elements, parent="block/block"):
    textures = dict(textures)
    textures.setdefault("particle", next(iter(textures.values())))
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
               {"parent": parent, "textures": textures, "elements": deconflict(elements)})


def blockstate_facing(name, model_for=None, props=None):
    """variants over facing x every combination of extra props; model_for(combo) -> model name"""
    props = props or {}
    keys = list(props.keys())
    variants = {}

    def rec(i, combo):
        if i == len(keys):
            for facing, y in FACINGS.items():
                parts = [f"facing={facing}"] + [f"{k}={combo[k]}" for k in keys]
                m = {"model": f"{NS}:block/" + (model_for(combo) if model_for else name)}
                if y:
                    m["y"] = y
                variants[",".join(sorted(parts))] = m
            return
        for v in props[keys[i]]:
            combo[keys[i]] = v
            rec(i + 1, combo)

    rec(0, {})
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def item(name, model_name=None):
    write_json(os.path.join(ASSETS, "items", name + ".json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:block/" + (model_name or name)}})


def loot(name):
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def recipe(name, pattern, key, count=1):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone",
        "pattern": pattern, "key": key, "result": {"id": f"{NS}:{name}", "count": count}})


LANG = {}
PICKAXE, AXE = [], []


def block(name, english, tool="pickaxe"):
    LANG[f"block.{NS}.{name}"] = english
    loot(name)
    (AXE if tool == "axe" else PICKAXE).append(f"{NS}:{name}")


# ---- individual blocks -------------------------------------------------------------------

def mast_pole(tex_key="pole"):
    return el((7, 0, 7), (9, 16, 9), {"all": tex_key, "up": tex_key, "down": tex_key})


def gen_signal_mast():
    model("signal_mast", {"pole": t("black_paint")}, [
        mast_pole(),
        el((6.5, 0, 6.5), (9.5, 1, 9.5), {"all": "pole"}),
    ])
    blockstate_facing("signal_mast")
    item("signal_mast")
    block("signal_mast", "Signal Mast")
    recipe("signal_mast", ["I", "I", "I"], {"I": "minecraft:iron_bars"}, 4)


def gen_searchlight():
    for aspect, lamp in (("dark", "lamp_dark"), ("red", "lamp_red"), ("yellow", "lamp_yellow"), ("green", "lamp_green")):
        model("searchlight_signal_" + aspect, {"pole": t("black_paint"), "back": t("signal_backplate"),
                                               "lamp": t(lamp), "particle": t("black_paint")}, [
            el((7, 0, 7), (9, 6, 9), {"all": "pole"}),
            # Round-ish backplate, two crossed slabs. They MUST NOT share a z plane: both used
            # to run 6->7 and overlapped, so their north and south faces were coplanar and the
            # renderer could not decide which was in front - that is the flicker. The second
            # slab is inset a hair on both sides so no two faces are ever at the same depth.
            el((3, 7, 6), (13, 15, 7), {"all": "pole", "north": ("back", (0, 0, 16, 16))}),
            el((4, 6, 6.02), (12, 16, 6.98), {"all": "pole", "north": ("back", (2, 0, 14, 16))}),
            # lamp housing behind the plate
            el((5, 8, 7), (11, 14, 11), {"all": "pole"}),
            # lens
            el((5.5, 8.5, 5.5), (10.5, 13.5, 6), {"all": "pole", "north": ("lamp", (0, 0, 16, 16))}, shade=False),
            # hood over the lens
            el((5, 13.5, 3), (11, 14.5, 6), {"all": "pole"}),
            el((5, 8.5, 3), (5.5, 13.5, 6), {"all": "pole"}),
            el((10.5, 8.5, 3), (11, 13.5, 6), {"all": "pole"}),
        ])
    blockstate_facing("searchlight_signal", lambda c: "searchlight_signal_" + c["aspect"],
                      {"aspect": ["dark", "red", "yellow", "green"]})
    item("searchlight_signal", "searchlight_signal_green")
    block("searchlight_signal", "Searchlight Signal")
    recipe("searchlight_signal", [" G ", "RTY", " S "], {
        "G": "minecraft:green_stained_glass", "R": "minecraft:red_stained_glass",
        "Y": "minecraft:yellow_stained_glass", "T": "create:electron_tube", "S": "create:track_signal"})


def gen_cabinet(name, front_off, front_on, english, recipe_key, recipe_pattern):
    for on in (False, True):
        model(name + ("_on" if on else ""), {"side": t("galvanized"), "front": t(front_on if on else front_off),
                                             "top": t("dark_steel")}, [
            el((2, 0, 3), (14, 10, 13), {"all": "side", "north": ("front", (0, 0, 16, 16)), "up": "top"}),
            el((1.5, 10, 2.5), (14.5, 11, 13.5), {"all": "top"}),
        ])
    blockstate_facing(name, lambda c: name + ("_on" if c["powered"] == "true" else ""),
                      {"powered": ["false", "true"]})
    item(name)
    block(name, english)
    recipe(name, recipe_pattern, recipe_key)


def gen_loading_hatch():
    model("loading_hatch", {"side": t("loading_hatch_side"), "top": t("loading_hatch_top"),
                            "bottom": t("dark_steel")}, [
        el((0, 0, 0), (16, 16, 16), {"all": "side", "up": "top", "down": "bottom"}),
    ])
    write_json(os.path.join(ASSETS, "blockstates", "loading_hatch.json"),
               {"variants": {"": {"model": f"{NS}:block/loading_hatch"}}})
    item("loading_hatch")
    block("loading_hatch", "Loading Hatch")
    recipe("loading_hatch", ["IHI", "ICI"], {"I": "create:iron_sheet", "H": "minecraft:hopper",
                                             "C": "create:andesite_casing"})


def gen_defect_detector():
    model("defect_detector", {"side": t("galvanized"), "door": t("detector_door"), "top": t("dark_steel"),
                              "ant": t("black_paint")}, [
        el((3, 0, 4), (13, 9, 12), {"all": "side", "north": ("door", (0, 0, 16, 16)), "up": "top"}),
        el((2.5, 9, 3.5), (13.5, 10, 12.5), {"all": "top"}),
        el((7.5, 10, 7.5), (8.5, 16, 8.5), {"all": "ant"}),
        el((7, 15, 7), (9, 16, 9), {"all": "ant"}),
    ])
    blockstate_facing("defect_detector", props={"powered": ["false", "true"], "enabled": ["false", "true"]})
    item("defect_detector")
    block("defect_detector", "Defect Detector")
    recipe("defect_detector", [" A ", "IOI", "ITI"], {"A": "minecraft:lightning_rod", "I": "create:iron_sheet",
                                                       "O": "create:track_observer", "T": "create:electron_tube"})


def gen_speed_sign():
    labels = ["10", "15", "20", "25", "30", "40", "50", "60", "r"]
    for i, lab in enumerate(labels):
        model(f"speed_sign_{lab}", {"post": t("galvanized"), "plate": t("speed_" + lab), "back": t("galvanized")}, [
            el((7, 0, 7), (9, 9, 9), {"all": "post"}),
            el((3, 8, 6), (13, 16, 7), {"all": "back", "north": ("plate", (0, 0, 16, 16))}),
        ])
    blockstate_facing("speed_sign", lambda c: "speed_sign_" + labels[int(c["limit"])],
                      {"limit": [str(i) for i in range(9)]})
    item("speed_sign", "speed_sign_30")
    block("speed_sign", "Speed Limit Sign")
    recipe("speed_sign", ["W", "S", "S"], {"W": "minecraft:white_concrete", "S": "create:iron_sheet"}, 2)


def gen_switch_stand():
    def stand(target_tex, turned):
        disc_a = ((4, 11, 7.5), (12, 15, 8.5)) if not turned else ((7.5, 11, 4), (8.5, 15, 12))
        disc_b = ((5, 10, 7.5), (11, 16, 8.5)) if not turned else ((7.5, 10, 5), (8.5, 16, 11))
        return [
            el((5, 0, 5), (11, 3, 11), {"all": "base"}),
            el((7.5, 3, 7.5), (8.5, 10, 8.5), {"all": "base"}),
            el((5.5, 4, 7.6), (7.5, 5, 8.4), {"all": "base"}),  # throw lever
            el(disc_a[0], disc_a[1], {"all": "target"}),
            el(disc_b[0], disc_b[1], {"all": "target"}),
        ]
    model("switch_stand_none", {"base": t("black_paint"), "target": t("target_gray")}, stand("target_gray", False))
    model("switch_stand_normal", {"base": t("black_paint"), "target": t("target_green")}, stand("target_green", False))
    model("switch_stand_reverse", {"base": t("black_paint"), "target": t("target_red")}, stand("target_red", True))
    blockstate_facing("switch_stand", lambda c: "switch_stand_" + c["target"],
                      {"target": ["none", "normal", "reverse"]})
    item("switch_stand", "switch_stand_normal")
    block("switch_stand", "Switch Stand")
    recipe("switch_stand", ["GR", "IL", "II"], {"G": "minecraft:green_dye", "R": "minecraft:red_dye",
                                                "I": "minecraft:iron_ingot", "L": "minecraft:lever"})


def gen_interlocking():
    els = [
        el((0, 0, 4), (16, 14, 16), {"all": "side", "north": ("front", (0, 2, 16, 16))}),
        el((-0.25, 14, 3.5), (16.25, 15, 16.25), {"all": "side"}),
    ]
    # levers: column c (left to right as seen from the front) sits at x = 14 - 4c in model space
    for c in range(4):
        x = 14 - 4 * c
        els.append(el((x - 0.75, 8.5, 1.5), (x + 0.75, 10, 4), {"all": "lever", "north": "tip"}))
        els.append(el((x - 0.75, 1.5, 1.5), (x + 0.75, 3, 4), {"all": "lever", "north": "tip"}))
    model("interlocking_panel", {"side": t("maroon_paint"), "front": t("interlocking_front"),
                                 "lever": t("black_paint"), "tip": t("white_paint")}, els)
    blockstate_facing("interlocking_panel")
    item("interlocking_panel")
    block("interlocking_panel", "Interlocking Panel")
    recipe("interlocking_panel", ["LLL", "PCP", "LTL"], {"L": "minecraft:lever", "P": "create:precision_mechanism",
                                                         "C": "create:railway_casing", "T": "create:transmitter"})


def gen_crossing_signal():
    def lights(left_on, right_on):
        # heads sit either side of the mast on a cross bar; seen from the front, left = +x
        L = "lamp_red" if left_on else "lamp_red_off"
        R = "lamp_red" if right_on else "lamp_red_off"
        return {"pole": t("white_paint"), "black": t("black_paint"), "boards": t("crossbuck_boards"),
                "left": t(L), "right": t(R), "back": t("signal_backplate"), "particle": t("white_paint")}

    def els():
        e = [
            el((7, 0, 7), (9, 16, 9), {"all": "pole"}),
            el((6, 0, 6), (10, 1, 10), {"all": "black"}),
            # cross bar
            el((1, 7, 7.5), (15, 8, 8.5), {"all": "black"}),
            # left head (viewer's left = +x)
            el((11, 5, 6), (15, 10, 7), {"all": "black", "north": ("back", (0, 0, 16, 16))}),
            el((11.5, 5.5, 5.5), (14.5, 8.5, 6), {"all": "black", "north": ("left", (0, 0, 16, 16))}, shade=False),
            el((11.5, 8.5, 4.5), (14.5, 9, 6), {"all": "black"}),
            # right head
            el((1, 5, 6), (5, 10, 7), {"all": "black", "north": ("back", (0, 0, 16, 16))}),
            el((1.5, 5.5, 5.5), (4.5, 8.5, 6), {"all": "black", "north": ("right", (0, 0, 16, 16))}, shade=False),
            el((1.5, 8.5, 4.5), (4.5, 9, 6), {"all": "black"}),
            # crossbuck boards at +-45 degrees
            el((0, 12.75, 6.5), (16, 14.25, 7),
               {"all": "pole", "north": ("boards", (0, 0, 16, 4)), "south": ("boards", (0, 0, 16, 4))},
               {"origin": [8, 13.5, 6.75], "axis": "z", "angle": 45}),
            el((0, 12.75, 6.4), (16, 14.25, 6.5),
               {"all": "pole", "north": ("boards", (0, 0, 16, 4)), "south": ("boards", (0, 0, 16, 4))},
               {"origin": [8, 13.5, 6.45], "axis": "z", "angle": -45}),
        ]
        return e

    for name, l, r in (("off", False, False), ("left", True, False), ("right", False, True)):
        model("crossing_signal_" + name, lights(l, r), els())
    blockstate_facing("crossing_signal", lambda c: "crossing_signal_" + c["flash"],
                      {"flash": ["off", "left", "right"], "train": ["false", "true"]})
    item("crossing_signal", "crossing_signal_left")
    block("crossing_signal", "Crossing Signal")
    recipe("crossing_signal", ["RWR", " B ", " I "], {"R": "minecraft:redstone_lamp", "W": "minecraft:white_concrete",
                                                      "B": "minecraft:bell", "I": "create:iron_sheet"})


def gen_crossing_gate():
    def els(angle, lit):
        lamp = "lamp_on" if lit else "lamp_off"
        e = [
            el((6.5, 0, 6.5), (9.5, 16, 9.5), {"all": "pole"}),
            el((5, 0, 5), (11, 2.5, 11), {"all": "black"}),
            # mechanism box
            el((5.5, 7, 5.5), (10.5, 11, 10.5), {"all": "black"}),
            # counterweight behind the pivot (opposite side to the arm)
            el((1, 8, 7), (6.5, 10, 9), {"all": "black"}),
        ]
        # arm: pivot at (8, 9, 8); goes out to +x (viewer's left from the front)
        if angle == 2:
            arm = [el((10.5, 8.5, 7.25), (32, 9.75, 8.75), {"all": "arm", "up": "arm", "down": "arm"}),
                   el((17, 9.75, 7.5), (18.5, 11, 8.5), {"all": "black", "north": lamp, "south": lamp}, shade=False),
                   el((26, 9.75, 7.5), (27.5, 11, 8.5), {"all": "black", "north": lamp, "south": lamp}, shade=False),
                   el((30.5, 9.75, 7.5), (32, 11, 8.5), {"all": "black", "north": lamp, "south": lamp}, shade=False)]
        elif angle == 1:
            arm = [el((10.5, 8.5, 7.25), (32, 9.75, 8.75), {"all": "arm"},
                      {"origin": [8, 9, 8], "axis": "z", "angle": 45})]
        else:
            arm = [el((7.25, 11, 7.25), (8.75, 32, 8.75), {"all": "arm"})]
        return e + arm

    for angle in (0, 1, 2):
        for lit in (False, True):
            if angle != 2 and lit:
                continue
            name = f"crossing_gate_{angle}" + ("_lit" if lit else "")
            model(name, {"pole": t("white_paint"), "black": t("black_paint"), "arm": t("gate_arm"),
                         "lamp_on": t("lamp_red"), "lamp_off": t("lamp_red_off"), "particle": t("white_paint")},
                  els(angle, lit))
    blockstate_facing("crossing_gate",
                      lambda c: f"crossing_gate_{c['angle']}" + ("_lit" if c["angle"] == "2" and c["lit"] == "true" else ""),
                      {"angle": ["0", "1", "2"], "lit": ["false", "true"]})
    item("crossing_gate", "crossing_gate_0")
    block("crossing_gate", "Crossing Gate")
    recipe("crossing_gate", ["RWW", "IP ", "IC "], {"R": "minecraft:redstone_lamp", "W": "minecraft:white_wool",
                                                    "I": "create:iron_sheet", "P": "minecraft:piston",
                                                    "C": "create:andesite_casing"})


def gen_new_tools():
    # car retarder: two long yellow brake beams either side of a flange way
    model("car_retarder", {"y": t("hazard_stripes"), "steel": t("dark_steel"), "beam": t("yellow_paint")}, [
        el((0, 0, 4), (16, 1.5, 12), {"all": "steel"}),
        el((0, 1.5, 4.5), (16, 4, 6), {"all": "beam", "north": "y"}),
        el((0, 1.5, 10), (16, 4, 11.5), {"all": "beam", "south": "y"}),
        el((2, 1.5, 7), (4, 3, 9), {"all": "steel"}),
        el((12, 1.5, 7), (14, 3, 9), {"all": "steel"}),
    ])
    blockstate_facing("car_retarder")
    item("car_retarder")
    block("car_retarder", "Car Retarder")
    recipe("car_retarder", ["YPY", "III"], {"Y": "minecraft:yellow_dye", "P": "minecraft:piston", "I": "create:iron_sheet"})

    # switch machine: low motor box with a throw rod sticking out the front
    model("switch_machine", {"side": t("galvanized"), "lid": t("switch_machine_lid"), "rod": t("dark_steel")}, [
        el((2, 0, 3), (14, 7, 13), {"all": "side", "up": "lid"}),
        el((1.5, 7, 2.5), (14.5, 8, 13.5), {"all": "side", "up": "lid"}),
        el((7, 2, 0), (9, 4, 3), {"all": "rod"}),
    ])
    blockstate_facing("switch_machine")
    item("switch_machine")
    block("switch_machine", "Switch Machine")
    recipe("switch_machine", [" R ", "ISI", "ICI"], {"R": "minecraft:redstone", "I": "create:iron_sheet",
                                                     "S": "minecraft:sticky_piston", "C": "create:andesite_casing"})

    # route sensor: small yellow box with a sensor eye, on a stub
    model("route_sensor", {"box": t("yellow_paint"), "face": t("route_sensor_face"), "steel": t("dark_steel")}, [
        el((4, 0, 4), (12, 5, 12), {"all": "box", "north": ("face", (0, 0, 16, 16)), "up": "face"}),
        el((6, 5, 6), (10, 7, 10), {"all": "steel"}),
    ])
    blockstate_facing("route_sensor")
    item("route_sensor")
    block("route_sensor", "Route Sensor")
    recipe("route_sensor", ["O", "T", "C"], {"O": "create:track_observer", "T": "create:electron_tube",
                                             "C": "create:andesite_casing"})

    # switch linker item
    write_json(os.path.join(ASSETS, "models", "item", "switch_linker.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/switch_linker"}})
    write_json(os.path.join(ASSETS, "items", "switch_linker.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/switch_linker"}})
    LANG[f"item.{NS}.switch_linker"] = "Switch Linker"
    write_json(os.path.join(DATA, "recipe", "switch_linker.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone",
        "pattern": [" GR", " I ", "B  "],
        "key": {"G": "minecraft:green_dye", "R": "minecraft:red_dye", "I": "minecraft:iron_ingot",
                "B": "create:brass_sheet"},
        "result": {"id": f"{NS}:switch_linker", "count": 1}})


def gen_car_retarder():
    model("car_retarder", {"top": t("retarder_top"), "side": t("retarder_side")}, [
        el((0, 0, 4), (16, 3, 12), {"all": "side", "up": "top"}),
        el((0, 3, 4.5), (16, 4, 6), {"all": "side", "up": "top"}),
        el((0, 3, 10), (16, 4, 11.5), {"all": "side", "up": "top"}),
    ])
    blockstate_facing("car_retarder", props={"setting": ["0", "1", "2", "3"], "powered": ["false", "true"]})
    item("car_retarder")
    block("car_retarder", "Car Retarder")
    recipe("car_retarder", ["YYY", "III", "CCC"], {"Y": "minecraft:yellow_dye", "I": "create:iron_sheet",
                                                   "C": "create:andesite_casing"})


def gen_switch_machine():
    model("switch_machine", {"side": t("galvanized"), "front": t("switch_machine_front"), "top": t("dark_steel")}, [
        el((2, 0, 3), (14, 7, 13), {"all": "side", "north": ("front", (0, 0, 16, 16)), "up": "top"}),
        el((1.5, 7, 2.5), (14.5, 8, 13.5), {"all": "top"}),
        el((6, 2, 1), (10, 4, 3), {"all": "top"}),
    ])
    blockstate_facing("switch_machine", props={"route": ["0", "1", "2"],
                                               "input": [str(i) for i in range(16)]})
    item("switch_machine")
    block("switch_machine", "Switch Machine")
    recipe("switch_machine", ["IPI", "RCR"], {"I": "create:iron_sheet", "P": "minecraft:piston",
                                              "R": "minecraft:redstone", "C": "create:andesite_casing"})


def gen_route_sensor():
    model("route_sensor", {"top": t("route_sensor_top"), "side": t("dark_steel")}, [
        el((4, 0, 4), (12, 4, 12), {"all": "side", "up": "top"}),
        el((6, 4, 6), (10, 6, 10), {"all": "side", "up": "top"}),
    ])
    blockstate_facing("route_sensor", props={"mode": ["0", "1", "2"]})
    item("route_sensor")
    block("route_sensor", "Route Sensor")
    recipe("route_sensor", [" O ", "ITI", " C "], {"O": "create:track_observer", "I": "create:iron_sheet",
                                                   "T": "create:electron_tube", "C": "create:andesite_casing"})


def gen_switch_linker():
    write_json(os.path.join(ASSETS, "models", "item", "switch_linker.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/switch_linker"}})
    write_json(os.path.join(ASSETS, "items", "switch_linker.json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:item/switch_linker"}})
    LANG[f"item.{NS}.switch_linker"] = "Switch Linker"
    write_json(os.path.join(DATA, "recipe", "switch_linker.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone",
        "pattern": ["  E", " B ", "B  "],
        "key": {"E": "create:electron_tube", "B": "create:brass_sheet"},
        "result": {"id": f"{NS}:switch_linker", "count": 1}})


def gen_decor():
    # mile post
    model("mile_post", {"post": t("concrete"), "face": t("mile_post_face")}, [
        el((6, 0, 6), (10, 15, 10), {"all": "post", "north": ("face", (6, 1, 10, 16)),
                                     "south": ("face", (6, 1, 10, 16))}),
    ])
    # whistle post
    model("whistle_post", {"post": t("galvanized"), "face": t("whistle_post_face"), "back": t("galvanized")}, [
        el((7, 0, 7), (9, 9, 9), {"all": "post"}),
        el((4, 7, 7), (12, 15, 8), {"all": "back", "north": ("face", (0, 0, 16, 16))}),
    ])
    # yard limit
    model("yard_limit_sign", {"post": t("galvanized"), "face": t("yard_limit_face"), "back": t("galvanized")}, [
        el((2, 0, 7.5), (3, 8, 8.5), {"all": "post"}),
        el((13, 0, 7.5), (14, 8, 8.5), {"all": "post"}),
        el((0, 8, 7), (16, 15, 8), {"all": "back", "north": ("face", (0, 0, 16, 7))}),
    ])
    # crossbuck only
    model("crossbuck", {"pole": t("white_paint"), "boards": t("crossbuck_boards"), "black": t("black_paint")}, [
        el((7, 0, 7), (9, 16, 9), {"all": "pole"}),
        el((0, 10.75, 6.5), (16, 12.25, 7),
           {"all": "pole", "north": ("boards", (0, 0, 16, 4)), "south": ("boards", (0, 0, 16, 4))},
           {"origin": [8, 11.5, 6.75], "axis": "z", "angle": 45}),
        el((0, 10.75, 6.4), (16, 12.25, 6.5),
           {"all": "pole", "north": ("boards", (0, 0, 16, 4)), "south": ("boards", (0, 0, 16, 4))},
           {"origin": [8, 11.5, 6.45], "axis": "z", "angle": -45}),
    ])
    # relay case / bungalow
    model("relay_case", {"side": t("aluminum"), "door": t("relay_case_door"), "roof": t("galvanized")}, [
        el((2, 0, 3), (14, 14, 13), {"all": "side", "north": ("door", (0, 0, 16, 16))}),
        el((1.5, 14, 2.5), (14.5, 15, 13.5), {"all": "roof"}),
    ])
    model("battery_box", {"side": t("galvanized"), "lid": t("battery_box_lid")}, [
        el((1, 0, 4), (15, 6, 12), {"all": "side"}),
        el((0.5, 6, 3.5), (15.5, 7, 12.5), {"all": "side", "up": "lid"}),
    ])
    model("phone_box", {"post": t("galvanized"), "box": t("yellow_paint"), "door": t("phone_box_door")}, [
        el((7, 0, 7), (9, 8, 9), {"all": "post"}),
        el((4, 8, 6), (12, 15, 10), {"all": "box", "north": ("door", (0, 0, 16, 16))}),
        el((3.5, 15, 5.5), (12.5, 16, 10.5), {"all": "box"}),
    ])
    model("buffer_stop", {"steel": t("dark_steel"), "face": t("buffer_face"), "pad": t("galvanized")}, [
        el((2, 0, 4), (4, 7, 14), {"all": "steel"}),
        el((12, 0, 4), (14, 7, 14), {"all": "steel"}),
        el((1, 7, 4), (15, 12, 7), {"all": "steel", "north": ("face", (0, 0, 16, 16))}),
        el((3, 8.5, 1), (6, 10.5, 4), {"all": "steel", "north": "pad"}),
        el((10, 8.5, 1), (13, 10.5, 4), {"all": "steel", "north": "pad"}),
        el((7, 0, 7), (9, 7.5, 14), {"all": "steel"}),
    ])
    model("wheel_stop", {"y": t("hazard_stripes"), "steel": t("dark_steel")}, [
        el((2, 0, 5), (14, 3, 11), {"all": "y"}),
        el((3, 3, 6), (13, 4, 10), {"all": "steel"}),
    ])
    model("derail", {"y": t("yellow_paint"), "steel": t("dark_steel")}, [
        el((3, 0, 6), (13, 2, 10), {"all": "y"}),
        el((4, 2, 7), (12, 3, 9), {"all": "y"}),
        el((12, 0.5, 7.5), (15, 1.5, 8.5), {"all": "steel"}),
    ])
    ties = []
    for layer in range(3):
        y = layer * 3
        for z in (1.5, 5.25, 9, 12.75):
            ties.append(el((0, y, z), (16, y + 2.5, z + 3), {"all": "wood", "east": "end", "west": "end"}))
    model("tie_pile", {"wood": t("creosote"), "end": t("creosote_end")}, ties)
    rails = [el((2, 0, 2), (4, 2, 14), {"all": "wood"}), el((12, 0, 2), (14, 2, 14), {"all": "wood"})]
    for z in (3, 6, 9, 12):
        rails.append(el((0, 2, z - 1), (16, 2.5, z + 1), {"all": "steel"}))
        rails.append(el((0, 2.5, z - 0.3), (16, 4, z + 0.3), {"all": "steel"}))
        rails.append(el((0, 4, z - 0.6), (16, 5, z + 0.6), {"all": "rail"}))
    model("rail_rack", {"wood": t("creosote"), "steel": t("dark_steel"), "rail": t("rail_steel")}, rails)
    model("spike_keg", {"side": t("keg_side"), "top": t("keg_top")}, [
        el((4, 0, 4), (12, 11, 12), {"all": "side", "up": "top", "down": "top"}),
        el((3.75, 1.5, 3.75), (12.25, 2.5, 12.25), {"all": "hoop"}),
        el((3.75, 8.5, 3.75), (12.25, 9.5, 12.25), {"all": "hoop"}),
    ])
    with open(os.path.join(ASSETS, "models", "block", "spike_keg.json")) as f:
        sk = json.load(f)
    sk["textures"]["hoop"] = t("dark_steel")
    write_json(os.path.join(ASSETS, "models", "block", "spike_keg.json"), sk)

    wheel = []
    for x1, x2 in ((1, 3), (13, 15)):
        wheel.append(el((x1, 0, 5), (x2, 10, 11), {"all": "steel", "east": "face", "west": "face"}))
        wheel.append(el((x1, 2, 3), (x2, 8, 13), {"all": "steel", "east": "face", "west": "face"}))
        wheel.append(el((x1, 1, 4), (x2, 9, 12), {"all": "steel", "east": "face", "west": "face"}))
    wheel.append(el((3, 4.5, 7.5), (13, 5.5, 8.5), {"all": "axle"}))
    model("wheelset", {"steel": t("rust"), "face": t("wheel_face"), "axle": t("dark_steel")}, wheel)

    model("yard_light", {"pole": t("galvanized"), "head": t("dark_steel"), "lens": t("yard_light_lens")}, [
        el((7, 0, 7), (9, 16, 9), {"all": "pole"}),
        el((7.5, 14, 4), (8.5, 15, 7), {"all": "pole"}),
        el((6, 13, 2), (10, 15, 6), {"all": "head", "down": "lens"}, shade=False),
    ])
    model("telegraph_pole", {"pole": t("pole_wood"), "arm": t("pole_wood"), "glass": t("glass_insulator")}, [
        el((6.5, 0, 6.5), (9.5, 16, 9.5), {"all": "pole"}),
        el((0, 12, 7), (16, 14, 9), {"all": "arm"}),
        el((1.5, 14, 7.5), (2.5, 15.5, 8.5), {"all": "glass"}),
        el((4.5, 14, 7.5), (5.5, 15.5, 8.5), {"all": "glass"}),
        el((10.5, 14, 7.5), (11.5, 15.5, 8.5), {"all": "glass"}),
        el((13.5, 14, 7.5), (14.5, 15.5, 8.5), {"all": "glass"}),
    ])

    # Signal bridge: an overhead truss that spans the tracks. Place them in a row to make a
    # span as wide as you need, stand it on Signal Masts, and hang Searchlight Signals under
    # it - a head over a far track reads its signal through a Switch Linker link.
    model("signal_bridge", {"steel": t("dark_steel"), "deck": t("galvanized")}, [
        el((0, 13, 5), (16, 15, 11), {"all": "deck"}),            # walkway / top chord
        el((0, 8, 6), (16, 9.5, 10), {"all": "steel"}),           # bottom chord
        el((1, 9.5, 7), (3, 13, 9), {"all": "steel"}),            # web posts
        el((7, 9.5, 7), (9, 13, 9), {"all": "steel"}),
        el((13, 9.5, 7), (15, 13, 9), {"all": "steel"}),
        el((0, 15, 5.5), (16, 16, 6), {"all": "steel"}),          # handrails
        el((0, 15, 10), (16, 16, 10.5), {"all": "steel"}),
    ])

    decor = {
        "signal_bridge": ("Signal Bridge", "pickaxe", ["III", "B B"], {"I": "create:iron_sheet", "B": "minecraft:iron_bars"}, 3),
        "mile_post": ("Mile Post", "pickaxe", ["C", "C"], {"C": "minecraft:white_concrete"}, 2),
        "whistle_post": ("Whistle Post", "pickaxe", ["W", "I"], {"W": "minecraft:white_concrete", "I": "create:iron_sheet"}, 2),
        "yard_limit_sign": ("Yard Limit Sign", "pickaxe", ["WWW", "I I"], {"W": "minecraft:white_concrete", "I": "create:iron_sheet"}, 2),
        "crossbuck": ("Crossbuck", "pickaxe", ["W W", " W ", " I "], {"W": "minecraft:white_concrete", "I": "create:iron_sheet"}, 2),
        "relay_case": ("Relay Case", "pickaxe", ["III", "IRI", "III"], {"I": "create:iron_sheet", "R": "minecraft:redstone"}, 1),
        "battery_box": ("Battery Box", "pickaxe", ["I I", "ICI"], {"I": "create:iron_sheet", "C": "minecraft:copper_ingot"}, 1),
        "phone_box": ("Railroad Phone Box", "pickaxe", ["Y", "I"], {"Y": "minecraft:yellow_concrete", "I": "create:iron_sheet"}, 1),
        "buffer_stop": ("Buffer Stop", "pickaxe", ["RRR", "I I"], {"R": "minecraft:red_concrete", "I": "minecraft:iron_ingot"}, 1),
        "wheel_stop": ("Wheel Stop", "pickaxe", ["YIY"], {"Y": "minecraft:yellow_dye", "I": "minecraft:iron_ingot"}, 2),
        "derail": ("Derail", "pickaxe", ["Y", "I"], {"Y": "minecraft:yellow_dye", "I": "minecraft:iron_ingot"}, 1),
        "tie_pile": ("Tie Pile", "axe", ["SSS", "SSS"], {"S": "minecraft:dark_oak_slab"}, 2),
        "rail_rack": ("Rail Rack", "pickaxe", ["RRR", "W W"], {"R": "minecraft:rail", "W": "minecraft:dark_oak_slab"}, 1),
        "spike_keg": ("Spike Keg", "axe", ["N", "B"], {"N": "minecraft:iron_nugget", "B": "minecraft:barrel"}, 1),
        "wheelset": ("Wheelset", "pickaxe", ["I I", "III"], {"I": "minecraft:iron_ingot"}, 1),
        "yard_light": ("Yard Light", "pickaxe", ["IL", "I "], {"I": "minecraft:iron_bars", "L": "minecraft:sea_lantern"}, 1),
        "telegraph_pole": ("Telegraph Pole", "axe", ["SSS", " L ", " L "], {"S": "minecraft:stick", "L": "minecraft:stripped_spruce_log"}, 3),
    }
    for name, (english, tool, pattern, key, count) in decor.items():
        blockstate_facing(name)
        item(name)
        block(name, english, tool)
        recipe(name, pattern, key, count)


def main():
    make_textures()
    gen_signal_mast()
    gen_searchlight()
    gen_cabinet("cargo_sensor", "cargo_sensor_front", "cargo_sensor_front_on", "Cargo Sensor",
                {"C": "create:andesite_casing", "T": "create:electron_tube", "Q": "minecraft:comparator"},
                ["T", "Q", "C"])
    gen_cabinet("car_counter", "car_counter_front", "car_counter_front_on", "Car Counter",
                {"C": "create:andesite_casing", "T": "create:electron_tube", "O": "minecraft:observer"},
                ["T", "O", "C"])
    gen_loading_hatch()
    gen_defect_detector()
    gen_speed_sign()
    gen_switch_stand()
    gen_interlocking()
    gen_crossing_signal()
    gen_crossing_gate()
    gen_new_tools()
    gen_decor()

    for tag, values in (("pickaxe", PICKAXE), ("axe", AXE)):
        write_json(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", tag + ".json"),
                   {"replace": False, "values": sorted(values)})

    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path) as f:
        lang = json.load(f)
    lang.update(LANG)
    write_json(lang_path, lang)
    print(f"{len(TEX)} textures, {len(LANG)} blocks")


if __name__ == "__main__":
    main()

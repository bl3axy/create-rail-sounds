# Create Rail Sounds

A railroad mod for Create. It does two things: it makes trains actually sound like trains, and it
gives you the trackside hardware to run a real railroad — signals, crossings, switch control,
cargo sensors, and the yard gear you need to do proper shunting.

Built for **Minecraft 26.2 (Fabric)** against **Create Fly 6.0.9**. Some of the shunting features
want **Steam 'n' Rails** installed as well; everything else works without it.

**You don't need the mod on your client to hear the sounds.** They come from the server resource
pack. Install the mod on your client too and you get the keybinds, the long seamless horn hold,
and the proper fade when you let go of the horn.

---

## What's in the box

- `create-rail-sounds-2.4.0.jar` — the mod. Drop it in `mods/` on the server.
- `railsounds-resourcepack.zip` — the sounds. Put it in `resourcepacks/` on the server and set
  `resource-pack=` in `server.properties`, or just hand it to people to install themselves.
- This README.

---

## Sounds

Everything is driven off the train's real speed, recalculated every tick. Nothing is on a timer
pretending to be a train.

| Sound | When you hear it |
|---|---|
| **Rail joint clack** | every `clackSpacing` blocks under each car, with a second hit when the rear bogey crosses the same joint. Volume and pitch follow speed |
| **Brake squeal** | only in the last part of a genuine stop — under `brakeStopSpeed` and slowing hard enough to actually stop within `brakeMaxTicksToStop`. Once per stop, cut off the moment the train stops. Louder the harder you brake, pitch rises as it slows |
| **Coupler slack** | clunks at every coupling, rippling front to back as you pull away and back to front as you stop |
| **Air release** | each car hisses off right after the train comes to a stand |
| **Engine idle** | a running diesel ticks over while it's standing, like the real thing |
| **Engine running** | the engine note while moving, notched to speed (see below) |
| **Notch up** | the engine digging in as it pulls away or takes another notch |
| **Startup** | one-shot when you light a diesel — 7.5s of the real thing catching and settling, levelled so it fades into the idle with no step in volume |
| **Loco bell** | the slow clang on the engine, on the **B** key |
| **Crossing bell** | rings while a train is heading toward a crossing inside its radius, or any car is sitting on it. Stops about 2s after the last car clears |
| **Horn / whistle** | `/railsounds diesel` and `/railsounds steam` set which one a train uses |

### How the engine note works

Here's the bit worth understanding, because it's not what you'd guess.

A diesel-electric doesn't rev smoothly like a car. It sits at one fixed engine speed per notch and
steps between them — notch 4 is a specific RPM, not "somewhere between 3 and 5". So that's how
this works: the running loop plays at one of **8 playback pitches**, picked from how much of
Create's top speed the train is doing. Speed up, it takes the next notch up, and you hear the
notch-up layer as it does.

**Steam locos get no engine audio** — no idle, no chuffs, no startup. There are no steam engine
clips in the pack, so rather than ask clients for files that aren't there and fill their logs, steam
is skipped outright. They still get the whistle, the clacks, the brakes, the couplers and
everything else.

This is also why the recorded clip doesn't need a huge rev range baked into it. A real diesel
rev-up only swings the fundamental around 10–15% anyway; the notch spread comes from playback
pitch, and `enginePitchMin` / `enginePitchMax` control how wide it is.

One deliberate limitation: a new notch is picked up **when the current loop comes round**, not the
instant your speed changes. Minecraft can only stop a sound by name, which would cut every other
loco within earshot, so restarting mid-clip isn't an option. It works out fine — a real engine
takes a few seconds to settle into a notch anyway, and the notch-up layer is the immediate cue.

### Turning an engine off

Engines are **running by default**, which is what you want for everything that already exists on
your railroad. Sit in a loco and press **K** to shut it down or start it up again. A shut-down loco
makes no engine noise at all; starting it plays the startup and fades into the idle.

That state is saved per train in the world folder, so a loco you shut down tonight is still shut
down tomorrow.

If the idling at your platforms gets on your nerves, `idleWhenStopped: false` in the config kills
it everywhere without you having to shut down each loco individually.

### About the sound files

The diesel idle, running loop, notch-up and startup are all built from real engine recordings.
The **loco bell is a synthesised placeholder** — right length, loops cleanly, but it's a stand-in.
There are no steam engine sounds at all (see above). If you record better ones, drop them straight
in: match the length to the config value
(`idleClipTicks`, `runClipTicks`, `startClipTicks`, `locoBellClipTicks` — 20 ticks per second) and
nothing else needs changing.

Every sound the mod plays is checked in CI against `sounds.json` and the actual files, so a
missing one can't silently do nothing.

---

## Keybinds

Rebindable under **Controls → Train Shunting**.

| Key | Does |
|---|---|
| **C** | couple to the cut you're touching, or within `couplerReach`. On foot, the joint you're looking at |
| **Z + 1-9** | uncouple at coupling N, counted from the car you're riding. Holding Z stops the number keys switching hotbar slots |
| **Z** (tap, on foot) | uncouple the coupling you're looking at |
| **J** | toggle auto-couple for trains you drive |
| **K** | start / shut down the engine of the train you're in |
| **B** | ring the bell on the train you're in, and ring it off again |

On **Z + 1-9** going up to 9 and not further: that's 9 *couplings*, which is a 10-car train. Past
that, uncouple from a car further down the cut, or use a yard rule or a schedule condition.

---

## Shunting

Needs Steam 'n' Rails. No coupler blocks to place — it works off the keys above.

- **Buffers** — a train touching another at or under `bufferMaxSpeed` (10 b/s) stops against it
  instead of exploding. Faster than that and you get Create's normal crash.
- **Auto-couple** — ease into a cut of cars at or under `coupleMaxSpeed` (3 b/s) and it couples,
  like knuckle couplers. Two powered trains never auto-couple; use **C** or a yard rule.
- **Either end** — couple at the front or back, pushing or pulling. Cars facing *opposite* ways
  (front to front) can't be joined, because Create stores each car's direction inside it.
- **Flying switches** — `uncoupleMaxSpeed` is 0.75 (15 b/s), so you can pull the pin at speed and
  let the car roll. Loose cars with no controls coast about 16× further than Create's default
  (`cutCoastFactor`), which is what makes kicking cars into a yard work.
- **Collision safety net** (`collisionAvoidance`) — every moving train looks along the track it's
  actually taking, following its route or how the switches are set, and brakes for anything in the
  way: ahead, round curves, fouling a junction. Following trains hold their distance. Parked cars
  end-on are still approached at coupling speed. **Keep your signals** — two trains meeting
  head-on just both stop and sit there.
- **Ease-in** (`easeIntoCuts`) — trains closing on parked cars slow themselves so they arrive slow
  enough to couple.
- **Back into stations** (`reverseIntoStations`) — reversing a single-cab loco, Space finds the
  station behind you and backs onto the platform. Create normally only allows that with controls
  at both ends.
- **Names** — the train you're driving keeps its name, then a scheduled train, then the loco.
  Diesel/steam marks and schedules carry over.

### Schedule entries

In Create's own schedule editor, per train:

- Instruction **Back into Station** — Travel to Station, but driven in reverse, single-cab locos
  included. Put a forward stop at the lead or wye just before it.
- Condition **Couple up** — couple to whatever's touching either end, then carry on.
- Condition **Uncouple cars** (count, 0 = all) — drop cars off the end away from the loco, then
  carry on.

---

## Signals, crossings and switch control

### Searchlight Signal + Signal Mast

A proper 3-aspect signal: **green** clear, **yellow** approach (the next block is occupied),
**red** stop. It reads the track ahead itself — no wiring needed — and you can still drive it with
redstone if you'd rather. Stack a Signal Mast under it for height.

### Crossing Signal + Crossing Gate + Crossbuck

Place a **Crossing Signal** and it registers a crossing point right there with a **35-block
radius**, automatically. **Sneak-right-click** it to cycle the radius if 35 isn't right for the
speed on that line.

The signal flashes and the bell rings while a train is approaching inside the radius or standing
on the crossing. **Crossing Gates** drop and lift with it. Both emit redstone while active, so you
can wire in your own lamps, more gates, or anything else.

### Switch Stand, Switch Machine and the Switch Linker

Switch control was the fiddliest part of running a layout, so:

- **Switch Stand** — the hand-throw lever by the track. Right-click to throw the switch next to it.
- **Switch Machine** — a powered switch. Redstone throws it, so a lever, a button, a timer or an
  Interlocking Panel can run it.
- **Switch Linker** (item) — right-click a switch, then right-click a Switch Machine or
  Interlocking Panel, and they're linked. That's how you get a switch on the far side of the yard
  onto a lever in your tower.
- **Interlocking Panel** — the signal tower. Levers on the panel throw linked switches as a group,
  so one pull sets a whole route instead of you running around throwing six switches.
- **Route Sensor** — fires redstone when a train takes a particular leg of a junction, so your
  tower can react to where trains actually went.

### Cargo Sensor and Car Counter

- **Cargo Sensor** — put it beside a station and it reads how full the docked train is. Comparator
  output **0–15**, plus plain redstone once it passes a threshold. **Sneak-right-click** to cycle
  the threshold (25 / 50 / 75 / 90 / 100%) and again to switch between **items and fluids**. This
  is what you want for "load until full, then go".
- **Car Counter** — counts the cars in the docked train, also on a comparator.

Both of them feed a **Display Link**, so you can put the number on a sign or a nixie tube display.

### Loading Hatch

A block you put *on the train*. It accepts items while the train is moving, so you can load a
train from a hopper or a belt without docking it at a station.

### The rest of the trackside gear

**Defect Detector** (redstone when a train passes, like a real hotbox detector), **Speed Limit
Sign** (sets a speed zone trains obey), **Yard Limit Sign**, **Car Retarder**, **Derail**, **Buffer
Stop**, **Wheel Stop**, **Battery Box**, **Relay Case**, **Telegraph Pole**, **Yard Light**,
**Railroad Phone Box**, **Mile Post**, **Whistle Post**, **Rail Rack**, **Tie Pile**, **Wheelset**,
**Spike Keg**.

The last lot are mostly scenery, and they're there because a railroad that's just track looks
wrong. None of them have block entities, which is deliberate — see Compatibility.

---

## Station displays

Put a Display Link on a Train Station:

- **Departure Board** — one train per row: departure clock time (in-game), train, destination,
  platform. The next train gets a "via" row listing its next stops. Filter with `*` for platforms
  the way Create does, e.g. `Spruce Town *`.
- **Platform Display (next train)** — the next train in detail (time, countdown, destination, via
  stops) plus the one after. Works on boards, signs, lecterns and nixie tubes.

---

## Commands

All op-level.

### Crossings
| Command | Does |
|---|---|
| `/railsounds crossing add <name> [radius]` | crossing where you're standing, default radius 64 |
| `/railsounds crossing remove <name>` | delete it |
| `/railsounds crossing list` | list them all with their radii |
| `/railsounds crossing signal <name> <x y z>` | a redstone block appears at those coords while the bell rings — for lamps and gates. Keep that spot empty |
| `/railsounds crossing nosignal <name>` | undo the above |

### Train type
| Command | Does |
|---|---|
| `/diesel` | diesel horn, diesel engine sounds, 8 notches |
| `/steam` | steam whistle. No engine audio — see the engine note section |

Note these are **root commands**, not under `/railsounds`. Stand by a station with it in build
(assembly) mode and run one — the train you assemble there gets that type. Horn and engine sounds
both follow it, so you set it once per train.

### Yard rules

Stations run these automatically whenever a train stops there — you set it once, the station does
it forever. `yardRuleDelayTicks` is how long it waits first.

| Command | Does |
|---|---|
| `/railsounds yard couple <station>` | couple to whatever's there |
| `/railsounds yard uncouple <n\|all> <station>` | drop n cars off the end away from the loco |
| `/railsounds yard backin <station>` | scheduled trains **must** back in, single-cab locos included. The trip is driven in reverse from the previous stop, so make that stop the switch or lead just past the spur |
| `/railsounds yard reverse <station>` | trains **may** back in when that's the shorter way |
| `/railsounds yard forward <station>` | back to normal |
| `/railsounds yard clear <station>` | remove the rule |
| `/railsounds yard list` | list every rule |

### Diagnostics
| Command | Does |
|---|---|
| `/railsounds test <sound>` | play one sound where you stand. Use it to check the resource pack is actually installed |
| `/railsounds debug` | every train: speed and braking state, whether its engine is lit, what notch it's working in and at what pitch, and whether its bell is ringing |
| `/railsounds reload` | re-read `config/railsounds.json` without restarting |
| `/railsounds fixtrains` | re-measures every coupling and clears stress derails — reach for this if a train won't couple or has derailed itself |
| `/railsounds reset` | drop all mod state for the world. Last resort |

`/railsounds debug` is the quickest way to check the notching is doing what you expect: drive a
train, run it, and watch the notch number climb as you speed up.

---

## Config

`config/railsounds.json`, written on first run. `/railsounds reload` after editing.

About 70 options. The ones worth knowing about:

**Volumes** — these are set relative to each other on purpose, because trackside a horn drowns
everything and an idling engine is nearly nothing:

```
1.00  horn / whistle        0.65  rail clack
0.90  crossing bell         0.60  brake squeal
0.85  coupler clunk         0.55  loco bell
0.70  engine running/start  0.50  air release
                            0.25  engine idle
```

Change any of them freely — that's a starting point, not a rule.

**Engine** — `engineSounds` (master switch), `idleWhenStopped`, `enginePitchMin` / `enginePitchMax`
(how wide the notch spread is), `dieselNotches` (8), `notchCooldownTicks`, `startFadeTicks` /
`startFadeVolume` (how the startup blends into the idle).

**Clip lengths** — `idleClipTicks`, `runClipTicks`, `startClipTicks`, `locoBellClipTicks`,
`bellClipTicks`, `brakeClipTicks`, `hornClipTicks`, `whistleClipTicks`. These must match your
actual .ogg lengths at 20 ticks per second, or loops will gap or overlap. CI checks the five
looping ones against the real files on every build, so a mismatch can't ship.

**Shunting** — `coupleMaxSpeed`, `bufferMaxSpeed`, `uncoupleMaxSpeed`, `cutCoastFactor`,
`collisionAvoidance`, `easeIntoCuts`, `reverseIntoStations`.

**Crossings** — `defaultCrossingRadius`, `bellTailTicks`, `onCrossingDistance`.

Your edits survive updates. There's a migration chain that only moves a value if you've left it on
the old default, and it's tested in CI from every past version, so hand-tuned numbers stay
hand-tuned.

---

## Compatibility

This is built to sit quietly alongside other mods.

- **No block entities on any of the 30 blocks.** State lives in blockstate properties and
  scheduled ticks instead. That means no tick overhead you'd notice, nothing to corrupt, and
  nothing for another mod to trip over.
- **Every block reschedules its tick before doing any work**, so an exception can't permanently
  kill a block.
- **Steam 'n' Rails is never redistributed.** It's reached by reflection and mixins only, and
  everything degrades gracefully if it isn't installed — you get a log line, not a crash.
- **Create Fly is CC0** and lives in `libs/` only to compile against. It isn't bundled.
- **13 mixins, no `@Overwrite`.** Four of them use `@Redirect` (nine redirects all told), which is exclusive — if another mod
  redirects the same instruction you'll get a mixin conflict at startup. That's the one place a
  clash is realistically possible.
- **Handcars are skipped** (`ignoreHandcars`) — no brake squeal or couplers on a handcar.
- **Crossing signal blocks left on by a crash** get switched off when their chunk loads.
- **Every per-tick job is independently guarded.** One feature throwing can't take the others
  down, and a repeating fault writes one stack trace a minute instead of 1,200.
- All textures and models are generated from code. No downloaded assets anywhere.

---

## Testing

The build is more paranoid than most, because reading the code three times found nothing and
running it found real bugs.

CI does this on every push:

1. Build the mod.
2. Check the config migration chain by lifting the real `migrate()` out of the source, compiling it
   and running it from every past version.
3. Check every sound the code plays is declared, has a file behind it, and that each looping
   clip is exactly as long as the config claims.
4. Check every mixin class is registered in a config — an unregistered one compiles, ships and
   silently does nothing.
5. Boot a real server, place all 30 blocks in-game over RCON, tick them, power the redstone ones,
   run every console command, break them all.
6. **Restart the server** and verify the blocks that were left standing survived, that crossings
   and switch links persisted, and that scheduled ticks are still running after the reload.
7. Load a deliberately old-format config and verify migrations applied, hand-tuned values survived
   and absent fields came back as defaults rather than zeros.

Step 6 is the one that earns its keep. It caught a display-source binding race that three read-
throughs had missed, where the registration order between Create and this mod turned out to be
non-deterministic — it bound fine creating a world and failed loading the same world again.

### What's still untested

Anything that needs a player and a real train: right-click cycling on the sensors, the Switch
Linker flow, Interlocking Panel levers, cargo readings off an actually-loaded train, and flying
switches. Those need you, in a world, with a train.

---

## Building it yourself

```
./gradlew build
```

GitHub Actions builds on every push; the jar lands in the run's artifacts and on the `dist` branch.

Create Fly goes in `libs/create-fly.jar`. It isn't committed as a dependency you fetch — it's there
to compile against and nothing from it ships in the jar.

---

## Licence and credits

The mod code is mine. Create Fly is CC0. Steam 'n' Rails is LGPL-3.0 and is never redistributed —
only reflected against. All textures, models, blockstates, loot tables and recipes are generated by
`tools/gen_rail_assets.py`. The diesel sounds are from my own recording; the loco bell is a
synthesised placeholder.

Worth knowing: **Minecraft's usage guidelines don't allow selling mods.** Donations, ads on videos
and the various creator programs are the realistic routes if you want this to make anything.

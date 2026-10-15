# Create Rail Sounds (Fabric 26.2, Create Fly 6.0.9)

Server-side mod that makes Create trains sound real, driven by the train's actual speed every tick:

| Event | What triggers it |
|---|---|
| Rail joint clack-clack | every `clackSpacing` blocks per car, 2nd hit when the rear bogey crosses the joint; volume/pitch follow speed |
| Brake squeal | train decelerating harder than `brakeDecelThreshold`; louder the harder it brakes, pitch rises as it slows |
| Coupler slack | pulling away: clunks ripple front→back car by car. Stopping: slack bunches back→front |
| Air release | each car hisses right after the train stops |
| Crossing bell | rings while any car is inside a crossing's radius, plus a short tail after it clears |

Players don't need the mod: sounds come from the server resource pack (`server-resourcepack/`).

## Commands (op)
- `/railsounds crossing add <name> [radius]` — crossing where you stand (default radius 40)
- `/railsounds crossing remove <name>` / `list`
- `/railsounds crossing signal <name> <x y z>` — a redstone block appears there while the bell rings (lamps/gates). Keep that spot empty.
- `/railsounds test <sound>` — play one of the sounds where you stand
- `/railsounds debug` — speed and braking of every train
- `/railsounds reload` — re-read `config/railsounds.json`

Tune everything in `config/railsounds.json` (made on first run).

## Building
GitHub Actions builds on every push; grab the jar from the run's artifacts.
Create Fly (CC0) is in `libs/` only to compile against; it isn't bundled.

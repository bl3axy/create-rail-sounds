# Create Rail Sounds (Fabric 26.2, Create Fly 6.0.9)

Server-side mod that makes Create trains sound real, driven by the train's actual speed every tick:

| Event | What triggers it |
|---|---|
| Rail joint clack-clack | every `clackSpacing` blocks per car, 2nd hit when the rear bogey crosses the joint; volume/pitch follow speed |
| Brake squeal | only in the last part of a real stop: under `brakeStopSpeed` and slowing hard enough to stop within `brakeMaxTicksToStop`; once per stop, cut off when the train stops (`brakeCutOnStop`). Louder the harder it brakes, pitch rises as it slows |
| Coupler slack | clunks at each coupling, rippling front→back when pulling away and when stopping |
| Air release | each car hisses right after the train stops |
| Crossing bell | rings while a train is heading toward the crossing (inside its radius) or any car is on it; stops ~2s after the last car clears |

Players don't need the mod: sounds come from the server resource pack (`server-resourcepack/`).
With the mod on their client too, diesel horns / steam whistles play a long seamless hold and fade out from the moment you let go (`hornFadeTicks`, `whistleFadeTicks`). Without it they fall back to replay + short tail.

## Commands (op)
- `/railsounds crossing add <name> [radius]` — crossing where you stand (default radius 64)
- `/railsounds crossing remove <name>` / `list`
- `/railsounds crossing signal <name> <x y z>` — a redstone block appears there while the bell rings (lamps/gates). Keep that spot empty.
- `/railsounds test <sound>` — play one of the sounds where you stand
- `/railsounds debug` — speed and braking of every train
- `/railsounds reload` — re-read `config/railsounds.json`

Tune everything in `config/railsounds.json` (made on first run).

## Building
GitHub Actions builds on every push; grab the jar from the run's artifacts.
Create Fly (CC0) is in `libs/` only to compile against; it isn't bundled.

## Compatibility notes
- Steam 'n' Rails: handcars are skipped (`ignoreHandcars`); S&R bogeys still use Create's wheel sound so the custom rumble applies to them too.
- Trains moving away from a crossing don't ring it; a stopped train only rings if it's parked on it (`onCrossingDistance`).
- Crossing signal blocks that were left on by a crash/restart are switched off when their chunk loads.

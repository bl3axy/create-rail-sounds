Drop converted .ogg files (MONO, so they fade with distance) here:
  assets/railsounds/sounds/clack.ogg, brake_squeal.ogg, coupler_clunk.ogg, air_release.ogg, crossing_bell.ogg
Optional overrides of Create's own sounds go in assets/create/sounds.json with "replace": true
(create:train / train2 / train3 = the rolling rumble, create:whistle_train = the horn).

--- engine sounds (not recorded yet) ---
sounds.json already declares these four, so dropping the files in is all that's
needed - no json editing:
  sounds/diesel_idle.ogg    diesel ticking over at a stand   (loop, ~10s)
  sounds/steam_idle.ogg     steam loco simmering             (loop, ~10s)
  sounds/diesel_start.ogg   cranking through to steady idle  (one shot)
  sounds/steam_start.ogg    fire lighting / blower on        (one shot)
Convert with tools/convert.sh in.wav sounds/diesel_idle.ogg (mono, 44.1kHz).
Then set idleClipTicks in config/railsounds.json to the idle clip's length in
ticks (20 ticks = 1 second) so the loop restarts seamlessly.

#!/bin/sh
# usage: tools/convert.sh input.(mp3|wav|m4a...) output.ogg
# mono + 44.1kHz vorbis: mono is required for Minecraft distance fading
ffmpeg -y -i "$1" -ac 1 -ar 44100 -c:a libvorbis -q:a 5 "$2"

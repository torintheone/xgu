#!/usr/bin/env bash
# Synthesises the sound effects (effects.py) and encodes them into the app as Ogg Vorbis.
# Needs python3 with numpy, and ffmpeg with libvorbis. Run from anywhere: scripts/sounds/build.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

python3 "$here/effects.py" "$work"
mkdir -p "$root/app/src/main/res/raw"
for wav in "$work"/*.wav; do
  name="$(basename "$wav" .wav)"
  ffmpeg -loglevel error -y -i "$wav" -c:a libvorbis -q:a 4 "$root/app/src/main/res/raw/$name.ogg"
  echo "app/src/main/res/raw/$name.ogg: $(du -h "$root/app/src/main/res/raw/$name.ogg" | cut -f1)"
done

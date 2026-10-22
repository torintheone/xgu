#!/usr/bin/env bash
# Renders the background music (ambient.py) and encodes it into the app as Ogg Vorbis.
# Needs python3 with numpy, and ffmpeg with libvorbis. Run from anywhere: scripts/music/build.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

python3 "$here/ambient.py" "$work/ambient.wav"
mkdir -p "$root/app/src/main/res/raw"
ffmpeg -loglevel error -y -i "$work/ambient.wav" -c:a libvorbis -q:a 3 "$root/app/src/main/res/raw/ambient.ogg"
echo "app/src/main/res/raw/ambient.ogg: $(du -h "$root/app/src/main/res/raw/ambient.ogg" | cut -f1)"

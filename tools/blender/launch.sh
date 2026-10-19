#!/usr/bin/env bash
set -euo pipefail
blender_tools_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec blender "$blender_tools_dir/build/federation.blend" --python "$blender_tools_dir/sidebar.py"

#!/bin/sh
set -eu
visual_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
visual_profile="$visual_root/tools/blockbench/.local/profile"
visual_app=${BLOCKBENCH_PATH:-"$HOME/.local/bin/blockbench"}
if [ ! -f "$visual_root/tools/blockbench/.local/mcp.js" ]; then
    echo 'Run python3 tools/blockbench/install_mcp.py and bootstrap_mcp.mjs first.' >&2
    exit 1
fi
if [ "$#" -eq 0 ]; then
    set -- "$visual_root/tools/blockbench/projects/ae2_federation_overview.bbmodel"
fi
exec "$visual_app" --userData "$visual_profile" "$@"

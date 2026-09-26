"""Fetch the pinned plugin into this repository; do not change user configuration."""
from pathlib import Path
import hashlib
import json
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LOCAL = ROOT / 'tools/blockbench/.local'
COMMIT = '657e0af81883f338b8478b6adf07b3ca8907197d'
URL = f'https://raw.githubusercontent.com/jasonjgardner/blockbench-mcp-plugin/{COMMIT}/mcp.js'
SHA256 = '766ddeda3111bf38015ae5cad3c3396b3f6964c4f8e94020104fe1eaca737b53'


def main():
    LOCAL.mkdir(parents=True, exist_ok=True)
    data = urllib.request.urlopen(URL, timeout=60).read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise RuntimeError('Plugin checksum mismatch; refusing to install')
    source = data.decode()
    original = 'a.listen(t,()=>{console.log(`[MCP] Server listening on http://localhost:'
    replacement = 'a.listen(t,"127.0.0.1",()=>{console.log(`[MCP] Server listening on http://localhost:'
    if source.count(original) != 1:
        raise RuntimeError('Unexpected listener code; refusing to patch')
    # The pinned upstream listener binds all interfaces. Limit this instance to
    # loopback without modifying any global Node or Blockbench networking.
    patched = source.replace(original, replacement)
    (LOCAL / 'mcp.js').write_text(patched)
    profile = LOCAL / 'profile'
    profile.mkdir(exist_ok=True)
    permissions = profile / 'plugin_permissions.json'
    existing = json.loads(permissions.read_text()) if permissions.exists() else {}
    allowed = existing.setdefault('mcp', {}).setdefault('allowed', {})
    allowed.update(process=True, net=True, fs=True)
    permissions.write_text(json.dumps(existing, indent=2) + '\n')
    (LOCAL / 'installation.json').write_text(json.dumps({
        'version': '1.9.2', 'url': URL, 'upstream_sha256': SHA256,
        'installed_sha256': hashlib.sha256(patched.encode()).hexdigest(),
        'patch': 'Bind the HTTP listener to 127.0.0.1 only',
        'profile': str(profile), 'port': 31337,
    }, indent=2) + '\n')
    print('Installed pinned MCP plugin in tools/blockbench/.local/mcp.js')
    print('Next: run bootstrap_mcp.mjs once to register it in the project profile.')


if __name__ == '__main__':
    main()

"""The connected Router face composed from the artist's four tiles, with relief taken from the art.

Each exposed face of a member starts from `router_base`. Where a face edge lies on the body's outside, the
matching four rows of `router_side_hor` (top, bottom) or four columns of `router_side_ver` (left, right) are laid
over it; where both edges of a corner do, the corner's 4x4 square of `router_corner` covers that. Composed this
way a single block's face equals `router.png`, and a larger face the artist's concept.

Texels in the backing greys sit one model unit below the face; every other texel (frame, links and cores) is flush
with it, as in the standalone model. Walls close each step; each wall shows its raised texel folded over the edge.

Texture edges follow Minecraft's face UVs, the ones the standalone model uses: on the four sides the top edge
is up; on the top face u runs along x and v along z; on the bottom face v runs against z.

Standard library only.
"""
import struct
import zlib

from geometry import ASSETS, SIDES, face_points, neighbor

TILES = ('base', 'corner', 'side_hor', 'side_ver')
# The two backing greys of the Router art: these texels are recessed.
RECESSED = {(135, 143, 165), (120, 126, 151)}
DEPTH = 1
# The four texture edges of each face, as the side of the block they lie on: left (u = 0), right (u = 16),
# top (v = 0) and bottom (v = 16).
EDGES = {
    'north': ('east', 'west', 'up', 'down'),
    'south': ('west', 'east', 'up', 'down'),
    'west': ('north', 'south', 'up', 'down'),
    'east': ('south', 'north', 'up', 'down'),
    'up': ('west', 'east', 'north', 'south'),
    'down': ('west', 'east', 'south', 'north'),
}


def read_png(path):
    """Rows of (r, g, b, a) for a non-interlaced 8-bit RGB(A) or 1/2/4/8-bit palette PNG."""
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'{path} is not a PNG')
    position, chunks = 8, {}
    idat = b''
    while position < len(data):
        length, kind = struct.unpack('>I4s', data[position:position+8])
        body = data[position+8:position+8+length]
        position += 12 + length
        if kind == b'IDAT':
            idat += body
        else:
            chunks[kind] = body
    width, height, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', chunks[b'IHDR'])
    if interlace or color not in (2, 3, 6) or (color != 3 and depth != 8):
        raise ValueError(f'{path}: unsupported PNG layout')
    channels = {2: 3, 3: 1, 6: 4}[color]
    bits = depth * channels
    stride = (width * bits + 7) // 8
    step = max(1, bits // 8)
    raw = zlib.decompress(idat)
    rows, previous = [], bytearray(stride)
    for y in range(height):
        kind = raw[y*(stride+1)]
        line = bytearray(raw[y*(stride+1)+1:(y+1)*(stride+1)])
        for i in range(stride):
            left = line[i-step] if i >= step else 0
            up = previous[i]
            corner = previous[i-step] if i >= step else 0
            if kind == 1:
                line[i] = (line[i] + left) & 255
            elif kind == 2:
                line[i] = (line[i] + up) & 255
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 255
            elif kind == 4:
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                line[i] = (line[i] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        previous = line
        rows.append(line)
    if color != 3:
        return [[tuple(row[x*channels:(x+1)*channels]) + ((255,) if channels == 3 else ())
                 for x in range(width)] for row in rows]
    palette = chunks[b'PLTE']
    alpha = chunks.get(b'tRNS', b'')
    result = []
    for row in rows:
        pixels = []
        for x in range(width):
            bit = x * depth
            index = (row[bit // 8] >> (8 - depth - bit % 8)) & ((1 << depth) - 1)
            pixels.append(tuple(palette[index*3:index*3+3]) + (alpha[index] if index < len(alpha) else 255,))
        result.append(pixels)
    return result


def load_tiles():
    return {name: read_png(ASSETS / 'textures/block/router' / f'router_{name}.png') for name in TILES}


def compose(tiles, left, right, top, bottom):
    """16x16 rows of (tile, recessed) for a face whose edges marked true lie on the body's outside."""
    grid = [['base'] * 16 for _ in range(16)]

    def lay(tile, columns, rows):
        for v in rows:
            for u in columns:
                if tiles[tile][v][u][3]:
                    grid[v][u] = tile
    if top:
        lay('side_hor', range(16), range(4))
    if bottom:
        lay('side_hor', range(16), range(12, 16))
    if left:
        lay('side_ver', range(4), range(16))
    if right:
        lay('side_ver', range(12, 16), range(16))
    for vertical, horizontal, columns, rows in [(left, top, range(4), range(4)), (right, top, range(12, 16), range(4)),
                                                (left, bottom, range(4), range(12, 16)),
                                                (right, bottom, range(12, 16), range(12, 16))]:
        if vertical and horizontal:
            lay('corner', columns, rows)
    return [[(grid[v][u], tiles[grid[v][u]][v][u][:3] in RECESSED) for u in range(16)] for v in range(16)]


def to_model(side, u, v, d):
    """Model coordinates of texture point (u, v) on `side`, d units below the face."""
    return {'north': (16-u, 16-v, d), 'south': (u, 16-v, 16-d), 'west': (d, 16-v, u),
            'east': (16-d, 16-v, 16-u), 'up': (u, 16-d, v), 'down': (u, d, 16-v)}[side]


def to_texture(side, point):
    x, y, z = point
    return {'north': (16-x, 16-y), 'south': (x, 16-y), 'west': (z, 16-y), 'east': (16-z, 16-y),
            'up': (x, z), 'down': (x, 16-z)}[side]


def box(side, u0, v0, u1, v1, d0, d1):
    corners = [to_model(side, u, v, d) for u in (u0, u1) for v in (v0, v1) for d in (d0, d1)]
    return ([min(c[a] for c in corners) for a in range(3)], [max(c[a] for c in corners) for a in range(3)])


def rectangles(grid, key):
    """Greedy row-major merge of texels with equal key(u, v) into (u0, v0, u1, v1, key)."""
    done = [[False] * 16 for _ in range(16)]
    result = []
    for v in range(16):
        for u in range(16):
            if done[v][u]:
                continue
            k = key(u, v)
            u1 = u
            while u1 < 16 and not done[v][u1] and key(u1, v) == k:
                u1 += 1
            v1 = v + 1
            while v1 < 16 and all(not done[v1][i] and key(i, v1) == k for i in range(u, u1)):
                v1 += 1
            for j in range(v, v1):
                for i in range(u, u1):
                    done[j][i] = True
            result.append((u, v, u1, v1, k))
    return result


def quad(cell, side, points, uv, tile, kind):
    return {'positions': [[cell[a] + p[a]/16 for a in range(3)] for p in points],
            'uv': [[t[0]/16, t[1]/16] for t in uv], 'side': side, 'kind': kind,
            'cell': list(cell), 'element': -1, 'texture': f'block/router/router_{tile}'}


def face_quads(cell, side, grid, beyond):
    """Quads of one exposed face. beyond[edge] gives the texel just past that edge in a connected member, or None
    where the edge lies on the body's outside."""
    result = []
    for u0, v0, u1, v1, (tile, recessed) in rectangles(grid, lambda u, v: grid[v][u]):
        d = DEPTH if recessed else 0
        lo, hi = box(side, u0, v0, u1, v1, d, d)
        points = face_points(lo, hi)[side]
        uv = [to_texture(side, p) for p in points]
        result.append(quad(cell, side, points, uv, tile, 'tile-recess' if recessed else 'tile-surface'))
    left, right, top, bottom = EDGES[side]
    # Each raised texel walls in every recessed neighbour: (du, dv, wall side, texture edge). Runs of walls along
    # one texel row or column from one tile merge into one quad.
    for du, dv, wall_side, edge in [(-1, 0, left, 'left'), (1, 0, right, 'right'), (0, -1, top, 'top'),
                                    (0, 1, bottom, 'bottom')]:
        def walled(u, v):
            tile, recessed = grid[v][u]
            if recessed:
                return None
            nu, nv = u + du, v + dv
            if 0 <= nu < 16 and 0 <= nv < 16:
                lower = grid[nv][nu][1]
            else:
                past = beyond[edge]
                lower = past is not None and past(u if du == 0 else v)[1]
            return tile if lower else None
        for line in range(16):
            run = 0
            while run < 16:
                u, v = (line, run) if du else (run, line)
                tile = walled(u, v)
                end = run + 1
                while tile and end < 16 and walled(*((line, end) if du else (end, line))) == tile:
                    end += 1
                if tile:
                    u0, v0, u1, v1 = (line, run, line + 1, end) if du else (run, line, end, line + 1)
                    lo, hi = box(side, u0, v0, u1, v1, 0, DEPTH)
                    points = face_points(lo, hi)[wall_side]
                    uv = []
                    for p in points:
                        tu, tv = to_texture(side, p)
                        depth = _depth(side, p)
                        # Fold the raised texels over their edge: depth runs back across the texel.
                        if du:
                            tu = line + (1 - depth if du > 0 else depth)
                        else:
                            tv = line + (1 - depth if dv > 0 else depth)
                        uv.append((tu, tv))
                    result.append(quad(cell, side, points, uv, tile, 'tile-wall'))
                run = end
    for edge, rows in [('left', [grid[v][0] for v in range(16)]), ('right', [grid[v][15] for v in range(16)]),
                       ('top', grid[0]), ('bottom', grid[15])]:
        if beyond[edge] is None and any(recessed for _, recessed in rows):
            raise ValueError(f'The Router art recesses a texel on the outer {edge} edge of a face')
    return result


def _depth(side, point):
    axis, sign = SIDES[side]
    return point[axis] if sign < 0 else 16 - point[axis]


def build_cell(tiles, cell, cells):
    result = []
    for side in SIDES:
        if neighbor(cell, side) in cells:
            continue
        flags = [neighbor(cell, edge) not in cells for edge in EDGES[side]]
        grid = compose(tiles, *flags)
        beyond = {}
        for index, edge in enumerate(('left', 'right', 'top', 'bottom')):
            if flags[index]:
                beyond[edge] = None
                continue
            # The connected member past this edge shares the other two boundary flags; its edge toward us is inside.
            other = list(flags)
            other[index] = False
            other[index ^ 1] = False
            past = compose(tiles, *other)
            if edge == 'left':
                beyond[edge] = lambda i, g=past: g[i][15]
            elif edge == 'right':
                beyond[edge] = lambda i, g=past: g[i][0]
            elif edge == 'top':
                beyond[edge] = lambda i, g=past: g[15][i]
            else:
                beyond[edge] = lambda i, g=past: g[0][i]
        result.extend(face_quads(cell, side, grid, beyond))
    return result

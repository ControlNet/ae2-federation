"""Compose actual Router/Switch art into solid-cuboid connected preview meshes.

Standard-library only. Minecraft coordinates and normalized UVs are exported so
the result is independent of Blender. This is not a runtime structure manager.
"""
from itertools import product
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'common/src/main/resources/assets/ae2federation'
SIDES = {'west': (0, -1), 'east': (0, 1), 'down': (1, -1),
         'up': (1, 1), 'north': (2, -1), 'south': (2, 1)}


def load_model(device):
    if device not in ('router', 'switch'):
        raise ValueError('Unknown device: ' + device)
    return json.loads((ASSETS / 'models/block' / (device + '.json')).read_text())


def cuboid(size):
    if len(size) != 3 or any(type(n) is not int or n < 1 for n in size):
        raise ValueError('Dimensions must be three positive integers')
    return set(product(*(range(n) for n in size)))


def validate(cells):
    if not cells:
        raise ValueError('Empty structure')
    lo = tuple(min(p[a] for p in cells) for a in range(3))
    hi = tuple(max(p[a] for p in cells) for a in range(3))
    if len(cells) != (hi[0]-lo[0]+1)*(hi[1]-lo[1]+1)*(hi[2]-lo[2]+1):
        raise ValueError('Structure must fill a solid cuboid')
    return lo, hi


def neighbor(cell, side):
    axis, sign = SIDES[side]
    return tuple(cell[a] + (sign if a == axis else 0) for a in range(3))


def surface_cells(cells):
    return {(p, side) for p in cells for side in SIDES if neighbor(p, side) not in cells}


def face_points(lo, hi):
    x, y, z = lo
    X, Y, Z = hi
    return {
        'north': [(X,Y,z), (X,y,z), (x,y,z), (x,Y,z)],
        'south': [(x,Y,Z), (x,y,Z), (X,y,Z), (X,Y,Z)],
        'west': [(x,Y,z), (x,y,z), (x,y,Z), (x,Y,Z)],
        'east': [(X,Y,Z), (X,y,Z), (X,y,z), (X,Y,z)],
        'up': [(x,Y,z), (x,Y,Z), (X,Y,Z), (X,Y,z)],
        'down': [(x,y,Z), (x,y,z), (X,y,z), (X,y,Z)],
    }


def uv_rect(side, lo, hi):
    x, y, z = lo
    X, Y, Z = hi
    return {'down': [x,16-Z,X,16-z], 'up': [x,z,X,Z],
            'north': [16-X,16-Y,16-x,16-y], 'south': [x,16-Y,X,16-y],
            'west': [z,16-Y,Z,16-y], 'east': [16-Z,16-Y,16-z,16-y]}[side]


def quad(points, uv, device, cell, side, kind, element):
    return {'positions': [[cell[a] + p[a]/16 for a in range(3)] for p in points],
            'uv': [[u/16, v/16] for u, v in uv], 'side': side, 'kind': kind,
            'cell': list(cell), 'element': element,
            'texture': f'block/{device}/{device}'}


def element_quads(element, device, cell, kind, index, connections=(), extension=None):
    """Keep source UV rotations; extend edge end pixels without stretching details."""
    result = []
    original_lo, original_hi = element['from'], element['to']
    lo, hi = list(original_lo), list(original_hi)
    if extension:
        axis, start, end = extension
        lo[axis], hi[axis] = start, end
    for side, face in element['faces'].items():
        normal_axis, sign = SIDES[side]
        plane = lo[normal_axis] if sign < 0 else hi[normal_axis]
        if side in connections and plane == (0 if sign < 0 else 16):
            continue
        u, v, U, V = face['uv']
        source_uv = [(u,v), (u,V), (U,V), (U,v)]
        turns = face.get('rotation', 0)//90
        source_uv = source_uv[turns:] + source_uv[:turns]
        source_points = face_points(original_lo, original_hi)[side]
        intervals = [(None, None)]
        if extension and extension[0] != normal_axis:
            axis = extension[0]
            cuts = sorted({lo[axis], hi[axis], original_lo[axis], original_hi[axis]})
            intervals = list(zip(cuts, cuts[1:]))
        for start, end in intervals:
            patch_lo, patch_hi = lo.copy(), hi.copy()
            if start is not None:
                patch_lo[axis], patch_hi[axis] = start, end
            points = face_points(patch_lo, patch_hi)[side]
            uv = []
            for p in points:
                coords = list(p)
                if start is not None:
                    if end <= original_lo[axis]:
                        coords[axis] = original_lo[axis] + .5
                    elif start >= original_hi[axis]:
                        coords[axis] = original_hi[axis] - .5
                origin, vertical, _, horizontal = source_points
                du = [horizontal[a]-origin[a] for a in range(3)]
                dv = [vertical[a]-origin[a] for a in range(3)]
                s = sum((coords[a]-origin[a])*du[a] for a in range(3))/sum(c*c for c in du)
                t = sum((coords[a]-origin[a])*dv[a] for a in range(3))/sum(c*c for c in dv)
                uv.append(tuple(source_uv[0][a] + s*(source_uv[3][a]-source_uv[0][a])
                                + t*(source_uv[1][a]-source_uv[0][a]) for a in range(2)))
            result.append(quad(points, uv, device, cell, side, kind, index))
    return result


def flat_indices(group):
    if isinstance(group, int):
        return [group]
    return [i for child in group['children'] for i in flat_indices(child)]


def boundary_sides(lo, hi, rim):
    return {side for side, (a, sign) in SIDES.items()
            if (hi[a] <= rim if sign < 0 else lo[a] >= 16-rim)}


def panel_quads(device, cell, side, connections, rim):
    """Nine-slice center surface: retain center pixels and extend borderless ends."""
    axis, sign = SIDES[side]
    tangent = [a for a in range(3) if a != axis]
    spans = []
    for a in tangent:
        negative = next(s for s, pair in SIDES.items() if pair == (a, -1))
        positive = next(s for s, pair in SIDES.items() if pair == (a, 1))
        cuts = [rim, 16-rim]
        if negative in connections:
            cuts.insert(0, 0)
        if positive in connections:
            cuts.append(16)
        spans.append(list(zip(cuts, cuts[1:])))
    result = []
    inset = 1 if device == 'router' else 0
    for pair in product(*spans):
        lo, hi = [0,0,0], [16,16,16]
        lo[axis] = hi[axis] = inset if sign < 0 else 16-inset
        for a, (start, end) in zip(tangent, pair):
            lo[a], hi[a] = start, end
        points = face_points(lo, hi)[side]
        uv_lo, uv_hi = lo.copy(), hi.copy()
        for a, (start, end) in zip(tangent, pair):
            if end <= rim:
                uv_lo[a] = uv_hi[a] = rim+.5
            elif start >= 16-rim:
                uv_lo[a] = uv_hi[a] = 16-rim-.5
        u,v,U,V = uv_rect(side, uv_lo, uv_hi)
        uv = [(u,v), (u,V), (U,V), (U,v)]
        result.append(quad(points, uv, device, cell, side, 'panel', -1))
    return result


def switch_fill(cell, side, connections, style):
    """Fill the gaps using real palette pixels from the artist's Switch atlas.

    Coordinates below are face-local texture coordinates, so all six faces use
    identical geometry. Layers stay inside the block's exterior plane.
    """
    normal, sign = SIDES[side]
    points = face_points([0,0,0], [16,16,16])[side]
    origin, vertical, _, horizontal = points
    u_axis = tuple((horizontal[a]-origin[a])/16 for a in range(3))
    v_axis = tuple((vertical[a]-origin[a])/16 for a in range(3))

    def connected_axis(vector, direction):
        axis = next(a for a in range(3) if vector[a])
        return next(s for s, pair in SIDES.items() if pair == (axis, vector[axis]*direction)) in connections

    left, right = connected_axis(u_axis, -1), connected_axis(u_axis, 1)
    top, bottom = connected_axis(v_axis, -1), connected_axis(v_axis, 1)
    if not any((left, right, top, bottom)):
        return []
    result = []
    # Every swatch is an actual source pixel, not a new replacement texture.
    palette = {'dark': (1,1), 'plate': (3,1), 'metal': (1,5),
               'cyan': (7,0), 'highlight': (6,0), 'shadow': (7,1)}

    def rectangle(u, v, U, V, color, depth, kind):
        if U <= u or V <= v:
            return
        positions = []
        for x,y in [(u,v),(u,V),(U,V),(U,v)]:
            p = [origin[a]+u_axis[a]*x+v_axis[a]*y for a in range(3)]
            p[normal] -= sign*depth
            positions.append(p)
        px, py = palette[color]
        uv = [(px+.5, py+.5)]*4
        result.append(quad(positions, uv, 'switch', cell, side, kind, -2))

    if style == 'infill':
        # Cover the pale field around the original 10x10 purple center.
        # Four disjoint strips meet across neighboring block boundaries.
        lo_u, hi_u = (0 if left else 2), (16 if right else 14)
        lo_v, hi_v = (0 if top else 2), (16 if bottom else 14)
        for rect in [(lo_u,lo_v,hi_u,3), (lo_u,13,hi_u,hi_v),
                     (lo_u,3,3,13), (13,3,hi_u,13)]:
            rectangle(*rect, 'plate', .18, 'infill')
        # Small grooves occupy the four-way spaces without restoring block frames.
        for u, v, enabled in [(0,0,left and top), (13,0,right and top),
                              (0,13,left and bottom), (13,13,right and bottom)]:
            if enabled:
                for offset in [.65,1.65]:
                    rectangle(u+.5,v+offset,u+2.5,v+offset+.4,
                              'dark', .12, 'infill-groove')

    # Paired half-links meet at block boundaries; none cross a body corner.
    for active, a,b in [(left,0,3), (right,13,16)]:
        if active:
            rectangle(a,6,b,10,'dark',.13,'link-bed')
            rectangle(a,7,b,9,'cyan',.07,'link')
            rectangle(a,7,b,7.5,'highlight',.05,'link-highlight')
            rectangle(a,8.5,b,9,'shadow',.04,'link-shadow')
    for active, a,b in [(top,0,3), (bottom,13,16)]:
        if active:
            rectangle(6,a,10,b,'dark',.13,'link-bed')
            rectangle(7,a,9,b,'cyan',.07,'link')
            rectangle(7,a,7.5,b,'highlight',.05,'link-highlight')
            rectangle(8.5,a,9,b,'shadow',.04,'link-shadow')
    return result


def build(device, cells, connected=True, switch_style='plain', router_style='plain'):
    if switch_style not in ('plain', 'links', 'infill', 'edge-matched', 'white-backed'):
        raise ValueError('Unknown Switch surface style: ' + switch_style)
    if router_style not in ('plain','raised'):
        raise ValueError('Unknown Router surface style: ' + router_style)
    cells = set(cells)
    validate(cells)
    model = load_model(device)
    if device == 'switch' and connected and switch_style in ('edge-matched', 'white-backed'):
        from switch_connectors import match_edges
        result = match_edges(build(device,cells,True,'infill'),cells,model)
        if switch_style == 'white-backed':
            # Original pale body pixel; preserve the contact sockets and relief.
            for q in result:
                if q['kind'] == 'infill':
                    q['uv'] = [[2.5/16, 2.5/16] for _ in range(4)]
        return result
    elements = model['elements']
    rim = 3 if device == 'router' else 2
    groups = {g['name']: g for g in model['groups'] if isinstance(g, dict)}
    corners = flat_indices(groups['corner'])
    edges = groups['edge']['children']
    result = []
    for cell in sorted(cells):
        connections = {side for side in SIDES if neighbor(cell, side) in cells}
        if not connected:
            for index, element in enumerate(elements):
                result.extend(element_quads(element, device, cell, 'original', index))
            continue
        for index in corners:
            element = elements[index]
            if not boundary_sides(element['from'], element['to'], rim) & connections:
                result.extend(element_quads(element, device, cell, 'corner', index))
        for group in edges:
            indices = flat_indices(group)
            lo = [min(elements[i]['from'][a] for i in indices) for a in range(3)]
            hi = [max(elements[i]['to'][a] for i in indices) for a in range(3)]
            boundaries = boundary_sides(lo, hi, rim)
            if boundaries & connections:
                continue
            axis = next(a for a in range(3) if all(SIDES[s][0] != a for s in boundaries))
            for index in indices:
                element = elements[index]
                start, end = element['from'][axis], element['to'][axis]
                for side in connections:
                    a, sign = SIDES[side]
                    if a == axis and sign < 0 and start == rim:
                        start = 0
                    if a == axis and sign > 0 and end == 16-rim:
                        end = 16
                result.extend(element_quads(element, device, cell, 'edge', index,
                                            connections, (axis, start, end)))
        for side in SIDES:
            if side in connections:
                continue
            panels = panel_quads(device, cell, side, connections, rim)
            if device == 'switch' and switch_style != 'plain':
                fill = switch_fill(cell, side, connections, switch_style)
                if fill:
                    normal, sign = SIDES[side]
                    for panel in panels:
                        for p in panel['positions']:
                            p[normal] -= sign*.25/16
                    result.extend(fill)
            result.extend(panels)
            if device == 'router':
                # Retain the artist's raised core and its four small face details.
                for index in flat_indices(groups['face']) + flat_indices(groups['face_detail']):
                    element = elements[index]
                    if side in boundary_sides(element['from'], element['to'], 1):
                        extension = None
                        connection = None
                        if router_style == 'raised':
                            # Continue the actual 4-wide, 1-high core tabs toward
                            # neighboring members, at core height above the skin.
                            for direction in connections:
                                axis,sign = SIDES[direction]
                                if axis == SIDES[side][0]:
                                    continue
                                start,end = element['from'][axis],element['to'][axis]
                                if (start,end) == (3,4) and sign < 0:
                                    extension,connection = (axis,0,end),direction
                                elif (start,end) == (12,13) and sign > 0:
                                    extension,connection = (axis,start,16),direction
                        generated = element_quads(element,device,cell,
                                                  'raised-link' if extension else 'detail',index,
                                                  connections,extension)
                        if extension:
                            for q in generated:
                                q['surface_side'],q['connection'] = side,connection
                        result.extend(generated)
    return result

"""Geometry checks for asset-backed connected device previews."""
import random
import unittest

from geometry import SIDES, build, cuboid, load_model, surface_cells, validate


def ray_hits(origin, direction, quads):
    """Whether the ray from origin along direction, up to one direction length, meets a quad's front."""
    def sub(a, b): return [a[i]-b[i] for i in range(3)]
    def dot(a, b): return sum(a[i]*b[i] for i in range(3))
    for q in quads:
        p = q['positions']
        u, v = sub(p[1], p[0]), sub(p[2], p[0])
        n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        if dot(n, direction) >= 0:
            continue
        t = dot(n, sub(p[0], origin))/dot(n, direction)
        if not 0 < t < 1:
            continue
        x = [origin[i]+t*direction[i] for i in range(3)]
        if all(min(c[i] for c in p)-1e-9 <= x[i] <= max(c[i] for c in p)+1e-9 for i in range(3)):
            return True
    return False


class GeometryTest(unittest.TestCase):
    def test_only_complete_cuboids(self):
        for size in [(1, 1, 1), (1, 4, 1), (4, 1, 3), (2, 3, 4)]:
            cells = cuboid(size)
            self.assertEqual(validate(cells), ((0, 0, 0), tuple(n - 1 for n in size)))
        for cells in [set(), {(0, 0, 0), (1, 0, 0), (0, 1, 0)},
                      cuboid((3, 3, 3)) - {(1, 1, 1)},
                      cuboid((2, 2, 2)) | {(2, 0, 0)}]:
            with self.assertRaises(ValueError):
                validate(cells)

    def test_exactly_eight_global_corners(self):
        for device in ['router', 'switch']:
            for size in [(1, 1, 1), (1, 1, 4), (1, 3, 3), (3, 2, 4)]:
                quads = build(device, cuboid(size))
                corners = {(tuple(q['cell']), q['element']) for q in quads if q['kind'] == 'corner'}
                self.assertEqual(len(corners), 8, (device, size))

    def test_panels_cover_every_exterior_cell_and_no_interior_cell(self):
        for device in ['router', 'switch']:
            for size in [(1, 1, 1), (1, 1, 4), (1, 3, 3), (3, 3, 3)]:
                cells = cuboid(size)
                panels = {(tuple(q['cell']), q['side']) for q in build(device, cells)
                          if q['kind'] == 'panel'}
                self.assertEqual(panels, surface_cells(cells))

    def test_bounds_uvs_and_nonzero_quad_area(self):
        for device in ['router', 'switch']:
            for q in build(device, cuboid((3, 2, 4))):
                for p in q['positions']:
                    self.assertTrue(all(0 <= p[a] <= (3, 2, 4)[a] for a in range(3)))
                self.assertTrue(all(0 <= v <= 1 for uv in q['uv'] for v in uv))
                p, r, s = q['positions'][:3]
                a = [r[i] - p[i] for i in range(3)]
                b = [s[i] - p[i] for i in range(3)]
                cross = [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
                self.assertGreater(sum(v*v for v in cross), 0)

    def test_repeatable_and_translatable(self):
        cells = cuboid((2, 2, 2))
        a = build('router', cells)
        self.assertEqual(a, build('router', set(reversed(sorted(cells)))))
        b = build('router', {(x+5, y+7, z-3) for x, y, z in cells})
        for qa, qb in zip(a, b):
            for pa, pb in zip(qa['positions'], qb['positions']):
                self.assertEqual(tuple(pb[i]-pa[i] for i in range(3)), (5, 7, -3))

    def test_actual_artist_assets(self):
        self.assertEqual(len(load_model('router')['elements']), 51)
        self.assertEqual(len(load_model('switch')['elements']), 73)

    def test_panel_area_has_no_missing_strips_or_duplicate_coverage(self):
        for device, rim in [('router', 3), ('switch', 2)]:
            for size in [(1, 1, 4), (1, 3, 3), (3, 2, 4)]:
                quads = build(device, cuboid(size))
                for side, (normal, _) in SIDES.items():
                    axes = [a for a in range(3) if a != normal]
                    area = 0
                    for q in quads:
                        if q['kind'] != 'panel' or q['side'] != side:
                            continue
                        extents = [max(p[a] for p in q['positions']) - min(p[a] for p in q['positions'])
                                   for a in axes]
                        area += extents[0]*extents[1]
                    expected = (size[axes[0]]-2*rim/16)*(size[axes[1]]-2*rim/16)
                    self.assertAlmostEqual(area, expected, msg=(device, size, side))

    def test_recessed_panels_stay_at_the_real_outer_skin(self):
        size = (3, 2, 4)
        for device, inset in [('router', 1/16), ('switch', 0)]:
            for q in build(device, cuboid(size)):
                if q['kind'] != 'panel':
                    continue
                axis, sign = SIDES[q['side']]
                expected = inset if sign < 0 else size[axis]-inset
                self.assertTrue(all(p[axis] == expected for p in q['positions']))

    def test_switch_styles_preserve_router_and_single_block(self):
        for style in ['links', 'infill', 'edge-matched']:
            self.assertEqual(build('router', cuboid((3,2,2))),
                             build('router', cuboid((3,2,2)), switch_style=style))
            self.assertEqual(build('switch', cuboid((1,1,1))),
                             build('switch', cuboid((1,1,1)), switch_style=style))

    def test_fill_is_exterior_bounded_and_uses_original_texture(self):
        for size in [(1,1,4), (1,3,3), (3,2,4)]:
            cells = cuboid(size)
            outside = surface_cells(cells)
            for style in ['links', 'infill']:
                additions = [q for q in build('switch', cells, switch_style=style) if q['element'] == -2]
                self.assertTrue(additions)
                for q in additions:
                    self.assertIn((tuple(q['cell']), q['side']), outside)
                    self.assertEqual(q['texture'], 'block/switch/switch')
                    for p in q['positions']:
                        self.assertTrue(all(0 <= p[a] <= size[a] for a in range(3)))
                    self.assertTrue(all(0 <= v <= 1 for uv in q['uv'] for v in uv))

    def test_links_meet_across_all_six_face_orientations(self):
        cells = cuboid((3,3,3))
        links = [q for q in build('switch', cells, switch_style='links') if q['kind'] == 'link']
        segments = {}
        for q in links:
            for a in range(3):
                if a == SIDES[q['side']][0]:
                    continue
                for boundary in [1,2]:
                    ends = [tuple(round(v,6) for v in p) for p in q['positions'] if p[a] == boundary]
                    if len(ends) == 2:
                        key = (q['side'], tuple(sorted(ends)))
                        segments[key] = segments.get(key, 0)+1
        self.assertTrue(segments)
        self.assertTrue(all(count == 2 for count in segments.values()))

    def test_edge_matched_contacts_have_source_depth_and_uvs(self):
        quads = build('switch',cuboid((2,2,2)),switch_style='edge-matched')
        contacts = [q for q in quads if q['kind'] == 'edge-contact']
        self.assertTrue(contacts)
        depths = set()
        for q in contacts:
            axis,sign = SIDES[q['side']]
            for p in q['positions']:
                local = (p[axis]-q['cell'][axis])*16
                depths.add(round(local if sign < 0 else 16-local,6))
            if q['element'] == 15 and q['source_face'] == 'north':
                self.assertEqual(set(tuple(uv) for uv in q['uv']),
                                 {(6/16,14/16),(6/16,1),(10/16,1),(10/16,14/16)})
        self.assertEqual(depths,{0,1,2})
        self.assertTrue(any(q['kind']=='socket-wall' for q in quads))
        self.assertFalse(any(q['kind'].startswith('link') for q in quads))

    def test_socket_is_cut_out_of_skin_and_infill(self):
        from switch_connectors import connections_on_face, footprint, local_uv
        cells = cuboid((2,2,2))
        for q in build('switch',cells,switch_style='edge-matched'):
            for p in q['positions']:
                self.assertTrue(all(0 <= p[a] <= 2 for a in range(3)))
            if q['kind'] not in ('panel','infill'):
                continue
            local = [local_uv(q['side'],q['cell'],p) for p in q['positions']]
            u,v,U,V = local[0][0],local[0][1],local[2][0],local[2][1]
            for c in connections_on_face(q['side'],tuple(q['cell']),cells):
                x,y,X,Y = footprint(c)
                self.assertFalse(max(u,x)<min(U,X) and max(v,y)<min(V,Y))

    def test_router_links_raise_to_core_height_with_real_sidewalls(self):
        quads = build('router',cuboid((3,2,2)),router_style='raised')
        links = [q for q in quads if q['kind']=='raised-link']
        self.assertTrue(links)
        surfaces = set()
        walls = set()
        for q in links:
            axis,sign = SIDES[q['surface_side']]
            depth = {round((p[axis]-q['cell'][axis])*16 if sign<0 else
                           16-(p[axis]-q['cell'][axis])*16,6) for p in q['positions']}
            if q['side']==q['surface_side']:
                self.assertEqual(depth,{0})
                surfaces.add(q['surface_side'])
            else:
                self.assertEqual(depth,{0,1})
                walls.add(q['surface_side'])
            self.assertEqual(q['texture'],'block/router/router')
        self.assertEqual(surfaces,set(SIDES))
        self.assertEqual(walls,set(SIDES))

    def test_router_raised_links_join_at_seams(self):
        quads = build('router',cuboid((3,3,3)),router_style='raised')
        edges = {}
        for q in quads:
            if q['kind']!='raised-link' or q['side']!=q['surface_side']:
                continue
            axis,sign = SIDES[q['connection']]
            boundary = q['cell'][axis]+(1 if sign>0 else 0)
            endpoints = [tuple(p) for p in q['positions'] if p[axis]==boundary]
            if len(endpoints)==2:
                key=(q['side'],tuple(sorted(endpoints)))
                edges[key]=edges.get(key,0)+1
        self.assertTrue(edges)
        self.assertTrue(all(count==2 for count in edges.values()))

    def test_router_raise_preserves_core_frame_switch_and_single_block(self):
        cells=cuboid((3,2,2))
        plain=build('router',cells)
        raised=build('router',cells,router_style='raised')
        for q in plain:
            if q['kind']!='detail' or 21<=q['element']<=26:
                self.assertIn(q,raised)
        self.assertEqual(build('router',cuboid((1,1,1))),
                         build('router',cuboid((1,1,1)),router_style='raised'))
        self.assertEqual(build('switch',cells,switch_style='edge-matched'),
                         build('switch',cells,switch_style='edge-matched',router_style='raised'))

    def test_connected_switch_and_router_members_are_opaque(self):
        # Rays entering an exposed face toward the block centre must meet a front face: the connected Switch
        # drops the body and face slabs, so the edge channels need their own inner walls.
        rng = random.Random(1)
        order = ['west', 'east', 'down', 'up', 'north', 'south']
        for device in ['router', 'switch']:
            for mask in [1, 4, 5, 3, 21, 42]:
                connected = {s for i, s in enumerate(order) if mask >> i & 1}
                cells = {(0, 0, 0)}
                for side in connected:
                    axis, sign = SIDES[side]
                    cells |= {tuple(c[a] + (sign if a == axis else 0) for a in range(3)) for c in cells}
                quads = [q for q in build(device, cells, switch_style='white-backed', router_style='raised')
                         if tuple(q['cell']) == (0, 0, 0)]
                exposed = [s for s in order if s not in connected]
                for _ in range(600):
                    side = rng.choice(exposed)
                    axis, sign = SIDES[side]
                    origin = [rng.uniform(.02, .98) for _ in range(3)]
                    origin[axis] = -.05 if sign < 0 else 1.05
                    target = [rng.uniform(.3, .7) for _ in range(3)]
                    direction = [target[i]-origin[i] for i in range(3)]
                    self.assertTrue(ray_hits(origin, direction, quads), (device, mask, side, origin, target))


if __name__ == '__main__':
    unittest.main()

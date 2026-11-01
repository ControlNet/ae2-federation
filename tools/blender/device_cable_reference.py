"""Current cable materials, ordinary Java export, and straight bundle presentation adapter.

Bundle panels follow BundleShellLayout's texel rule; original artist rings retain their
UVs and rotations. Device ends retain the closed shell and per-member dense arms.
"""
import copy
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

from assets import model_quads
from build_scene import mesh_object
from bundle_beams import texel, Mesh, Style
from render_bundle_beams import beam_material, build_object
import cable_beams
import plasma_beam

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools/blockbench'))
from export_beam_former_models import export_elements

CORE = 'ae2federation:part/cable/dense/core'
COLLAR = 'ae2federation:part/cable/dense/collar'
RING = 'ae2federation:block/large_cable/large_cable'


def sampled_material(materials, texture, u, v, name):
    source = materials.get(texture)
    image, _ = materials.load_image(texture)
    mat = source.copy()
    mat.name = 'Cable matched / '+name
    coordinate = mat.node_tree.nodes.new('ShaderNodeCombineXYZ')
    coordinate.inputs['X'].default_value = (u+.5)/image.size[0]
    coordinate.inputs['Y'].default_value = 1-(v+.5)/image.size[1]
    for node in mat.node_tree.nodes:
        if node.type == 'TEX_IMAGE':
            for link in list(node.inputs['Vector'].links):
                mat.node_tree.links.remove(link)
            mat.node_tree.links.new(coordinate.outputs[0], node.inputs['Vector'])
    mat['source_texture'] = texture
    mat['source_texel'] = [u, v]
    return mat


def matched_palette(materials):
    return {
        'white': sampled_material(materials, CORE, 4, 6, 'white rail'),
        'silver': sampled_material(materials, CORE, 2, 6, 'outer rail'),
        'dark': sampled_material(materials, CORE, 3, 6, 'dark stripe'),
        'glass': sampled_material(materials, CORE, 6, 6, 'glass, original alpha 75/255'),
        'cyan': sampled_material(materials, COLLAR, 3, 6, 'cyan collar'),
        'ice': sampled_material(materials, COLLAR, 4, 6, 'cyan highlight'),
        'teal': sampled_material(materials, COLLAR, 3, 11, 'collar dark'),
        'ring': sampled_material(materials, RING, 6, 14, 'bundle ring metal'),
    }


def transform(obj, origin, direction, local_offset=(0, 0, 0)):
    """Rotate Blender +X onto direction, then place at the device's contact plane."""
    rotation = Vector((1, 0, 0)).rotation_difference(Vector(direction))
    for vertex in obj.data.vertices:
        vertex.co = rotation @ (vertex.co+Vector(local_offset))
    obj.location = origin
    return obj


def ring_quads(size, assets):
    name = 'large_cable_straight' if size == 2 else 'huge_cable_straight'
    project = json.loads((ROOT/'tools/blockbench/projects'/f'{name}.bbmodel').read_text())
    elements = export_elements(project)
    offset = (0, 0, 0) if size == 2 else (16, 24, 16)
    for element in elements:
        for key in ('from', 'to'):
            element[key] = [x+d for x, d in zip(element[key], offset)]
        if 'rotation' in element:
            element['rotation']['origin'] = [x+d for x, d in zip(element['rotation']['origin'], offset)]
    model = {'elements': elements, 'textures': {'0': RING}}
    quads = model_quads(assets, model)
    # Artist models use Z as their axis. Return X-axis Minecraft quads centred across Y/Z.
    for q in quads:
        q['positions'] = [(z-size/2, y-size/2, size/2-x) for x, y, z in q['positions']]
    return quads


def bundle_shell(size, length):
    lo, hi = 2, 16*size-2
    a0, a1 = 2, round(length*16)-2
    result = []

    def panel(point, urange, vrange):
        # Merge adjacent equal texels in each row; retain 1/16-block rail widths.
        for v in range(*vrange):
            u = urange[0]
            while u < urange[1]:
                sample = texel(u, v, *urange, *vrange)
                end = u+1
                while end < urange[1] and texel(end, v, *urange, *vrange) == sample:
                    end += 1
                uv = ((sample[0]+.5)/16, (sample[1]+.5)/16)
                result.append({'positions': [tuple(c/16 for c in point(x, y))
                                             for x, y in ((u,v),(end,v),(end,v+1),(u,v+1))],
                               'uv': [uv]*4, 'texture': CORE})
                u = end
    for side in (lo, hi):
        panel(lambda a, b, s=side: (a, s-size*8, b-size*8), (a0,a1), (lo,hi))
        panel(lambda a, b, s=side: (a, b-size*8, s-size*8), (a0,a1), (lo,hi))
    for side in (a0,a1):
        panel(lambda a, b, s=side: (s, a-size*8, b-size*8), (lo,hi), (lo,hi))
    return result


def carrier(coll, origin, direction, size, length):
    """Use the repository's R/LITE and R/PLASMA study adapters at 60% load."""
    if size == 1:
        mesh = Mesh()
        cable_beams.r_beam(mesh, Style(.6,.25),
                          [plasma_beam.straight(0,0,length,(0,0,0),16)],
                          cable_beams.FILL, 2, 8)
        offset = (0, 0, 0)
    else:
        mesh = plasma_beam.generate('ship_soft', .6, .25,
                                    [plasma_beam.straight(0, 0, length, (0, 0, 0), 16)])
        if size == 3:
            for i, face in enumerate(mesh.faces):
                scale = 4/3 if mesh.materials[mesh.indices[i]][3] == 'solid' else 1.5
                for index in face:
                    x,y,z = mesh.vertices[index]
                    mesh.vertices[index] = (x,y*scale,z*scale)
        offset = (0,0,0)
    mats = [beam_material('Cable reference / '+m[0], *m[1:]) for m in mesh.materials]
    obj = build_object('Cable / current R light adapter', [[mesh.vertices[i] for i in face] for face in mesh.faces],
                       coll, mats, mesh.indices)
    transform(obj, origin, direction, offset)
    obj['cable_size'] = size
    obj['beam_origin'] = list(origin)
    obj['beam_direction'] = list(direction)
    obj['beam_length'] = length
    obj['beam_core_slots'] = [i for i,m in enumerate(mesh.materials) if m[3]=='solid']
    return obj


def cable(coll, origin, direction, size, length, assets, materials, export):
    """A straight run with its first end touching a device; no ordinary-cable scaling."""
    origin = Vector(origin)
    rotation = Vector((1,0,0)).rotation_difference(Vector(direction))
    if size == 1:
        for index in range(int(length)):
            mask = '7' if index == 0 else '12' if index == int(length)-1 else '15'
            obj = mesh_object('Cable / exported ordinary shell', export['shells'][mask], coll, materials)
            transform(obj, origin+rotation@Vector((index,0,0)), direction, (0,.5,-.5))
            obj['cable_size'] = size
            obj['connection_bits'] = int(mask)
    else:
        obj = mesh_object('Cable / bundle shell texel layout', bundle_shell(size,length), coll, materials)
        transform(obj, origin, direction)
        obj['cable_size'] = size
        # Each dense member reaches from the shell cap at 2/16 to the device plane.
        for u in range(size):
            for v in range(size):
                element = {'from': [0,3,3], 'to': [2,13,13],
                           'faces': {f:{'texture':'#0'} for f in ('east','up','down','north','south')}}
                qs = model_quads(assets, {'elements':[element], 'textures':{'0':'ae2federation:part/cable/dense/line'}})
                arm = mesh_object('Cable / bundle member dense contact', qs, coll, materials)
                transform(arm, origin+rotation@Vector((0,-(v+.5-size/2),u+.5-size/2)), direction, (0,.5,-.5))
                arm['cable_size'] = size
        ring = ring_quads(size, assets)
        for distance in ([1] if size == 2 else [1.5]):
            obj = mesh_object('Cable / original artist bundle ring', copy.deepcopy(ring), coll, materials)
            transform(obj, origin+rotation@Vector((distance,0,0)), direction)
            obj['cable_size'] = size
    carrier(coll, origin, direction, size, length)

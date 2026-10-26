"""Original editable device concepts, in block units and Blender Z-up coordinates.

Quantum Cube: open frame with a central energy sphere and six converging beams.
Beam Converter: longitudinal conversion cells, FE contacts and a cyan front socket.
These are Blender proposals; they do not change Minecraft models or behavior.
"""
import itertools
import math
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

from build_scene import collection, mesh_object, setup_scene

PALETTE = {
    'ceramic': '#ececf4', 'ceramic_shadow': '#a4a8bf', 'seam': '#54556e',
    'dark': '#282b40', 'inset': '#393c54', 'cyan': '#67bdce',
    'cyan_light': '#b5f4f5', 'violet': '#9871d1', 'violet_dark': '#57457f',
    'amber': '#dab677',
}
FACES = {
    'front': ((1, 0, 0), (0, 0, 1), (0, -1, 0)),
    'back': ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
    'right': ((0, 1, 0), (0, 0, 1), (1, 0, 0)),
    'left': ((0, -1, 0), (0, 0, 1), (-1, 0, 0)),
    'top': ((1, 0, 0), (0, 1, 0), (0, 0, 1)),
    'bottom': ((1, 0, 0), (0, -1, 0), (0, 0, -1)),
}


def linear(hex_color):
    rgb = [int(hex_color[i:i+2], 16) / 255 for i in (1, 3, 5)]
    return tuple(x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in rgb)


def material(name, color, emission=0, alpha=1):
    mat = bpy.data.materials.new('Study / ' + name)
    mat.diffuse_color = (*linear(color), alpha)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    shader = nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*linear(color), 1)
    shader.inputs['Roughness'].default_value = .72
    shader.inputs['Emission Color'].default_value = (*linear(color), 1)
    shader.inputs['Emission Strength'].default_value = emission
    if alpha < 1:
        mix = nodes.new('ShaderNodeMixShader')
        transparent = nodes.new('ShaderNodeBsdfTransparent')
        mix.inputs[0].default_value = alpha
        links.new(transparent.outputs[0], mix.inputs[1])
        links.new(shader.outputs[0], mix.inputs[2])
        links.new(mix.outputs[0], nodes.get('Material Output').inputs['Surface'])
    return mat


def palette():
    mats = {key: material(key, value) for key, value in PALETTE.items()}
    mats['light'] = material('Optical line', '#8ee9f2', 1.4)
    mats['core'] = material('Beam core', '#d3ffff', 3.5)
    mats['halo'] = material('Beam shoulder', '#40bedc', 1.2, .15)
    mats['carrier'] = material('Optical carrier', '#63d6ed', 1.8, .45)
    mats['phase'] = material('Phase insert', '#a380e1', .7)
    return mats


def box(name, center, size, mat, coll, basis=None):
    vertices = list(itertools.product((-.5, .5), repeat=3))
    vertices = [tuple(p[i] * size[i] for i in range(3)) for p in vertices]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1),
             (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(mat)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    coll.objects.link(obj)
    obj.location = center
    if basis:
        obj.rotation_euler = Matrix(basis).transposed().to_euler()
    obj['design_part'] = True
    return obj


def face_box(name, origin, basis, u, v, depth, width, height, thickness, mat, coll):
    center = Vector(origin)
    for direction, distance in zip(basis, (u, v, depth)):
        center += Vector(direction) * distance
    return box(name, center, (width, height, thickness), mat, coll, basis)


def square_ring(name, origin, basis, half, depth, width, thickness, mat, coll):
    for side in (-1, 1):
        face_box(name + ' / horizontal', origin, basis, 0, side * half, depth,
                 2 * half + width, width, thickness, mat, coll)
        face_box(name + ' / vertical', origin, basis, side * half, 0, depth,
                 width, 2 * half - width, thickness, mat, coll)


def tube(name, start, end, radius, mat, coll, sides=16):
    start, end = Vector(start), Vector(end)
    direction = end - start
    bpy.ops.mesh.primitive_cylinder_add(vertices=sides, radius=radius, depth=direction.length,
                                       end_fill_type='NGON', location=(start+end)/2)
    obj = bpy.context.object
    obj.name = name
    obj.rotation_euler = direction.to_track_quat('Z', 'Y').to_euler()
    for old in list(obj.users_collection):
        old.objects.unlink(obj)
    coll.objects.link(obj)
    obj.data.materials.append(mat)
    obj['design_part'] = True
    return obj


def energy_material():
    mat = bpy.data.materials.new('Study / turbulent quantum energy')
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.clear()
    out = nodes.new('ShaderNodeOutputMaterial')
    emission = nodes.new('ShaderNodeEmission')
    emission.inputs['Strength'].default_value = 2.4
    tex = nodes.new('ShaderNodeTexNoise')
    tex.noise_dimensions = '4D'
    tex.inputs['Scale'].default_value = 5.3
    tex.inputs['Detail'].default_value = 3
    tex.inputs['Roughness'].default_value = .65
    tex.inputs['W'].default_value = 0
    tex.inputs['W'].keyframe_insert('default_value', frame=1)
    tex.inputs['W'].default_value = 1.8
    tex.inputs['W'].keyframe_insert('default_value', frame=61)
    tex.inputs['W'].default_value = 0
    tex.inputs['W'].keyframe_insert('default_value', frame=121)
    ramp = nodes.new('ShaderNodeValToRGB')
    stops = [(0, '#242a62'), (.3, '#56469d'), (.46, '#309dbb'),
             (.58, '#88edf2'), (.69, '#edffff')]
    ramp.color_ramp.elements.remove(ramp.color_ramp.elements[1])
    for index, (position, color) in enumerate(stops):
        element = ramp.color_ramp.elements[0] if index == 0 else ramp.color_ramp.elements.new(position)
        element.position = position
        element.color = (*linear(color), 1)
    links.new(tex.outputs['Fac'], ramp.inputs[0])
    links.new(ramp.outputs[0], emission.inputs['Color'])
    links.new(emission.outputs[0], out.inputs['Surface'])
    return mat


def orb(center, radius, coll, mats):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=radius, location=center)
    obj = bpy.context.object
    obj.name = 'Quantum / energy sphere'
    for old in list(obj.users_collection):
        old.objects.unlink(obj)
    coll.objects.link(obj)
    obj.data.materials.append(energy_material())
    for poly in obj.data.polygons:
        poly.use_smooth = True
    obj['design_part'] = True
    # Fine irregular arcs sit on the surface, not mechanical orbit rings.
    for k in range(3):
        curve = bpy.data.curves.new('Surface discharge', 'CURVE')
        curve.dimensions = '3D'
        curve.bevel_depth = .006
        curve.bevel_resolution = 1
        spline = curve.splines.new('POLY')
        spline.points.add(60)
        for i, point in enumerate(spline.points):
            t = i / 60 * math.pi * .7 + k * 2.1
            latitude = .45 * math.sin(t*2+k) + (k-1)*.3 + .018*math.sin(t*46)
            r = radius * 1.018
            p = Vector((r*math.cos(latitude)*math.cos(t), r*math.cos(latitude)*math.sin(t), r*math.sin(latitude)))
            point.co = (*p, 1)
        arc = bpy.data.objects.new('Quantum / surface discharge', curve)
        arc.location = center
        coll.objects.link(arc)
        curve.materials.append(mats['core'])
        arc.rotation_euler.z = 0
        arc.keyframe_insert('rotation_euler', frame=1)
        arc.rotation_euler.z = math.tau
        arc.keyframe_insert('rotation_euler', frame=121)
    data = bpy.data.lights.new('Quantum / local energy light', 'POINT')
    data.energy = 35 * radius
    data.color = (.25, .8, 1)
    data.shadow_soft_size = radius
    light = bpy.data.objects.new(data.name, data)
    light.location = center
    coll.objects.link(light)


def quantum(scene, size, mats, origin=(0, 0, 0), powered=True):
    structure = collection(scene, f'Quantum {size} / editable frame')
    energy = collection(scene, f'Quantum {size} / energy and convergence')
    center = Vector(origin) + Vector((0, 0, size/2))
    h = size/2
    # Twelve rails, with an inset dark stripe and a single ceramic highlight.
    for axis in range(3):
        other = [i for i in range(3) if i != axis]
        for a, b in itertools.product((-1, 1), repeat=2):
            p = center.copy()
            p[other[0]] += a*(h-.14)
            p[other[1]] += b*(h-.14)
            dims = [.25]*3
            dims[axis] = size-.5
            box('Quantum / ceramic edge rail', p, dims, mats['ceramic'], structure)
            for idx, sign in zip(other, (a, b)):
                stripe = p.copy()
                stripe[idx] += sign*.127
                dims2 = [.09]*3
                dims2[axis] = size-.75
                dims2[idx] = .012
                box('Quantum / rail inset', stripe, dims2, mats['seam'], structure)
    for signs in itertools.product((-1, 1), repeat=3):
        p = center + Vector(tuple(sign*(h-.22) for sign in signs))
        box('Quantum / corner anchor', p, (.44,)*3, mats['ceramic'], structure)
        for axis in range(3):
            plate = p.copy()
            plate[axis] += signs[axis]*.224
            dims = [.25]*3
            dims[axis] = .018
            box('Quantum / anchor phase inset', plate, dims, mats['violet_dark'], structure)
            plate[axis] += signs[axis]*.012
            dims = [.125]*3
            dims[axis] = .02
            box('Quantum / anchor phase indicator', plate, dims, mats['phase'], structure)
    aperture = (size-2)/2
    for face, basis in FACES.items():
        square_ring('Quantum / '+face+' socket shadow', center, basis, aperture-.06, h-.13,
                    .18, .16, mats['seam'], structure)
        square_ring('Quantum / '+face+' socket lip', center, basis, aperture-.06, h-.035,
                    .105, .07, mats['ceramic'], structure)
        # Narrow cardinal supports; corners of every face remain open.
        start, stop = aperture+.035, h-.25
        length = stop-start
        for sign in (-1, 1):
            for vertical in (False, True):
                mid = sign*(start+stop)/2
                face_box('Quantum / '+face+' radial support', center, basis,
                         0 if vertical else mid, mid if vertical else 0, h-.15,
                         .125 if vertical else length, length if vertical else .125,
                         .16, mats['ceramic_shadow'], structure)
                face_box('Quantum / '+face+' phase trace', center, basis,
                         0 if vertical else mid, mid if vertical else 0, h-.0625,
                         .045 if vertical else length*.7, length*.7 if vertical else .045,
                         .018, mats['phase'], structure)
            face_box('Quantum / '+face+' cyan socket tab', center, basis,
                     sign*(aperture-.06), 0, h+.005, .09, .22, .035, mats['cyan'], structure)
            face_box('Quantum / '+face+' cyan socket tab', center, basis,
                     0, sign*(aperture-.06), h+.005, .22, .09, .035, mats['cyan'], structure)
        if powered:
            start = center + Vector(basis[2])*h
            tube('Quantum / '+face+' converging beam', start, center, .013*size/3, mats['core'], energy)
            tube('Quantum / '+face+' carrier', start, center, .035*size/3, mats['carrier'], energy)
            tube('Quantum / '+face+' beam shoulder', start, center, .085*size/3, mats['halo'], energy)
            normal = Vector(basis[2])
            for phase in (0, .5):
                pulse = tube('Quantum / '+face+' inward packet', center, center+normal*.14,
                             .037*size/3, mats['core'], energy)
                for frame in range(1, 122):
                    distance = h*(1-((frame-1)/40+phase) % 1)
                    pulse.location = center+normal*distance
                    pulse.keyframe_insert('location', frame=frame)
    if powered:
        orb(center, size*.24, energy, mats)
        energy_glow(scene)
    scene['quantum_size'] = size
    scene['quantum_port_width'] = size-2
    scene['quantum_center'] = list(center)
    scene['concept_note'] = 'Open visual frame; gameplay still requires the existing solid member arrangement.'
    return structure, energy


def energy_glow(scene):
    """Restrained Blender compositor glow, kept separate from the editable geometry."""
    tree = bpy.data.node_groups.new(scene.name+' / energy glow', 'CompositorNodeTree')
    tree.interface.new_socket(name='Image', in_out='OUTPUT', socket_type='NodeSocketColor')
    source = tree.nodes.new('CompositorNodeRLayers')
    source.scene = scene
    glare = tree.nodes.new('CompositorNodeGlare')
    glare.inputs['Type'].default_value = 'Fog Glow'
    glare.inputs['Threshold'].default_value = 1.5
    glare.inputs['Strength'].default_value = .35
    glare.inputs['Size'].default_value = .3
    output = tree.nodes.new('NodeGroupOutput')
    tree.links.new(source.outputs['Image'], glare.inputs['Image'])
    tree.links.new(glare.outputs['Image'], output.inputs['Image'])
    scene.compositing_node_group = tree


def converter(scene, size, mats, origin=(0, 0, 0)):
    coll = collection(scene, f'Converter {size} / editable conversion stack')
    center = Vector(origin) + Vector((0, 0, size/2))
    h, length = size/2, size*2
    box('Converter / recessed sealed body', center, (size-.125, length-.125, size-.125), mats['inset'], coll)
    for x, z in itertools.product((-1, 1), repeat=2):
        p = center + Vector((x*(h-.09), 0, z*(h-.09)))
        box('Converter / longitudinal ceramic rail', p, (.18, length, .18), mats['ceramic'], coll)
    for name in ('right', 'left', 'top', 'bottom'):
        # Local U follows the device axis; V follows its cross-section.
        basis = FACES[name]
        if name in ('top', 'bottom'):
            basis = ((0, 1, 0), (-1, 0, 0) if name == 'top' else (1, 0, 0), basis[2])
        for cell in range(length):
            u = cell-(length-1)/2
            for row in range(size):
                v = row-(size-1)/2
                face_box('Converter / ceramic cell panel', center, basis, u, v, h-.035,
                         .91, .88, .065, mats['ceramic'], coll)
                face_box('Converter / cell recess', center, basis, u, v, h+.002,
                         .70, .46, .016, mats['seam'], coll)
                face_box('Converter / conversion chamber', center, basis, u, v, h+.013,
                         .60, .32, .02, mats['dark'], coll)
                # Three staged vanes express conversion along the axis.
                for j in range(3):
                    face_box('Converter / phase vane', center, basis, u+(j-1)*.18, v, h+.035,
                             .085, .24-.035*j, .055, mats['ceramic_shadow'], coll)
                face_box('Converter / optical channel', center, basis, u, v, h+.067,
                         .64, .065, .028, mats['cyan'], coll)
                for sign in (-1, 1):
                    face_box('Converter / FE contact recess', center, basis, u+sign*.28, v+.325, h+.005,
                             .13, .09, .02, mats['seam'], coll)
                    face_box('Converter / FE contact', center, basis, u+sign*.28, v+.325, h+.018,
                             .07, .045, .015, mats['amber'], coll)
                face_box('Converter / panel registration', center, basis, u-.25, v-.34, h+.004,
                         .19, .035, .012, mats['ceramic_shadow'], coll)
    for face in ('front', 'back'):
        basis = FACES[face]
        depth = length/2
        square_ring('Converter / '+face+' ceramic collar', center, basis, h-.09, depth-.1,
                    .18, .2, mats['ceramic'], coll)
        square_ring('Converter / '+face+' recessed gasket', center, basis, h-.23, depth-.08,
                    .10, .10, mats['seam'], coll)
        face_box('Converter / '+face+' terminal plate', center, basis, 0, 0, depth-.1,
                 max(.5, size-.45), max(.5, size-.45), .10, mats['dark'], coll)
        if face == 'front':
            square_ring('Converter / cyan optical aperture', center, basis, (size-.6)/2, depth-.03,
                        .10, .08, mats['cyan'], coll)
            # A single central optical terminal; no free-space emitter rings.
            face_box('Converter / front optical terminal', center, basis, 0, 0, depth-.015,
                     size*.31, size*.31, .12, mats['cyan'], coll)
            face_box('Converter / front terminal highlight', center, basis, 0, 0, depth+.05,
                     size*.16, size*.16, .015, mats['cyan_light'], coll)
            for sign in (-1, 1):
                face_box('Converter / front alignment lug', center, basis, sign*(h-.09), 0, depth+.01,
                         .17, .23, .06, mats['cyan'], coll)
                face_box('Converter / front alignment lug', center, basis, 0, sign*(h-.09), depth+.01,
                         .23, .17, .06, mats['cyan'], coll)
        else:
            face_box('Converter / rear energy acceptor', center, basis, 0, 0, depth-.02,
                     size*.38, size*.38, .12, mats['ceramic_shadow'], coll)
            face_box('Converter / rear energy socket', center, basis, 0, 0, depth+.045,
                     size*.22, size*.22, .025, mats['seam'], coll)
            for sign in (-1, 1):
                face_box('Converter / rear FE conductor', center, basis, sign*size*.23, 0, depth+.03,
                         .065, size*.38, .045, mats['amber'], coll)
    scene['converter_cross_section'] = size
    scene['converter_length'] = length
    scene['converter_front'] = 'Blender -Y / Minecraft south'
    return coll


def connected_cables(scene, center, size, args, assets, imported_materials, mats):
    import json
    cable_file = args.output / 'cable.json'
    data = json.loads(cable_file.read_text())
    coll = collection(scene, 'Connection demo / production cable shells')
    center = Vector(center)
    for axis in range(3):
        for sign in (-1, 1):
            direction = Vector(tuple(sign if i == axis else 0 for i in range(3)))
            rotation = Vector((1, 0, 0)).rotation_difference(direction)
            for index in range(2):
                pos = center + direction*(size/2+.5+index)
                quads = data['shells']['15']
                obj = mesh_object('Production cable / shell', quads, coll, imported_materials, cutout=True)
                # Import maps Minecraft unit cube to X[0,1], Y[-1,0], Z[0,1].
                for vert in obj.data.vertices:
                    vert.co = rotation @ (vert.co-Vector((.5, -.5, .5)))
                obj.location = pos
            start = center + direction*(size/2+2)
            end = center + direction*(size/2)
            tube('Connection demo / center carrier', start, end, .013, mats['core'], coll)
            tube('Connection demo / optical carrier', start, end, .035, mats['carrier'], coll)
            tube('Connection demo / carrier shoulder', start, end, .085, mats['halo'], coll)
    scene['cable_note'] = 'Shells exported from current Java; internal light is a convergence concept, not runtime beam parity.'


def concepts(args, assets, imported_materials, configure, render):
    mats = palette()
    scenes = []
    for size in (3, 4, 5):
        scene = setup_scene(f'Design / Quantum Cube {size}x{size}x{size}')
        quantum(scene, size, mats)
        configure(scene, args, size*1.85, (0, 0, size/2))
        scene.frame_set(31)
        scenes.append(scene)
        render(scene, args.output / f'quantum-{size}.png')
    for size in (1, 2, 3):
        scene = setup_scene(f'Design / Beam Converter {size}x{size}x{2*size}')
        converter(scene, size, mats)
        configure(scene, args, size*2.75, (0, 0, size/2))
        scenes.append(scene)
        render(scene, args.output / f'converter-{size}.png')
        if size == 2:
            front_camera = scene.camera
            from build_scene import camera
            rear = camera(scene, 'Converter / rear inspection', (7, 10, 7), (0, 0, 1), 5.5)
            scene.camera = rear
            render(scene, args.output / 'converter-rear.png')
            scene.camera = front_camera
    connected = setup_scene('Design / Quantum Cube six-axis convergence')
    quantum(connected, 3, mats, origin=(0, 0, 2))
    connected_cables(connected, (0, 0, 3.5), 3, args, assets, imported_materials, mats)
    configure(connected, args, 10, (0, 0, 3.5))
    connected.frame_set(31)
    scenes.append(connected)
    render(connected, args.output / 'quantum-connected.png')
    # An orthographic view checks that every beam lands exactly on the central sphere.
    old = connected.camera
    from build_scene import camera
    connected.camera = camera(connected, 'Quantum / axial inspection', (0, -15, 3.5), (0, 0, 3.5), 8.2)
    render(connected, args.output / 'quantum-front.png')
    connected.camera = old
    return scenes

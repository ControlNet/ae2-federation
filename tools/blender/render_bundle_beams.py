"""Render the 2x2 middle-beam concepts inside the real shell and the artist's rings, from matched cameras.

Run with Blender's bundled Python. The shell takes its texels from the dense core texture by BundleShellLayout's rule;
the rings are the artist's straight bbmodel on its own texture. Nothing in the mod's assets is written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bundle_beams
from bundle_beams import (LENGTH, LOADS, PROFILES, RING_OPENING, generate, ring_boundaries, ring_quads,
                          shell_tiles)
from build_scene import camera

# View: (camera direction from the run's middle, in Blender axes; ortho scale; width; height; loads; open end)
VIEWS = {
    'iso': ((-.55, -1, .8), 6.4, 1200, 760, ('idle', 'medium', 'full'), False),
    # Eye level, square to the run, as a player standing beside the cable sees it.
    'level': ((0, -1, 0), 6.6, 1500, 540, ('idle', 'medium', 'full'), False),
    'end': ((1, 0, 0), 2.5, 760, 760, ('medium',), True),
}
CYCLE = .25
FRAMES = 32
# With --soft, each glow layer fades out toward its outline, as a volume of light thins at its edge.
SOFT = False


def linear(value):
    return value / 12.92 if value <= .04045 else ((value + .055) / 1.055) ** 2.4


def to_blender(point):
    x, y, z = point
    return x, -z, y


def nodes_of(material):
    material.use_nodes = True
    tree = material.node_tree
    tree.nodes.clear()
    return tree.nodes, tree.links


def beam_material(name, rgb, alpha, mode):
    material = bpy.data.materials.new('Bundle beam / ' + name)
    nodes, links = nodes_of(material)
    emission = nodes.new('ShaderNodeEmission')
    emission.inputs['Color'].default_value = (*(linear(c) for c in rgb), 1)
    transparent = nodes.new('ShaderNodeBsdfTransparent')
    out = nodes.new('ShaderNodeOutputMaterial')
    if mode == 'add':
        # Additive like the game's glow pass: the light adds, nothing behind is hidden.
        emission.inputs['Strength'].default_value = alpha
        if SOFT:
            # Facing is 0 where the surface faces the camera and 1 at the outline.
            weight = nodes.new('ShaderNodeLayerWeight')
            inverse = nodes.new('ShaderNodeMath')
            inverse.operation = 'SUBTRACT'
            inverse.inputs[0].default_value = 1
            links.new(weight.outputs['Facing'], inverse.inputs[1])
            fade = nodes.new('ShaderNodeMath')
            fade.operation = 'POWER'
            fade.inputs[1].default_value = 2
            links.new(inverse.outputs[0], fade.inputs[0])
            scaled = nodes.new('ShaderNodeMath')
            scaled.operation = 'MULTIPLY'
            # Twice the light at the middle, so the layer gives about as much in all as a flat one.
            scaled.inputs[1].default_value = 2 * alpha
            links.new(fade.outputs[0], scaled.inputs[0])
            links.new(scaled.outputs[0], emission.inputs['Strength'])
        add = nodes.new('ShaderNodeAddShader')
        links.new(transparent.outputs[0], add.inputs[0])
        links.new(emission.outputs[0], add.inputs[1])
        links.new(add.outputs[0], out.inputs['Surface'])
    else:
        emission.inputs['Strength'].default_value = 1
        mix = nodes.new('ShaderNodeMixShader')
        mix.inputs[0].default_value = alpha
        links.new(transparent.outputs[0], mix.inputs[1])
        links.new(emission.outputs[0], mix.inputs[2])
        links.new(mix.outputs[0], out.inputs['Surface'])
    material.surface_render_method = 'BLENDED'
    return material


def lit_material(name, rgba):
    """A block surface under the scene's lights; alpha below 1 lets the beam show through, as the glass does."""
    material = bpy.data.materials.new('Bundle shell / ' + name)
    nodes, links = nodes_of(material)
    shader = nodes.new('ShaderNodeBsdfPrincipled')
    shader.inputs['Base Color'].default_value = (*(linear(c) for c in rgba[:3]), 1)
    shader.inputs['Roughness'].default_value = .85
    out = nodes.new('ShaderNodeOutputMaterial')
    if rgba[3] < 1:
        transparent = nodes.new('ShaderNodeBsdfTransparent')
        mix = nodes.new('ShaderNodeMixShader')
        mix.inputs[0].default_value = rgba[3]
        links.new(transparent.outputs[0], mix.inputs[1])
        links.new(shader.outputs[0], mix.inputs[2])
        links.new(mix.outputs[0], out.inputs['Surface'])
    else:
        links.new(shader.outputs[0], out.inputs['Surface'])
    return material


def ring_material(image):
    material = bpy.data.materials.new('Bundle ring / large_cable')
    nodes, links = nodes_of(material)
    texture = nodes.new('ShaderNodeTexImage')
    texture.image = image
    texture.interpolation = 'Closest'
    shader = nodes.new('ShaderNodeBsdfPrincipled')
    shader.inputs['Roughness'].default_value = .8
    links.new(texture.outputs['Color'], shader.inputs['Base Color'])
    links.new(texture.outputs['Alpha'], shader.inputs['Alpha'])
    out = nodes.new('ShaderNodeOutputMaterial')
    links.new(shader.outputs[0], out.inputs['Surface'])
    return material


def build_object(name, faces, collection, materials, indices, uvs=None):
    vertices = []
    polygons = []
    for points in faces:
        first = len(vertices)
        vertices.extend(to_blender(p) for p in points)
        polygons.append(tuple(range(first, first + len(points))))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], polygons)
    mesh.update()
    for material in materials:
        mesh.materials.append(material)
    for polygon, index in zip(mesh.polygons, indices):
        polygon.material_index = index
    if uvs is not None:
        layer = mesh.uv_layers.new(name='MinecraftUV')
        for polygon, corners in zip(mesh.polygons, uvs):
            for loop, (u, v) in zip(polygon.loop_indices, corners):
                layer.data[loop].uv = (u, 1 - v)
    obj = bpy.data.objects.new(name, mesh)
    collection.objects.link(obj)
    return obj


def shell(collection, image, open_end):
    pixels = list(image.pixels)
    width, height = image.size

    def texel(u, v):
        # Blender stores rows bottom-up; texture rows count from the top.
        at = ((height - 1 - v) * width + u) * 4
        return tuple(round(c * 255) / 255 for c in pixels[at:at + 4])

    tiles = shell_tiles(open_end=open_end)
    keys = sorted({t for _, t in tiles})
    materials = [lit_material('texel %d,%d' % key, texel(*key)) for key in keys]
    return build_object('Shell' + (' / cutaway' if open_end else ''), [q for q, _ in tiles], collection, materials,
                        [keys.index(t) for _, t in tiles])


def bore_marker(collection):
    """A thin orange circle at the cut, the bore the ring's flanges leave."""
    import math
    points, faces = [], []
    sides, r, w = 64, RING_OPENING, .008
    for i in range(sides):
        a = math.tau * i / sides
        for radius in (r, r + w):
            points.append(to_blender((LENGTH + .02, 1 + radius * math.cos(a), 1 + radius * math.sin(a))))
    for i in range(sides):
        j = (i + 1) % sides
        faces.append((2 * i, 2 * j, 2 * j + 1, 2 * i + 1))
    mesh = bpy.data.meshes.new('Ring bore marker')
    mesh.from_pydata(points, [], faces)
    mesh.materials.append(beam_material('bore marker', (1, .55, .1), 1, 'solid'))
    obj = bpy.data.objects.new('Ring bore marker', mesh)
    collection.objects.link(obj)
    return obj


def scene_setup():
    scene = bpy.data.scenes.new('Bundle Beam Concepts')
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 40
    scene.cycles.use_denoising = True
    scene.cycles.transparent_max_bounces = 48
    scene.cycles.seed = 41
    scene.render.resolution_percentage = 100
    scene.view_settings.view_transform = 'Standard'
    scene.world = bpy.data.worlds.new('Bundle Beam World')
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes['Background']
    background.inputs[0].default_value = (0.08, 0.11, 0.16, 1)
    background.inputs[1].default_value = 0.45
    stage = bpy.data.meshes.new('Stage')
    stage.from_pydata([(-20, -20, -0.02), (26, -20, -0.02), (26, 20, -0.02), (-20, 20, -0.02)], [], [(0, 1, 2, 3)])
    stage.materials.append(lit_material('stage', (0.025, 0.038, 0.052, 1)))
    scene.collection.objects.link(bpy.data.objects.new('Stage', stage))
    for title, position, power, size in [('Key', (-2, -7, 8), 900, 6), ('Fill', (8, 4, 6), 600, 5)]:
        light = bpy.data.lights.new(title, 'AREA')
        light.energy = power
        light.shape = 'DISK'
        light.size = size
        obj = bpy.data.objects.new(title, light)
        scene.collection.objects.link(obj)
        obj.location = position
        obj.rotation_euler = (Vector((3, -1, 1)) - obj.location).to_track_quat('-Z', 'Y').to_euler()
    return scene


def animate(scene, profiles, output, kept):
    """One 64-tick loop (3.2 s) per concept at 60% load from the eye-level camera, as numbered frames."""
    for obj in kept.objects:
        obj.hide_render = True
    scene.render.resolution_x, scene.render.resolution_y = 1200, 432
    scene.cycles.samples = 16
    for profile in profiles:
        title = PROFILES[profile][0]
        frames = output / profile / 'frames'
        frames.mkdir(parents=True, exist_ok=True)
        first = generate(profile, LOADS['medium'], 0)
        materials = [beam_material('%s loop %s' % (profile, m[0]), *m[1:]) for m in first.materials]
        for index in range(FRAMES):
            mesh = generate(profile, LOADS['medium'], index / FRAMES)
            assert mesh.faces == first.faces and mesh.materials == first.materials, (profile, index)
            beam = build_object('%s / loop' % title, [[mesh.vertices[i] for i in f] for f in mesh.faces],
                                scene.collection, materials, mesh.indices)
            scene.render.filepath = str(frames / ('%03d.png' % index))
            bpy.ops.render.render(write_still=True, scene=scene.name)
            data = beam.data
            bpy.data.objects.remove(beam)
            bpy.data.meshes.remove(data)
        print('BUNDLE_BEAM_ANIMATED %s' % profile, flush=True)


def main(args):
    global SOFT
    SOFT = args.soft
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    asset_root = bundle_beams.ASSETS

    def asset_hashes():
        return {str(p.relative_to(asset_root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in asset_root.rglob('*') if p.is_file()}
    before = asset_hashes()
    profiles = [p for p in PROFILES if not args.only or p in args.only]

    scene = scene_setup()
    fixed = bpy.data.collections.new('Shell and rings')
    scene.collection.children.link(fixed)
    core_image = bpy.data.images.load(str(bundle_beams.CORE_TEXTURE))
    ring_image = bpy.data.images.load(str(bundle_beams.RING_TEXTURE))
    closed = shell(fixed, core_image, False)
    cutaway = shell(fixed, core_image, True)
    quads = ring_quads(ring_boundaries())
    build_object('Rings', [q for q, _ in quads], fixed, [ring_material(ring_image)], [0] * len(quads),
                 [uv for _, uv in quads])
    marker = bore_marker(fixed)

    middle = Vector(to_blender((LENGTH / 2, 1, 1)))
    cameras = {}
    for key, (direction, scale, *_rest) in VIEWS.items():
        position = middle + Vector(direction).normalized() * 14
        if key == 'end':
            position = Vector(to_blender((LENGTH + 8, 1, 1)))
        cameras[key] = camera(scene, 'Camera / ' + key, tuple(position), tuple(middle if key != 'end'
                                                                                else to_blender((0, 1, 1))), scale)

    report = {'cycle': CYCLE, 'soft': SOFT, 'animation_frames': FRAMES if args.animate else 0, 'loads': LOADS, 'ring_opening': RING_OPENING, 'views': {}, 'profiles': {}}
    kept = bpy.data.collections.new('Beam concepts at 60% load')
    scene.collection.children.link(kept)
    for profile in profiles:
        title, description = PROFILES[profile]
        report['profiles'][profile] = {'title': title, 'description': description, 'loads': {}}
        for load_name, load in LOADS.items():
            views = [v for v, spec in VIEWS.items() if load_name in spec[4] and (not args.views or v in args.views)]
            if not views:
                continue
            mesh = generate(profile, load, CYCLE)
            materials = [beam_material('%s %s %s' % (profile, load_name, m[0]), *m[1:]) for m in mesh.materials]
            beam = build_object('%s / %s' % (title, load_name), [[mesh.vertices[i] for i in f] for f in mesh.faces],
                                scene.collection, materials, mesh.indices)
            report['profiles'][profile]['loads'][load_name] = {
                'polygons': len(mesh.faces), 'solid_reach': round(bundle_beams.solid_reach(mesh), 4),
                'materials': [list(m) for m in mesh.materials]}
            for view in views:
                _, _, width, height, _, open_end = VIEWS[view]
                closed.hide_render = open_end
                cutaway.hide_render = not open_end
                marker.hide_render = not open_end
                scene.camera = cameras[view]
                scene.render.resolution_x = width
                scene.render.resolution_y = height
                scene.render.filepath = str(output / ('%s-%s-%s.png' % (profile, load_name, view)))
                bpy.ops.render.render(write_still=True, scene=scene.name)
                print('BUNDLE_BEAM_RENDERED %s %s %s' % (profile, load_name, view), flush=True)
            if load_name == 'medium':
                scene.collection.objects.unlink(beam)
                kept.objects.link(beam)
                beam.hide_render = beam.hide_viewport = profile != 'base'
            else:
                bpy.data.objects.remove(beam)
    closed.hide_render = False
    cutaway.hide_render = marker.hide_render = True
    if args.animate:
        scene.camera = cameras['level']
        animate(scene, profiles, output, kept)
    scene.camera = cameras['iso']
    bpy.ops.wm.save_as_mainfile(filepath=str(output / 'bundle-beams.blend'))
    assert before == asset_hashes(), 'Production assets were modified'
    report['views'] = {k: {'scale': v[1], 'size': v[2:4], 'loads': v[4], 'cutaway': v[5]} for k, v in VIEWS.items()
                       if not args.views or k in args.views}
    (output / 'bundle-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('BUNDLE_BEAMS_OK: %d concepts rendered; assets unchanged' % len(profiles), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=HERE.parents[1] / 'build/bundle-beam-check')
    parser.add_argument('--only', nargs='*', help='render only these concept keys')
    parser.add_argument('--soft', action='store_true', help='fade each glow layer toward its outline')
    parser.add_argument('--views', nargs='*', choices=tuple(VIEWS), help='render only these still views')
    parser.add_argument('--animate', action='store_true', help='also render one loop per concept at 60%% load')
    main(parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []))

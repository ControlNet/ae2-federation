"""Verify reopened UV artwork, source teal, fragmented layers and cable alignment."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE))
from verify_device_alternatives import core_radius


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--animate',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    out=Path(bpy.data.filepath).parent;manifest=json.loads((out/'manifest.json').read_text())
    for group in ('production_sha256','cable_sources'):
        for name,digest in manifest[group].items():
            assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    for name,spec in manifest['painted_textures']['textures'].items():
        assert hashlib.sha256((out/'textures'/(name+'.png')).read_bytes()).hexdigest()==spec['sha256'],name
    checked_faces=0;connections=[];core_scenes=0
    for name in manifest['scenes']:
        scene=bpy.data.scenes[name];bpy.context.window.scene=scene;bpy.context.view_layer.update()
        for obj in scene.objects:
            if obj.type!='MESH':continue
            assert all(math.isfinite(c) for v in obj.data.vertices for c in v.co),obj.name
            assert all(p.area>1e-10 for p in obj.data.polygons),obj.name
            if not obj.get('mechanical_texture'):continue
            uv=obj.data.uv_layers['PixelUV']
            for poly in obj.data.polygons:
                mat=obj.data.materials[poly.material_index];w,h=mat['texture_pixels']
                coords=[uv.data[i].uv for i in poly.loop_indices]
                assert abs(max(c.x for c in coords)-min(c.x for c in coords)-1)<1e-6
                assert abs(max(c.y for c in coords)-min(c.y for c in coords)-1)<1e-6
                assert abs(w*h/poly.area-256)<.01,(obj.name,w,h,poly.area)
                tex=next(n for n in mat.node_tree.nodes if n.type=='TEX_IMAGE' and n.image.colorspace_settings.name!='Non-Color')
                assert tex.interpolation=='Closest'
                assert tex.inputs['Vector'].links[0].from_node.type=='TEX_COORD'
                assert tex.inputs['Vector'].links[0].from_socket.name=='UV'
                checked_faces+=1
        outer=[o for o in scene.objects if o.get('fragment_layer')=='outer']
        inner=[o for o in scene.objects if o.get('fragment_layer')=='inner']
        if outer or inner:
            assert len(outer)==22 and len(inner)==12,(name,len(outer),len(inner))
            center=Vector(scene['core_center'])
            for obj in outer+inner:assert (obj.parent.location-center).length<1e-6
            root=outer[0].parent
            scene.frame_set(1);initial=root.rotation_euler.z
            scene.frame_set(31);assert abs(root.rotation_euler.z-initial)>.05
            scene.frame_set(19);core_scenes+=1
        if name.startswith('Connected /'):
            refs=[o for o in scene.objects if o.name.startswith('Cable / current R light adapter')]
            internal=[o for o in scene.objects if o.name.startswith('Laser / carrier from cable interface') or o.name.startswith('Quantum / cable convergence')]
            assert len(refs)==len(internal)==(1 if scene['device']=='laser' else 2)
            radii=[core_radius(o) for o in refs+internal]
            assert max(radii)-min(radii)<1e-5
            if scene['device']=='quantum':
                for obj in internal:
                    end=Vector(obj['beam_origin'])+Vector(obj['beam_direction'])*obj['beam_length']
                    assert (end-Vector(scene['core_center'])).length<1e-6
            connections.append({'scene':name,'core_radius':radii[0]})
    for image in bpy.data.images:
        if image.source=='FILE':assert image.packed_file,image.name
    for mat in bpy.data.materials:
        if 'device_light_rgb' in mat:assert list(mat['device_light_rgb']) in ([108,174,187],[162,220,226])
    assert checked_faces>1000
    assert len(connections)==6
    report={'scenes':len(manifest['scenes']),'mechanical_faces_at_16_pixels_per_block':checked_faces,
            'fragmented_core_scenes':core_scenes,'connection_checks':connections,
            'production_unchanged':True,'packed_images':True}
    (out/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print('REFINED_VERIFY_OK',checked_faces,'UV faces; 34 fragments per core; 6 cable cases')
    if args.animate:
        folder=out/'core-animation';folder.mkdir(exist_ok=False)
        scene=bpy.data.scenes['Refined Quantum / core detail'];bpy.context.window.scene=scene
        scene.render.resolution_x=scene.render.resolution_y=720;scene.cycles.samples=32
        prefs=bpy.context.preferences.addons['cycles'].preferences;prefs.compute_device_type='OPTIX';prefs.get_devices()
        for device in prefs.devices:device.use=device.type=='OPTIX'
        scene.cycles.device='GPU' if any(d.use for d in prefs.devices) else 'CPU'
        for index,frame in enumerate(range(1,121,4)):
            scene.frame_set(frame);scene.render.filepath=str(folder/f'{index:03d}.png');bpy.ops.render.render(write_still=True)
        assert (folder/'000.png').read_bytes()!=(folder/'010.png').read_bytes()
        print('FRAGMENT_CORE_ANIMATION_OK',folder)


if __name__=='__main__':main()

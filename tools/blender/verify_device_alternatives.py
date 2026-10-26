"""Inspect reopened proposal scenes and their actual cable contact geometry/materials."""
import hashlib
import json
import math
from pathlib import Path

import bpy
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[2]


def core_radius(obj):
    origin=Vector(obj['beam_origin']);axis=Vector(obj['beam_direction']).normalized()
    indices={i for p in obj.data.polygons if p.material_index in obj['beam_core_slots'] for i in p.vertices}
    points=[obj.matrix_world@obj.data.vertices[i].co for i in indices]
    at_contact=[p-origin for p in points if abs((p-origin).dot(axis))<1e-5]
    assert at_contact, obj.name
    return max((v-axis*v.dot(axis)).length for v in at_contact)


def main():
    output=Path(bpy.data.filepath).parent
    manifest=json.loads((output/'manifest.json').read_text())
    for group in ('production_sha256','cable_java','ring_projects'):
        for name,digest in manifest[group].items():
            assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
    rows=[]
    for name in manifest['scenes']:
        scene=bpy.data.scenes[name];bpy.context.window.scene=scene;bpy.context.view_layer.update()
        assert scene.camera
        for obj in scene.objects:
            if obj.type=='MESH':
                assert all(math.isfinite(c) for v in obj.data.vertices for c in v.co),obj.name
                assert all(p.area>1e-12 for p in obj.data.polygons),obj.name
        n=scene['section'];key=scene['candidate']
        is_connection=name.startswith('Connection /')
        refs=[o for o in scene.objects if o.name.startswith('Cable / current R light adapter')]
        internal=[o for o in scene.objects if 'continuous cable beam' in o.name]
        if scene['device_kind']=='quantum':
            assert list(scene['body_dimensions'])==[n+2]*3
            expected=2 if is_connection else 0
            assert len(refs)==len(internal)==expected,(name,len(refs),len(internal))
            assert not any('radial support' in o.name or 'socket lip' in o.name for o in scene.objects)
            has_glass=any(o.name.startswith('Quantum / cable-matched glass') for o in scene.objects)
            assert has_glass==(key!='Q1')
            center=Vector(scene['optical_center'])
            for obj in internal:
                end=Vector(obj['beam_origin'])+Vector(obj['beam_direction'])*obj['beam_length']
                assert (end-center).length<1e-6,(obj.name,end,center)
        else:
            assert list(scene['body_dimensions'])==[n,2*n,n]
            assert len(refs)==int(is_connection)
            assert len(internal)==1
            assert Vector(internal[0]['beam_origin'])==Vector(scene['optical_center'])
            assert any(o.name.startswith('Terminal /') for o in scene.objects)==is_connection
        if is_connection:
            radii=[core_radius(o) for o in refs+internal]
            assert max(radii)-min(radii)<1e-5,(name,radii)
            if n==1:
                first=[o for o in scene.objects if o.get('connection_bits')==7]
                assert len(first)==len(refs)
                # The machine end really narrows to a 10/16-block dense arm.
                for obj in first:
                    verts=[v.co for v in obj.data.vertices]
                    extent=[max(v[i] for v in verts)-min(v[i] for v in verts) for i in range(3)]
                    axis=max(range(3),key=lambda i:extent[i])
                    contact=[v for v in verts if abs(v[axis])<1e-6]
                    assert len(contact)>=4
                    for across in set(range(3))-{axis}:
                        width=max(v[across] for v in contact)-min(v[across] for v in contact)
                        assert abs(width-10/16)<1e-6,(name,width)
            else:
                rings=[o for o in scene.objects if o.name.startswith('Cable / original artist bundle ring')]
                arms=[o for o in scene.objects if o.name.startswith('Cable / bundle member dense contact')]
                assert len(rings)==len(refs)
                assert len(arms)==n*n*len(refs)
            rows.append({'scene':name,'section':n,'beam_core_radius':radii[0],
                         'matched_beam_radii':True,'cable_runs':len(refs)})
    for image in bpy.data.images:
        if image.source=='FILE':assert image.packed_file,image.name
    for mat in bpy.data.materials:
        if 'source_texel' not in mat:continue
        samples=[node for node in mat.node_tree.nodes if node.type=='TEX_IMAGE']
        assert samples
        for node in samples:
            assert node.inputs['Vector'].links[0].from_node.bl_idname=='ShaderNodeCombineXYZ'
            u,v=mat['source_texel'];w,h=node.image.size
            alpha=node.image.pixels[((h-1-v)*w+u)*4+3]
            assert abs(alpha-(75/255 if 'glass' in mat.name.lower() else 1))<1e-5,(mat.name,alpha)
    assert len(rows)==3*len(manifest['concepts'])
    report={'production_unchanged':True,'scenes':len(manifest['scenes']),
            'connection_checks':rows,'material_sampling':'packed original texture texels; glass alpha 75/255'}
    (output/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
    print('DEVICE_ALTERNATIVES_VERIFY_OK',len(manifest['scenes']),'scenes,',len(rows),'connection checks')


if __name__=='__main__':main()

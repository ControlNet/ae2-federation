"""Export the reviewed Blender meshes/UVs to resource-pack-readable Minecraft mesh data.

Coordinates are centred, Minecraft Y-up, laser muzzle toward +Z. Animated layers retain
parent-local vertices; the renderer applies their tilt and counterrotation independently.
"""
import json
from pathlib import Path
import sys
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'common/src/main/resources/assets/ae2federation/device_meshes'

def main():
    source = Path(bpy.data.filepath).parent
    atlas = json.loads((source / 'runtime-atlas.json').read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    def export(name, scene, objects, center, local=False):
        bpy.context.window.scene = scene
        scene.frame_set(1)
        deps = bpy.context.evaluated_depsgraph_get()
        quads = []
        for obj in objects:
            evaluated = obj.evaluated_get(deps)
            mesh = evaluated.to_mesh()
            if mesh is None:
                continue
            try:
                uv = mesh.uv_layers.active
                # Blender's polygon order is preserved by the right-handed (x,z,-y) conversion.
                for face in mesh.polygons:
                    mat = mesh.materials[face.material_index]
                    if not mat.get('painted_texture') and not mat.name.startswith('Energy / source teal'):
                        raise ValueError('Unexpected non-device material: '+mat.name)
                    tile = mat.get('painted_texture', 'teal-light')
                    tx, ty, w, h = atlas['tiles'][tile]
                    points = []
                    for loop in face.loop_indices:
                        p = mesh.vertices[mesh.loops[loop].vertex_index].co
                        p = (obj.matrix_local @ p) if local else (obj.matrix_world @ p - Vector(center))
                        st = uv.data[loop].uv if uv else (.5, .5)
                        points.append([round(v, 7) for v in (p.x, p.z, -p.y,
                            (tx+max(0,min(1,st[0]))*w)/atlas['size'],
                            (ty+(1-max(0,min(1,st[1])))*h)/atlas['size'])])
                    # Triangles use a duplicated final vertex in Minecraft's quad stream.
                    polygons = [points] if len(points)==4 else [[points[0],points[i],points[i+1]] for i in range(1,len(points)-1)]
                    for poly in polygons:
                        if len(poly)==3: poly.append(poly[-1])
                        quads.append(poly)
            finally:
                evaluated.to_mesh_clear()
        (OUT / (name+'.json')).write_text(json.dumps({'quads':quads},separators=(',',':'))+'\n')
        print(name, len(quads))
    for n in (1,2,3):
        for device in ('laser','quantum'):
            scene=bpy.data.scenes[f'Connected / {device} / {n}']
            center=(0,0,n/2 if device=='laser' else (n+2)/2)
            export(f'{device}-{n}',scene,[o for o in scene.objects if o.get('mechanical_texture')],center)
    scene=bpy.data.scenes['Refined Quantum / core detail']
    for layer in ('outer','inner'):
        root=next(o for o in scene.objects if o.name.startswith('Core / '+layer+' rotation'))
        export('core-'+layer,scene,list(root.children),(0,0,0),True)
    export('core-centre',scene,[o for o in scene.objects if o.type=='MESH' and not o.parent and o.name.startswith('Core / ')],(0,0,1.15))
    print('DEVICE_MESH_EXPORT_OK')

if __name__=='__main__':
    main()

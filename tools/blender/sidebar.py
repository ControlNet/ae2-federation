"""Session-local Blender sidebar. Loaded by launch.sh; no global add-on installation."""
import argparse
import importlib
import json
from pathlib import Path
import subprocess
import sys

import bpy

HERE=Path(__file__).resolve().parent
if str(HERE) not in sys.path: sys.path.insert(0,str(HERE))


class FEDERATION_OT_reload_assets(bpy.types.Operator):
    bl_idname='federation.reload_assets'
    bl_label='Reload Assets and Cable Geometry'
    bl_description='Rebuild generated scenes from disk. Edits inside generated scenes will be replaced; save a copy to keep them'

    def invoke(self,context,event):
        return context.window_manager.invoke_confirm(self,event)

    def execute(self,context):
        repo=Path(context.scene.get('ae2f_repo',str(HERE.parents[1])))
        output=Path(context.scene.get('ae2f_output',str(HERE/'build')))
        packs=[Path(p) for p in json.loads(context.scene.get('ae2f_resource_packs','[]'))]
        subprocess.run(['python3',str(HERE/'export_cable.py'),'--repo',str(repo),'--output',str(output/'cable.json')],check=True)
        for name in ('assets','materials','build_scene'):
            if name in sys.modules: importlib.reload(sys.modules[name])
        import build_scene
        build_scene.build(argparse.Namespace(repo=repo,output=output,resource_pack=packs,render=False,replace=True))
        self.report({'INFO'},'Reloaded current assets and production cable geometry')
        return {'FINISHED'}


class FEDERATION_PT_assets(bpy.types.Panel):
    bl_label='Federation Asset Preview'
    bl_idname='FEDERATION_PT_assets'
    bl_space_type='VIEW_3D'
    bl_region_type='UI'
    bl_category='Federation'

    def draw(self,context):
        layout=self.layout
        layout.label(text='Read current repository assets')
        layout.operator('federation.reload_assets',icon='FILE_REFRESH')
        layout.separator()
        layout.label(text='Space: play flow animation')
        layout.label(text='Scene menu: gallery / atlas / flow')
        layout.label(text='Reload replaces generated scenes.')
        layout.label(text='Save a copy to keep manual edits.')


for cls in (FEDERATION_OT_reload_assets,FEDERATION_PT_assets):
    previous=getattr(bpy.types,cls.__name__,None)
    if previous: bpy.utils.unregister_class(previous)
    bpy.utils.register_class(cls)

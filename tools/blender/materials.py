"""Blender materials for nearest-sampled Minecraft textures and Java flow phases."""
import bpy
from assets import animation_schedule


class Materials:
    def __init__(self, assets, phases):
        self.assets, self.phases, self.cache = assets, phases, {}
        self.images = {}

    def load_image(self, texture):
        if texture not in self.images:
            path, animation = self.assets.texture(texture)
            # Fresh datablocks ensure a reload reads changed PNG bytes, including packed images.
            image = bpy.data.images.load(str(path), check_existing=False)
            image.pack()
            image['ae2f_generated'] = True
            self.images[texture] = (image, animation)
        return self.images[texture]

    def get(self, texture, *, flow=False, phase=None, cutout=False):
        key = (texture,flow,phase,cutout)
        if key in self.cache:
            return self.cache[key]
        image, animation = self.load_image(texture)
        material = bpy.data.materials.new(texture + (f' / phase {phase}' if phase is not None else ''))
        material['ae2f_generated'] = True
        material.use_nodes = True
        material.surface_render_method = 'DITHERED'
        nodes = material.node_tree.nodes; nodes.clear()
        links = material.node_tree.links
        texcoord = nodes.new('ShaderNodeTexCoord')
        vector = texcoord.outputs['UV']
        if phase is not None:
            shift = nodes.new('ShaderNodeVectorMath'); shift.operation = 'ADD'
            shift.label = 'Phase samples from the tools\' CableFlowGeometry.phase'
            links.new(vector, shift.inputs[0])
            for frame, values in enumerate(self.phases, 1):
                shift.inputs[1].default_value = (values[phase],0,0)
                shift.inputs[1].keyframe_insert('default_value', frame=frame)
            vector = shift.outputs['Vector']
        def sample(offset=None):
            node = nodes.new('ShaderNodeTexImage'); node.image = image
            node.interpolation = 'Closest'; node.extension = 'REPEAT'
            links.new(vector if offset is None else offset, node.inputs['Vector'])
            return node
        tex = sample()
        color, alpha = tex.outputs['Color'],tex.outputs['Alpha']
        if animation:
            nodes.remove(tex)
            fw,fh,schedule = animation_schedule(animation,*image.size)
            columns,rows = image.size[0]//fw,image.size[1]//fh
            scale = nodes.new('ShaderNodeVectorMath'); scale.operation='MULTIPLY'
            links.new(vector,scale.inputs[0]); scale.inputs[1].default_value=(1/columns,1/rows,1)
            offsets = []
            samples = []
            for _ in range(2):
                offset = nodes.new('ShaderNodeVectorMath'); offset.operation='ADD'
                links.new(scale.outputs['Vector'],offset.inputs[0]); offsets.append(offset)
                samples.append(sample(offset.outputs['Vector']))
            mix = nodes.new('ShaderNodeMixRGB'); mix.blend_type='MIX'
            links.new(samples[0].outputs['Color'],mix.inputs[1]); links.new(samples[1].outputs['Color'],mix.inputs[2])
            # Minecraft interpolates RGB between frames while retaining the current frame's alpha.
            color,alpha = mix.outputs[0],samples[0].outputs['Alpha']
            total = sum(d for _,d in schedule)
            for frame in range(1,242):
                tick = ((frame-1)*20/30)%total
                for i,(index,duration) in enumerate(schedule):
                    if tick < duration:
                        following = schedule[(i+1)%len(schedule)][0]
                        for offset,idx in zip(offsets,(index,following)):
                            offset.inputs[1].default_value=(idx%columns/columns,1-(idx//columns+1)/rows,0)
                            offset.inputs[1].keyframe_insert('default_value',frame=frame)
                        mix.inputs[0].default_value=tick/duration if animation.get('interpolate',False) else 0
                        mix.inputs[0].keyframe_insert('default_value',frame=frame)
                        break
                    tick -= duration
        if cutout:
            threshold=nodes.new('ShaderNodeMath'); threshold.operation='GREATER_THAN'
            links.new(alpha,threshold.inputs[0]); threshold.inputs[1].default_value=0.1
            alpha=threshold.outputs[0]
        if flow:
            # Minecraft's emissive entity fragment shader discards texture alpha < 0.1
            # before multiplying by vertex alpha. Preserve fractional alpha above the cutoff.
            discarded=nodes.new('ShaderNodeMath'); discarded.operation='LESS_THAN'
            discarded.label='Minecraft texture alpha discard (< 0.1)'
            links.new(alpha,discarded.inputs[0]); discarded.inputs[1].default_value=0.1
            keep=nodes.new('ShaderNodeMath'); keep.operation='SUBTRACT'
            keep.inputs[0].default_value=1; links.new(discarded.outputs[0],keep.inputs[1])
            filtered=nodes.new('ShaderNodeMath'); filtered.operation='MULTIPLY'
            links.new(alpha,filtered.inputs[0]); links.new(keep.outputs[0],filtered.inputs[1])
            alpha=filtered.outputs[0]
            attr=nodes.new('ShaderNodeVertexColor'); attr.layer_name='FlowAlpha'
            multiply=nodes.new('ShaderNodeMath'); multiply.operation='MULTIPLY'
            links.new(alpha,multiply.inputs[0]); links.new(attr.outputs['Alpha'],multiply.inputs[1]); alpha=multiply.outputs[0]
            shader=nodes.new('ShaderNodeEmission'); links.new(color,shader.inputs['Color'])
            shader.inputs['Strength'].default_value=1
        else:
            shader=nodes.new('ShaderNodeBsdfPrincipled'); links.new(color,shader.inputs['Base Color'])
            shader.inputs['Roughness'].default_value=0.7
        transparent=nodes.new('ShaderNodeBsdfTransparent')
        mix_shader=nodes.new('ShaderNodeMixShader')
        links.new(alpha,mix_shader.inputs[0]); links.new(transparent.outputs[0],mix_shader.inputs[1]); links.new(shader.outputs[0],mix_shader.inputs[2])
        output=nodes.new('ShaderNodeOutputMaterial'); links.new(mix_shader.outputs[0],output.inputs['Surface'])
        # Every frame is explicitly sampled; constant keys avoid interpolation across atlas wrap boundaries.
        if material.node_tree.animation_data:
            action=material.node_tree.animation_data.action
            for layer in action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        for curve in bag.fcurves:
                            for point in curve.keyframe_points: point.interpolation='CONSTANT'
        self.cache[key]=material
        return material

"""Create a repeatable, isolated in-game visual acceptance scene; never packaged in the mod."""
from pathlib import Path
import argparse
import json

parser = argparse.ArgumentParser()
parser.add_argument('world', type=Path, help='Disposable test world directory; writes only its visual_scene datapack')
args = parser.parse_args()
pack = args.world / 'datapacks/visual_scene'
function = pack / 'data/visual_scene/function'
function.mkdir(parents=True, exist_ok=True)
(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'AE2 Federation visual acceptance scene'}}))
commands = ['forceload add -8 -12 65 48', 'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
            'time set noon', 'weather clear', 'fill -8 0 -14 65 0 48 minecraft:smooth_stone',
            'fill -8 1 -12 65 5 48 minecraft:air', 'gamemode creative @a']
def block(x,y,z,name):
    commands.append(f'setblock {x} {y} {z} {name}')
for i,name in enumerate(['router','pattern_provider[facing=south]','processing_endpoint','cable']):
    block(i*3,1,0,'minecraft:polished_deepslate')
    block(i*3,2,0,'ae2federation:'+name)
block(0,5,0,'ae2federation:router')
block(12,2,0,'ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},south:{id:"ae2federation:bridge"}}')
block(12,2,1,'ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}}')
for i,face in enumerate(['north','south','east','west','up','down']):
    block(i*3,2,-5,'ae2federation:pattern_provider[facing='+face+']')
    block(i*3,2,-10,'ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},'+face+':{id:"ae2federation:bridge"}}')
# All 64 masks use actual neighboring Federation cables, not manufactured visual state.
directions=[(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]
for mask in range(64):
    x,z=24+(mask%8)*5,(mask//8)*5
    block(x,3,z,'ae2federation:cable')
    for bit,(dx,dy,dz) in enumerate(directions):
        if mask & (1<<bit): block(x+dx,3+dy,z+dz,'ae2federation:cable')
# Three long axes, and real native ME attachments.
commands += ['fill 0 2 8 14 2 8 ae2federation:cable',
             'fill 0 2 12 0 9 12 ae2federation:cable',
             'fill 5 2 12 5 2 22 ae2federation:cable']
block(-1,2,8,'ae2federation:router')
block(15,2,8,'ae2federation:pattern_provider[facing=west]')
block(16,2,8,'ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}}')
block(17,2,8,'ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}}')
block(18,2,8,'ae2federation:processing_endpoint')
block(19,2,8,'ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}}')
for i,name in enumerate(['router','pattern_provider','processing_endpoint','bridge','cable']):
    commands.append(f'give @a ae2federation:{name}')
    commands.append('summon minecraft:item '+f'{i*3+.5} 3 3.5 '+
                    '{NoGravity:1b,Age:-32768s,PickupDelay:32767s,Item:{id:"ae2federation:'+name+'",count:1}}')
commands += ['tp @a 8 6 16 facing 6 2 0']
(function/'gallery.mcfunction').write_text('\n'.join(commands)+'\n')
# 2,048 ordinary cables plus 128 machines, isolated from the gallery camera.
stress=['forceload add 96 0 168 68', 'fill 100 0 0 165 0 66 minecraft:smooth_stone']
for row in range(32):
    stress += [f'fill 100 2 {row*2} 163 2 {row*2} ae2federation:cable',
               f'setblock 99 2 {row*2} ae2federation:router',
               f'setblock 164 2 {row*2} ae2federation:pattern_provider[facing=west]',
               f'setblock 99 4 {row*2} ae2federation:processing_endpoint',
               f'setblock 164 4 {row*2} ae2federation:router']
stress += ['tp @a 130 25 80 facing 130 2 28']
(function/'stress.mcfunction').write_text('\n'.join(stress)+'\n')
print(pack)

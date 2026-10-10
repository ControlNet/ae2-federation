"""Write a datapack that builds the mod page scenes in a disposable world; never packaged in the mod.

`function hero_scene:base` builds two real ME networks joined by Federation blocks, with a third network on a Bridge.
`function hero_scene:lineup` puts every Federation block and item side by side. Both clear and replace the terrain
around their origin, so run them only in a throwaway world.
"""
from pathlib import Path
import argparse
import json

parser = argparse.ArgumentParser()
parser.add_argument('world', type=Path, help='Disposable world directory; writes only its hero_scene datapack')
parser.add_argument('--origin', type=int, nargs=3, default=[596, 68, 900], metavar=('X', 'Y', 'Z'),
                    help='Base scene origin; Y is the ground block level')
parser.add_argument('--lineup-offset', type=int, default=-45, help='X offset of the lineup from the base origin')
args = parser.parse_args()

pack = args.world / 'datapacks/hero_scene'
function = pack / 'data/hero_scene/function'
function.mkdir(parents=True, exist_ok=True)
(pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'AE2 Federation mod page scenes'}}))

FULL_DENSE_CELL = 'ae2:dense_energy_cell{internalCurrentPower:1600000.0d}'


def drive(facing, cells):
    items = ','.join(f'item{i}:{{id:"ae2:{cell}",count:1}}' for i, cell in enumerate(cells))
    return f'ae2:drive[facing={facing}]{{inv:{{{items}}}}}'


def cable_bus(cable, **parts):
    nbt = [f'cable:{{id:"ae2:{cable}"}}'] + [f'{side}:{{id:"{part}"}}' for side, part in parts.items()]
    return 'ae2:cable_bus{' + ','.join(nbt) + '}'


class Scene:
    def __init__(self, x, y, z):
        self.x, self.y, self.z = x, y, z
        self.commands = []

    def at(self, dx, dy, dz):
        return f'{self.x + dx} {self.y + dy} {self.z + dz}'

    def block(self, dx, dy, dz, state):
        self.commands.append(f'setblock {self.at(dx, dy, dz)} {state}')

    def fill(self, a, b, state):
        self.commands.append(f'fill {self.at(*a)} {self.at(*b)} {state}')

    def clear(self, x0, x1, z0, z1):
        # Flat grass at the origin's ground level, solid below it and open sky above it.
        self.commands += [f'forceload add {self.x + x0 - 16} {self.z + z0 - 16} {self.x + x1 + 16} {self.z + z1 + 16}',
                          f'kill @e[type=!player,x={self.x + x0},y={self.y - 4},z={self.z + z0},'
                          f'dx={x1 - x0},dy=40,dz={z1 - z0}]']
        for y0 in range(1, 33, 8):
            self.fill((x0, y0, z0), (x1, min(y0 + 7, 32), z1), 'minecraft:air')
        self.fill((x0, -6, z0), (x1, -1, z1), 'minecraft:dirt')
        self.fill((x0, 0, z0), (x1, 0, z1), 'minecraft:grass_block')

    def write(self, name):
        (function / f'{name}.mcfunction').write_text('\n'.join(self.commands) + '\n')


setup = ['gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
         'gamerule randomTickSpeed 0', 'time set 6000', 'weather clear']

# --- Base: Main Base and Workshop on two Switches, a Router with a smeltery between them, a Farm on a Bridge. ---
base = Scene(*args.origin)
base.commands += setup
base.clear(-24, 20, -10, 8)
base.fill((-21, 0, -6), (-7, 0, -2), 'minecraft:polished_andesite')
base.fill((7, 0, -6), (17, 0, -2), 'minecraft:polished_andesite')
base.fill((-8, 0, -5), (8, 0, -3), 'minecraft:stone_bricks')
base.fill((-3, 0, -2), (3, 0, 1), 'minecraft:stone_bricks')

# Farm: a small storage network west of Main Base, joined to it by a Bridge.
base.block(-21, 1, -4, FULL_DENSE_CELL)
base.block(-21, 2, -4, drive('south', ['item_storage_cell_1k'] * 2))
base.block(-20, 1, -4, cable_bus('lime_smart_cable'))
base.block(-19, 1, -4, cable_bus('fluix_smart_cable', west='ae2federation:bridge'))

# Main Base: energy, drives with terminals above them, a controller and a crafting CPU.
base.block(-18, 1, -4, FULL_DENSE_CELL)
base.block(-18, 2, -4, FULL_DENSE_CELL)
cells = [['item_storage_cell_64k', 'item_storage_cell_16k', 'item_storage_cell_16k', 'item_storage_cell_4k'],
         ['item_storage_cell_4k', 'fluid_storage_cell_4k', 'item_storage_cell_16k'],
         ['item_storage_cell_64k', 'item_storage_cell_64k', 'fluid_storage_cell_16k'],
         ['item_storage_cell_16k', 'item_storage_cell_4k', 'item_storage_cell_1k', 'item_storage_cell_1k']]
for i, row in enumerate(cells):
    base.block(-17 + i, 1, -4, drive('south', row))
    base.block(-17 + i, 2, -4, drive('south', list(reversed(row))))
terminals = ['ae2:terminal', 'ae2:crafting_terminal', 'ae2:pattern_encoding_terminal', 'ae2:pattern_access_terminal']
for i, terminal in enumerate(terminals):
    base.block(-17 + i, 3, -4, cable_bus('purple_smart_cable', south=terminal))
for dy in (1, 2, 3):
    base.block(-13, dy, -4, 'ae2:controller')
base.fill((-12, 1, -5), (-11, 2, -4), 'ae2:crafting_unit')
base.block(-12, 1, -4, 'ae2:4k_crafting_storage')
base.block(-11, 1, -4, 'ae2:crafting_accelerator')
base.block(-12, 2, -4, 'ae2:crafting_monitor[facing=south]')
base.block(-11, 2, -4, 'ae2:16k_crafting_storage')
# Switch A: its west face on Main Base's cable, its east face on the Federation Cable.
base.block(-10, 1, -4, cable_bus('purple_smart_cable'))
base.block(-9, 1, -4, 'ae2federation:switch')
# The Federation Pattern Provider: front down on Switch A, west face on Main Base's cable.
base.block(-10, 2, -4, cable_bus('purple_smart_cable'))
base.block(-9, 2, -4, 'ae2federation:pattern_provider[facing=down]')

# Federation Cable to a Router, and on to the Workshop's Switch.
base.fill((-8, 1, -4), (-1, 1, -4), 'ae2federation:cable')
base.block(0, 1, -4, 'ae2federation:router')
base.fill((1, 1, -4), (6, 1, -4), 'ae2federation:cable')
base.block(7, 1, -4, 'ae2federation:switch')

# Smeltery: three Processing Endpoints off the Router, each under a furnace subnet.
base.fill((0, 1, -3), (0, 1, -2), 'ae2federation:cable')
base.fill((-2, 1, -1), (2, 1, -1), 'ae2federation:cable')
for dx in (-2, 0, 2):
    base.block(dx, 1, 0, 'ae2federation:processing_endpoint[facing=north]')
    base.block(dx, 2, 0, cable_bus('light_blue_smart_cable', up='ae2:storage_bus'))
    base.block(dx, 3, 0, 'minecraft:furnace[facing=south]')
    base.block(dx, 1, 1, 'minecraft:hopper[facing=north]')

# Workshop: its own energy and drive, pattern providers feeding molecular assemblers.
base.block(8, 1, -4, cable_bus('green_smart_cable'))
base.block(9, 1, -4, FULL_DENSE_CELL)
base.block(9, 2, -4, drive('south', ['item_storage_cell_4k'] * 3))
for i, dx in enumerate(range(10, 16)):
    state = 'ae2:pattern_provider' if i % 2 == 0 else 'ae2:molecular_assembler'
    base.block(dx, 1, -4, state)
    base.block(dx, 2, -4, state)
base.block(9, 3, -4, cable_bus('green_smart_cable', south='ae2:crafting_terminal'))
base.write('base')

# --- Lineup: every Federation block on a quartz plinth before a wall, every Federation item floating above them. ---
lineup = Scene(args.origin[0] + args.lineup_offset, args.origin[1], args.origin[2])
lineup.commands += setup
lineup.clear(-14, 14, -8, 10)
lineup.fill((-11, 0, -4), (10, 0, 2), 'minecraft:polished_deepslate')
lineup.fill((-11, 1, -4), (10, 5, -4), 'minecraft:deepslate_tiles')
lineup.fill((-11, 6, -4), (10, 6, -4), 'minecraft:polished_deepslate_slab[type=bottom]')
# Switch, Federation Cable and Router joined on one plinth; then the Provider and Endpoint facing south.
lineup.fill((-8, 1, -2), (-5, 1, -2), 'minecraft:smooth_quartz')
lineup.block(-8, 2, -2, 'ae2federation:switch')
lineup.fill((-7, 2, -2), (-6, 2, -2), 'ae2federation:cable')
lineup.block(-5, 2, -2, 'ae2federation:router')
for dx, state in [(-2, 'ae2federation:pattern_provider[facing=south]'),
                  (1, 'ae2federation:processing_endpoint[facing=south]')]:
    lineup.block(dx, 1, -2, 'minecraft:smooth_quartz')
    lineup.block(dx, 2, -2, state)
# The two parts on one ME cable, so they read as cable attachments.
lineup.fill((4, 1, -2), (7, 1, -2), 'minecraft:smooth_quartz')
lineup.block(4, 2, -2, cable_bus('fluix_smart_cable', south='ae2federation:bridge'))
lineup.block(5, 2, -2, cable_bus('fluix_smart_cable'))
lineup.block(6, 2, -2, cable_bus('fluix_smart_cable'))
lineup.block(7, 2, -2, cable_bus('fluix_smart_cable', south='ae2federation:federation_p2p_tunnel'))
items = ['nexus_processor_press', 'printed_nexus_circuit', 'nexus_processor', 'nexus_core']
for i, item in enumerate(items):
    x, y, z = lineup.x - 4.5 + i * 3 + 0.5, lineup.y + 4.2, lineup.z - 3 + 0.2
    lineup.commands.append(f'summon minecraft:item_display {x} {y} {z} {{item:{{id:"ae2federation:{item}",count:1}},'
                           'item_display:"fixed",Rotation:[180f,0f],transformation:{left_rotation:[0f,0f,0f,1f],'
                           'right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2.2f,2.2f,2.2f]}}')
lineup.write('lineup')
print(pack)

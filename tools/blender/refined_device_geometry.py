"""Pixel-grid device structures. Coordinates are voxels (16 per block), Blender Z-up.

Every box face receives a purpose-drawn texture at its physical pixel dimensions.
"""
import itertools
import math


def part(name, center, size, role, rotation=0, face_roles=None):
    return {'name': name, 'center': list(center), 'size': list(size), 'role': role,
            'rotation_y': rotation, 'face_roles': face_roles or {}}


def laser_parts(section=2, variant='open'):
    n = section
    z = 8*n
    pieces = []
    def add(name, center, size, role, rotation=0, face_roles=None):
        pieces.append(part(name, center, size, role, rotation, face_roles))
    # A compact, dark excitation block; the white ceramic is confined to shoulders.
    add('Excitation / main housing', (0, 11*n, z), (12*n, 9*n, 12*n), 'engine')
    add('Excitation / rear energy terminal', (0, 15.5*n, z), (8*n, n, 8*n), 'terminal')
    for sx, sz in itertools.product((-1,1), repeat=2):
        add('Excitation / corner armor', (sx*6*n, 11*n, z+sz*6*n), (3*n, 10*n, 3*n), 'ceramic')
        add('Excitation / contact shoe', (sx*6*n, 5.5*n, z+sz*6*n), (3*n, n, 3*n), 'collar')
    # Open rails carry a graduated focusing chamber. Dimensions remain on the pixel grid.
    for sx, sz in itertools.product((-1,1), repeat=2):
        add('Chassis / textured rail', (sx*6*n, -4*n, z+sz*6*n), (2*n, 20*n, 2*n), 'rail')
        for y in (-10*n, 2*n):
            add('Chassis / rail coupling', (sx*6*n, y, z+sz*6*n), (3*n, 2*n, 3*n), 'collar')
    add('Optics / gain rod', (0,-2*n,z), (3*n, 23*n, 3*n), 'gain')
    # Eight independent blocks around each ring echo the artist's segmented beam former.
    for layer, y, radius, width, thick, role in (
        ('rear gate', 4*n, 4.5*n, 3*n, n, 'metal'),
        ('forward gate', -5*n, 4*n, 3*n, n, 'collar'),
        ('muzzle support', -12*n, 5.5*n, 4*n, 2*n, 'metal'),
        ('muzzle lip', -15*n, 4*n, 3*n, 2*n, 'collar')):
        for j in range(8):
            angle = j*45
            a = math.radians(angle)
            add('Optics / '+layer, (radius*math.sin(a),y,z+radius*math.cos(a)),
                (width, thick, max(1,n)), role, angle)
    # Four ceramic forks make the front's direction visible even without its light.
    for sx,sz in itertools.product((-1,1),repeat=2):
        add('Muzzle / stepped shoulder', (sx*4*n,-12*n,z+sz*4*n), (3*n,5*n,3*n), 'ceramic')
    if variant == 'shroud':
        # Long segmented top/side shutters, with open gaps and a dark optical throat.
        for axis in ('top','left','right'):
            for segment in range(3):
                y = -9*n+segment*5*n
                if axis=='top':
                    center,size=(0,y,z+6*n),(9*n,4*n,n)
                else:
                    sign=1 if axis=='right' else -1
                    center,size=(sign*6*n,y,z),(n,4*n,9*n)
                add('Shroud / directional shutter',center,size,'shroud')
    return pieces


def quantum_parts(section=2):
    edge = 16*(section+2)
    h = edge/2
    pieces=[]
    def add(name, center, size, role):
        pieces.append(part(name,center,size,role))
    # The only full-length members are the twelve perimeter rails.
    for axis in range(3):
        others=[i for i in range(3) if i!=axis]
        for a,b in itertools.product((-1,1),repeat=2):
            pos=[0,0,h]
            pos[others[0]]+=a*(h-2)
            pos[others[1]]+=b*(h-2)
            dims=[4,4,4];dims[axis]=edge-12
            add('Quantum / etched perimeter rail',pos,dims,'rail')
            for sign in (-1,1):
                p=list(pos);p[axis]+=(h-13)*sign
                d=[6,6,6];d[axis]=3
                add('Quantum / rail sleeve',p,d,'collar')
    for signs in itertools.product((-1,1),repeat=3):
        pos=[signs[0]*(h-4),signs[1]*(h-4),h+signs[2]*(h-4)]
        add('Quantum / corner anchor',pos,(8,8,8),'anchor')
        for axis in range(3):
            p=list(pos);p[axis]+=signs[axis]*4
            d=[6,6,6];d[axis]=1
            add('Quantum / inset corner plate',p,d,'terminal')
    return pieces


# Box corner order used by the Blender builder, with physical UV axes per face.
FACES = [((0,1,3,2),(1,2),'left'), ((4,6,7,5),(1,2),'right'),
         ((0,4,5,1),(0,2),'front'), ((2,3,7,6),(0,2),'back'),
         ((0,2,6,4),(0,1),'bottom'), ((1,5,7,3),(0,1),'top')]


def texture_requirements():
    result=set()
    for n in (1,2,3):
        for piece in laser_parts(n,'open')+laser_parts(n,'shroud')+quantum_parts(n):
            for _,axes,face in FACES:
                w,h=(max(1,round(piece['size'][a])) for a in axes)
                role=piece['face_roles'].get(face,piece['role'])
                result.add((role,w,h))
    result.update([('shard',12,12),('shard_inner',12,12),('core',16,16)])
    return sorted(result)

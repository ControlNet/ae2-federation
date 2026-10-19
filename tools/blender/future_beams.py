"""Eight deterministic, real mesh beam concepts. Standard-library geometry only.

Coordinates are Minecraft block units. These new geometries are Blender prototypes
for a future Java renderer, not drop-in texture replacements or game-tested effects.
"""
import math

TAU=math.tau
PROFILES={
 'e_helix':('E / PHASE HELIX','Two rotating cyan/violet helices around a fine axial filament.'),
 'f_gates':('F / PHOTON GATES','Traveling twelve-sided light gates surrounding a central beam.'),
 'g_crystals':('G / CRYSTAL TRAIN','Suspended faceted photon crystals traveling along a fine guide.'),
 'h_arc':('H / ION ARC','Angular white-blue plasma filaments with violet branch discharges.'),
 'i_lattice':('I / QUANTUM LATTICE','A rotating diamond wire lattice, punctuated by bright lattice nodes.'),
 'j_rift':('J / RIFT RIBBON','A translucent violet wave sheet bounded by luminous pink/cyan edges.'),
 'k_orbit':('K / ORBITAL NODES','Warm core nodes and orbiting satellites enclosed in cool inclined rings.'),
 'l_blades':('L / VECTOR BLADES','Traveling chevron-shaped optical fins with alternating cyan and amber tips.'),
}
# Display-referred RGB and alpha; material adapter converts RGB to scene-linear.
PALETTE=[
 (.35,.94,1,.95), (.85,.99,1,1), (.65,.28,1,.85), (1,.25,.67,.92),
 (.2,.9,.74,.85), (1,.64,.22,1), (.27,.16,.66,.18), (.05,.55,.75,.26),
]


def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def mul(a,s): return tuple(x*s for x in a)
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def unit(v):
    length=math.sqrt(sum(x*x for x in v))
    return mul(v,1/max(length,1e-12))


class Mesh:
    def __init__(self):
        self.vertices=[]; self.faces=[]; self.materials=[]

    def face(self,points,material):
        first=len(self.vertices); self.vertices.extend(points)
        self.faces.append(tuple(range(first,first+len(points)))); self.materials.append(material)

    def tube(self,a,b,radius,material,sides=4):
        direction=unit(sub(b,a))
        normal=unit(cross(direction,(0,0,1) if abs(direction[2])<.9 else (0,1,0)))
        tangent=cross(direction,normal)
        rings=[]
        for center in (a,b):
            rings.append([add(center,add(mul(normal,radius*math.cos(i*TAU/sides)),mul(tangent,radius*math.sin(i*TAU/sides)))) for i in range(sides)])
        for i in range(sides):
            j=(i+1)%sides
            self.face([rings[0][i],rings[0][j],rings[1][j],rings[1][i]],material)
        self.face(rings[0][::-1],material); self.face(rings[1],material)

    def line(self,points,radius,material):
        for a,b in zip(points,points[1:]): self.tube(a,b,radius,material)

    def crystal(self,center,axis,length,radius,material=0,angle=0):
        b,c=(axis+1)%3,(axis+2)%3
        ends=[]
        for sign in (-1,1):
            p=list(center); p[axis]+=sign*length; ends.append(tuple(p))
        waist=[]
        for i in range(4):
            p=list(center); phi=angle+i*TAU/4
            p[b]+=radius*math.cos(phi); p[c]+=radius*math.sin(phi); waist.append(tuple(p))
        for i in range(4):
            self.face([ends[0],waist[(i+1)%4],waist[i]],material)
            self.face([ends[1],waist[i],waist[(i+1)%4]],2 if material==0 else material)


def generate(profile,mask,world,origin,cycle):
    mesh=Mesh()
    for axis in range(3):
        pair=(mask>>(2*axis))&3
        if not pair: continue
        start=0 if pair&2 else .5
        end=1 if pair&1 else .5
        length=end-start
        n=max(1,round(length*24))
        coordinate=world[axis]
        b,c=(axis+1)%3,(axis+2)%3
        def point(s,y=0,z=0):
            p=[.5,.5,.5]; p[axis]=s; p[b]+=y; p[c]+=z
            return tuple(p)
        def curve(fn,segments=n):
            return [fn(start+length*i/segments) for i in range(segments+1)]
        def ring(s,r,material=0,angle=0,tilt=0):
            points=[]
            for i in range(17):
                phi=i*TAU/16+angle
                points.append(point(s+tilt*math.cos(phi),r*math.cos(phi),r*math.sin(phi)))
            mesh.line(points,.0055,material)
        if profile=='e_helix':
            mesh.tube(point(start),point(end),.005,1)
            for strand in range(2):
                def helix(s):
                    angle=TAU*((coordinate+s)/1.25-cycle)+strand*math.pi
                    return point(s,.125*math.cos(angle),.125*math.sin(angle))
                mesh.line(curve(helix),.009,0 if strand==0 else 2)
        elif profile=='f_gates':
            mesh.tube(point(start),point(end),.009,0)
            count=max(1,round(length*2))
            for i in range(count):
                s=start+length*((i+.15+2*cycle)%count)/count
                ring(s,.17,0 if i%2==0 else 2,cycle*TAU)
                # Four bright cardinal notches establish a mechanical optical-gate silhouette.
                for k in range(4):
                    phi=k*math.pi/2
                    mesh.tube(point(s,.15*math.cos(phi),.15*math.sin(phi)),point(s,.19*math.cos(phi),.19*math.sin(phi)),.008,1)
        elif profile=='g_crystals':
            mesh.tube(point(start),point(end),.004,7)
            count=max(1,round(length*2))
            for i in range(count):
                s=start+length*((i+.4+2*cycle)%count)/count
                mesh.crystal(point(s),axis,.12,.092,0,cycle*TAU+coordinate*.7)
                mesh.crystal(point(s),axis,.04,.024,1)
        elif profile=='h_arc':
            samples=max(1,round(length*12))
            def arc(s,offset=0):
                q=(coordinate+s)*12
                return point(s,.075*math.sin(q*2.4+TAU*cycle+offset),.075*math.sin(q*4.1-TAU*cycle+offset))
            main=curve(arc,samples); mesh.line(main,.009,1)
            mesh.line(curve(lambda s:arc(s,2.4),samples),.0045,0)
            for i in range(samples):
                s=start+length*(i+.5)/samples
                phase=(coordinate+s)*9+TAU*cycle
                endpoint=point(s+.035,.16*math.cos(phase),.16*math.sin(phase))
                mesh.tube(arc(s),endpoint,.0035,2)
        elif profile=='i_lattice':
            samples=max(1,round(length*8))
            for i in range(samples):
                s=start+length*i/samples; S=start+length*(i+1)/samples
                for k in range(4):
                    phi=k*math.pi/2+TAU*cycle
                    a=point(s,.135*math.cos(phi),.135*math.sin(phi))
                    for turn in (-1,1):
                        angle=phi+turn*math.pi/2
                        mesh.tube(a,point(S,.135*math.cos(angle),.135*math.sin(angle)),.0035,4)
                    mesh.crystal(a,axis,.012,.009,1)
        elif profile=='j_rift':
            def center(s):
                q=TAU*((coordinate+s)*.7-cycle)
                return .085*math.sin(q),.04*math.cos(q)
            upper=[]; lower=[]
            for i in range(n+1):
                s=start+length*i/n; y,z=center(s)
                upper.append(point(s,y+.07,z)); lower.append(point(s,y-.07,z))
            for i in range(n): mesh.face([upper[i],lower[i],lower[i+1],upper[i+1]],6)
            mesh.line(upper,.0065,3); mesh.line(lower,.0065,0)
            mesh.line(curve(lambda s:point(s,*center(s))),.004,2)
        elif profile=='k_orbit':
            mesh.tube(point(start),point(end),.004,7)
            s=(start+end)/2
            mesh.crystal(point(s),axis,.045,.04,5,cycle*TAU)
            for k in range(2):
                ring(s,.155,0 if k==0 else 2,tilt=.065 if k==0 else -.065)
            for k in range(3):
                phi=TAU*cycle+k*TAU/3+coordinate
                position=point(s+.065*math.cos(phi),.155*math.cos(phi),.155*math.sin(phi))
                mesh.crystal(position,axis,.025,.021,5 if k==0 else 1,phi)
        elif profile=='l_blades':
            count=max(1,round(length*4))
            for i in range(count):
                s=start+length*((i+.2+4*cycle)%count)/count
                radius=.10+.045*math.sin((coordinate+s)*TAU)
                for plane in range(2):
                    wings=[point(s-.075,-radius if plane==0 else 0,-radius if plane else 0),point(s+.035),point(s-.075,radius if plane==0 else 0,radius if plane else 0)]
                    mesh.line(wings,.007,0)
                    back=[point(s-.095,-radius if plane==0 else 0,-radius if plane else 0),point(s+.015),point(s-.095,radius if plane==0 else 0,radius if plane else 0)]
                    for j in range(2):mesh.face([wings[j],wings[j+1],back[j+1],back[j]],7)
                mesh.crystal(point(s+.035),axis,.014,.013,5)
        else: raise ValueError(profile)
    # Small joint marker acknowledges a branch without pretending to model packet routing.
    if mask.bit_count()>2:
        mesh.crystal((.5,.5,.5),0,.065,.055,1,cycle*TAU)
    mesh.vertices=[(p[0]+origin[0],-p[2]+origin[1],p[1]+origin[2]) for p in mesh.vertices]
    return mesh

"""Reuse the artist's perimeter contact geometry in recessed interior sockets."""
from copy import deepcopy

from geometry import SIDES, element_quads, face_points, neighbor, quad


def basis(side):
    origin, vertical, _, horizontal = face_points([0,0,0], [16,16,16])[side]
    return origin, [(horizontal[a]-origin[a])/16 for a in range(3)], [
        (vertical[a]-origin[a])/16 for a in range(3)]


def to_world(side, u, v, depth):
    origin, U, V = basis(side)
    axis, sign = SIDES[side]
    p = [origin[a]+U[a]*u+V[a]*v for a in range(3)]
    p[axis] -= sign*depth
    return p


def local_uv(side, cell, point):
    origin, U, V = basis(side)
    relative = [16*(point[a]-cell[a])-origin[a] for a in range(3)]
    return sum(relative[a]*U[a] for a in range(3)), sum(relative[a]*V[a] for a in range(3))


def connections_on_face(side, cell, cells):
    _, U, V = basis(side)
    result = []
    for label, vector, sign in [('left',U,-1),('right',U,1),('top',V,-1),('bottom',V,1)]:
        axis = next(a for a in range(3) if vector[a])
        direction = next(s for s,pair in SIDES.items() if pair == (axis,vector[axis]*sign))
        if neighbor(cell,direction) in cells:
            result.append(label)
    return result


def footprint(connection):
    return {'left': (0,5,3,11), 'right': (13,5,16,11),
            'top': (5,0,11,3), 'bottom': (5,13,11,16)}[connection]


def subtract(rect, cut):
    u,v,U,V = rect
    x,y,X,Y = max(u,cut[0]),max(v,cut[1]),min(U,cut[2]),min(V,cut[3])
    if x >= X or y >= Y:
        return [rect]
    return [r for r in [(u,v,U,y),(u,Y,U,V),(u,y,x,Y),(X,y,U,Y)] if r[0]<r[2] and r[1]<r[3]]


def cut_surface(q, cuts):
    """Remove the socket footprint from both skin and infill, preserving UVs."""
    cell, side = q['cell'], q['side']
    local = [local_uv(side,cell,p) for p in q['positions']]
    u,v = local[0]
    U,V = local[2]
    pieces = [(u,v,U,V)]
    for cut in cuts:
        pieces = [part for r in pieces for part in subtract(r,cut)]
    result = []
    for x,y,X,Y in pieces:
        new = deepcopy(q)
        new['positions'], new['uv'] = [], []
        for a,b in [(x,y),(x,Y),(X,Y),(X,y)]:
            s,t = (a-u)/(U-u), (b-v)/(V-v)
            for key in ['positions','uv']:
                new[key].append([q[key][0][i]+s*(q[key][3][i]-q[key][0][i])+
                                 t*(q[key][1][i]-q[key][0][i]) for i in range(len(q[key][0]))])
        result.append(new)
    return result


def mapped_point(side, connection, point):
    x,y,depth = point
    u,v = {'left': (y,x), 'right': (16-y,x), 'top': (x,y), 'bottom': (x,16-y)}[connection]
    return to_world(side,u,v,depth)


def determinant(side, connection):
    origin = mapped_point(side,connection,(0,0,0))
    axes = [[mapped_point(side,connection,tuple(int(a==b) for a in range(3)))[i]-origin[i]
             for i in range(3)] for b in range(3)]
    a,b,c = axes
    return a[0]*(b[1]*c[2]-b[2]*c[1])-a[1]*(b[0]*c[2]-b[2]*c[0])+a[2]*(b[0]*c[1]-b[1]*c[0])


def socket(cell, side, connection, elements):
    result = []
    rect = footprint(connection)
    # The pocket is two model units deep, matching the source contact thickness.
    points = [to_world(side,u,v,2) for u,v in [(rect[0],rect[1]),(rect[0],rect[3]),
                                             (rect[2],rect[3]),(rect[2],rect[1])]]
    result.append(quad(points,[(3.5,1.5)]*4,'switch',cell,side,'socket-floor',-3))
    # Real pocket walls. The seam between halves stays open.
    for a,b in zip(points,points[1:]+points[:1]):
        A,B = list(a),list(b)
        normal,sign = SIDES[side]
        A[normal] += sign*1.82
        B[normal] += sign*1.82
        # Omit a wall lying exactly on the neighbor block boundary.
        if any(a[axis] == b[axis] and a[axis] in (0,16) for axis in range(3) if axis != normal):
            continue
        result.append(quad([a,A,B,b],[(1.5,1.5)]*4,'switch',cell,side,'socket-wall',-3))

    # Indices 15/16/17 are the original cyan edge contact and recessed side pieces.
    # Preserve their sizes, UVs and 0/1/2-unit depth profile. Supply only caps that
    # were buried inside the original body and become visible in the new socket.
    for index in [15,16,17]:
        element = deepcopy(elements[index])
        for face in ['east','west','up','down']:
            if face not in element['faces']:
                element['faces'][face] = deepcopy(element['faces'].get('down',element['faces']['north']))
        quads = element_quads(element,'switch',(0,0,0),'edge-contact',index)
        for q in quads:
            if q['side'] == 'down':
                # The cyan contact's outer end meets its partner across this seam.
                if element['from'][1] == 0:
                    continue
            positions = [mapped_point(side,connection,[16*v for v in p]) for p in q['positions']]
            uv = [[16*v for v in pair] for pair in q['uv']]
            if determinant(side,connection) < 0:
                positions.reverse()
                uv.reverse()
            mapped = quad(positions,uv,'switch',cell,side,'edge-contact',index)
            mapped['source_face'] = q['side']
            mapped['connection'] = connection
            result.append(mapped)

    # A one-unit recessed neck joins the unchanged purple module to the contact.
    neck = deepcopy(elements[16])
    neck['from'],neck['to'] = [5,2,1],[11,3,2]
    pixel = {'texture':'#0','uv':[10.5,14.5,10.5,14.5]}
    neck['faces'] = {s:dict(pixel) for s in ['north','east','west','up','down']}
    for q in element_quads(neck,'switch',(0,0,0),'contact-neck',-3):
        positions = [mapped_point(side,connection,[16*v for v in p]) for p in q['positions']]
        uv = [[16*v for v in pair] for pair in q['uv']]
        if determinant(side,connection) < 0:
            positions.reverse()
            uv.reverse()
        result.append(quad(positions,uv,'switch',cell,side,'contact-neck',-3))
    return result


def match_edges(baseline, cells, model):
    result, contexts = [], {}
    for q in baseline:
        if q['kind'].startswith('link'):
            continue
        if q['kind'] not in ('panel','infill'):
            result.append(q)
            continue
        key = (tuple(q['cell']),q['side'])
        if key not in contexts:
            contexts[key] = connections_on_face(key[1],key[0],cells)
        result.extend(cut_surface(q,[footprint(c) for c in contexts[key]]))
    for (cell,side),connections in contexts.items():
        for connection in connections:
            result.extend(socket(cell,side,connection,model['elements']))
    return result

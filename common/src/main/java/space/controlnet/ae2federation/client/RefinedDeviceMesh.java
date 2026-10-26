package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/** Blender mesh polygons and UV-preserving clipping into individual chunk-owned block cells. */
public final class RefinedDeviceMesh {
    private RefinedDeviceMesh() { }

    public record Vertex(double x, double y, double z, double u, double v) {
        double coordinate(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        Vertex mix(Vertex b, double t) {
            return new Vertex(x+(b.x-x)*t, y+(b.y-y)*t, z+(b.z-z)*t, u+(b.u-u)*t, v+(b.v-v)*t);
        }
        public Vertex move(double dx, double dy, double dz) { return new Vertex(x+dx,y+dy,z+dz,u,v); }
        public Vertex scale(double scale) { return new Vertex(x*scale,y*scale,z*scale,u,v); }
        public Vertex turn(Direction front) {
            return switch (front) {
                case SOUTH -> this;
                case NORTH -> new Vertex(-x,y,-z,u,v);
                case EAST -> new Vertex(z,y,-x,u,v);
                case WEST -> new Vertex(-z,y,x,u,v);
                case UP -> new Vertex(x,z,-y,u,v);
                case DOWN -> new Vertex(x,-z,y,u,v);
            };
        }
    }

    public record Quad(List<Vertex> vertices, double[] normal) {
        public Quad(List<Vertex> vertices) { this(vertices, calculateNormal(vertices)); }
        private static double[] calculateNormal(List<Vertex> vertices) {
            var a=vertices.get(0); var b=vertices.get(1); var c=vertices.get(2);
            double ax=b.x-a.x, ay=b.y-a.y, az=b.z-a.z, bx=c.x-a.x, by=c.y-a.y, bz=c.z-a.z;
            double x=ay*bz-az*by, y=az*bx-ax*bz, z=ax*by-ay*bx;
            double length=Math.sqrt(x*x+y*y+z*z);
            return length < 1e-12 ? new double[]{0,1,0} : new double[]{x/length,y/length,z/length};
        }
    }

    /** Clip after moving into the member's [0,1] cube. Shared coplanar faces belong to one cell only. */
    public static List<Quad> cell(List<Quad> source, Direction front, double dx, double dy, double dz) {
        var result=new ArrayList<Quad>();
        for (var quad:source) {
            List<Vertex> polygon=new ArrayList<>();
            for (var v:quad.vertices) polygon.add(v.turn(front).move(dx,dy,dz));
            var normal=new Quad(polygon).normal();
            boolean duplicate=false;
            for (int axis=0;axis<3;axis++) {
                final int a=axis;
                // A polygon exactly on a grid plane belongs to the cell behind its outward normal.
                if (normal[axis] > .999 && polygon.stream().allMatch(v -> Math.abs(v.coordinate(a))<1e-7)
                        || normal[axis] < -.999 && polygon.stream().allMatch(v -> Math.abs(v.coordinate(a)-1)<1e-7)) {
                    duplicate=true; break;
                }
                polygon=clip(polygon,axis,0,true);
                polygon=clip(polygon,axis,1,false);
            }
            if (duplicate || polygon.size()<3) continue;
            for (int i=1;i<polygon.size()-1;i++) {
                var a=polygon.get(0); var b=polygon.get(i); var c=polygon.get(i+1);
                double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
                double area=Math.pow(uy*vz-uz*vy,2)+Math.pow(uz*vx-ux*vz,2)+Math.pow(ux*vy-uy*vx,2);
                if (area>1e-16) result.add(new Quad(List.of(a,b,c,c)));
            }
        }
        return List.copyOf(result);
    }

    private static List<Vertex> clip(List<Vertex> input,int axis,double plane,boolean greater) {
        var output=new ArrayList<Vertex>();
        if (input.isEmpty()) return output;
        var previous=input.getLast();
        double before=previous.coordinate(axis)-plane;
        boolean wasInside=greater ? before>=-1e-8 : before<=1e-8;
        for (var current:input) {
            double after=current.coordinate(axis)-plane;
            boolean inside=greater ? after>=-1e-8 : after<=1e-8;
            if (inside!=wasInside) output.add(previous.mix(current,before/(before-after)));
            if (inside) output.add(current);
            previous=current; before=after; wasInside=inside;
        }
        return output;
    }
}

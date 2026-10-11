import java.util.*;

/** Exports current production geometry through an offline quad backend. */
public final class ExportCable {
    static String array(float[] a) { return Arrays.toString(a); }
    static String matrix(float[][] a) { return Arrays.stream(a).map(ExportCable::array).reduce((x,y)->x+","+y).map(x->"["+x+"]").orElse("[]"); }
    static String quads(List<CapturedQuad> quads) {
        var rows = new ArrayList<String>();
        for (var q : quads) rows.add("{\"texture\":\"ae2federation:part/cable/dense/"+q.texture()+"\",\"positions\":"+matrix(q.positions())+",\"uv\":"+matrix(q.uv())+"}");
        return "["+String.join(",", rows)+"]";
    }
    public static void main(String[] args) {
        var cores = new EnumMap<CableCoreFaces.Variant, TextureRef>(CableCoreFaces.Variant.class);
        for (var v : CableCoreFaces.Variant.values()) cores.put(v, new TextureRef(v.texture()));
        var builder = new FederationCableBuilder(cores, new TextureRef("line"));
        // Validate the full connection space, even though only requested shapes are exported.
        for (int c=0; c<CableVisualConnections.COUNT; c++) {
            if (builder.build(c).isEmpty()) throw new AssertionError("Empty cable " + c);
        }
        var shapes = new ArrayList<String>();
        for (String value : args[0].split(",")) {
            int c = Integer.parseInt(value);
            shapes.add("\""+c+"\":"+quads(builder.build(c)));
        }
        var flow = new ArrayList<String>();
        for (int mask=0; mask<64; mask++) {
            var rows = new ArrayList<String>();
            for (var r : CableFlowGeometry.ribbons(mask)) {
                var vertices = new ArrayList<String>();
                for (var v : r.vertices()) vertices.add(array(new float[]{v.x(),v.y(),v.z(),v.along(),v.across(),v.alpha()}));
                rows.add("{\"axis\":"+r.axis()+",\"layer\":\""+r.layer()+"\",\"vertices\":["+String.join(",",vertices)+"]}");
            }
            flow.add("["+String.join(",", rows)+"]");
        }
        var phases = new ArrayList<String>();
        for (int frame=0; frame<=240; frame++) {
            double ticks = frame * 20.0 / 30.0;
            float[] row = new float[4];
            for (int coordinate=0; coordinate<4; coordinate++) row[coordinate] = CableFlowGeometry.phase(coordinate, (long)ticks, (float)(ticks-Math.floor(ticks)));
            phases.add(array(row));
        }
        System.out.print("{\"shells\":{"+String.join(",",shapes)+"},\"item\":"+quads(builder.item())+",\"flow\":["+String.join(",",flow)+"],\"phases\":["+String.join(",",phases)+"]}");
    }
}

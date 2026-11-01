package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class RefinedDeviceMeshTest {
    private static final RefinedDeviceMesh.Quad PANEL=new RefinedDeviceMesh.Quad(List.of(
            new RefinedDeviceMesh.Vertex(0,0,.5,0,0),new RefinedDeviceMesh.Vertex(2,0,.5,1,0),
            new RefinedDeviceMesh.Vertex(2,1,.5,1,1),new RefinedDeviceMesh.Vertex(0,1,.5,0,1)));
    @Test void clippingPreservesAreaAndInterpolatesUvAtMemberSeams() {
        double area=0;
        for (int x=0;x<2;x++) {
            var quads=RefinedDeviceMesh.cell(List.of(PANEL),Direction.SOUTH,-x,0,0);
            assertEquals(2,quads.size());
            for (var q:quads) {
                var a=q.vertices().get(0); var b=q.vertices().get(1); var c=q.vertices().get(2);
                area+=Math.abs((b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x()))/2;
                for (var v:q.vertices()) {
                    assertTrue(v.x()>=0 && v.x()<=1);
                    assertEquals((v.x()+x)/2,v.u(),1e-8);
                    assertEquals(v.y(),v.v(),1e-8);
                }
            }
        }
        assertEquals(2,area,1e-8);
    }
    @Test void everyFacingMapsTheMuzzleToItsDirection() {
        var muzzle=new RefinedDeviceMesh.Vertex(0,0,1,0,0);
        for (var direction:Direction.values()) {
            var v=muzzle.turn(direction);
            assertEquals(direction.getStepX(),v.x(),1e-8);
            assertEquals(direction.getStepY(),v.y(),1e-8);
            assertEquals(direction.getStepZ(),v.z(),1e-8);
        }
    }
    @Test void gridPlaneHasOneOwnerAndRemoteCellsStayEmpty() {
        var plane=new RefinedDeviceMesh.Quad(PANEL.vertices().stream().map(v -> v.move(0,0,.5)).toList());
        assertEquals(2,RefinedDeviceMesh.cell(List.of(plane),Direction.SOUTH,0,0,0).size());
        assertTrue(RefinedDeviceMesh.cell(List.of(plane),Direction.SOUTH,0,0,-1).isEmpty());
        assertTrue(RefinedDeviceMesh.cell(List.of(plane),Direction.SOUTH,0,0,-4).isEmpty());
    }
}

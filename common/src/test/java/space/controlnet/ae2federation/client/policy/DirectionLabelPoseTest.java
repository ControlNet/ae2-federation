package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DirectionLabelPoseTest {
    private static final float BODY_WIDTH = 40;
    private static final float BODY_HEIGHT = 13;
    private static final float CAP = 5;
    private static final float CLEARANCE = 3;

    private static DirectionLabelPose pose(double degrees) {
        double radians = Math.toRadians(degrees);
        return DirectionLabelPose.of((float) Math.cos(radians), (float) Math.sin(radians), BODY_WIDTH, BODY_HEIGHT, CAP, CLEARANCE);
    }

    @Test
    void aLevelLinkToTheRightCarriesItsLabelAboveWithTheCapOnTheRight() {
        var pose = pose(0);
        assertEquals(0, pose.rotation(), 1e-4);
        assertEquals(DirectionLabelPose.Cap.RIGHT, pose.cap());
        assertEquals(BODY_WIDTH + CAP, pose.width(), 1e-4);
        assertEquals(BODY_HEIGHT, pose.height(), 1e-4);
        // Left of travel to the right is up, in screen coordinates.
        assertEquals(0, pose.offsetX(), 1e-4);
        assertEquals(-(BODY_HEIGHT / 2 + CLEARANCE), pose.offsetY(), 1e-4);
    }

    @Test
    void aLinkToTheLeftIsTurnedBackUprightWithTheCapOnTheLeftBelowTheLine() {
        var pose = pose(180);
        assertEquals(0, pose.rotation(), 1e-3);
        assertEquals(DirectionLabelPose.Cap.LEFT, pose.cap());
        assertEquals(BODY_HEIGHT / 2 + CLEARANCE, pose.offsetY(), 1e-3);
    }

    @Test
    void theTwoDirectionsOfALinkSitOnOppositeSides() {
        for (double degrees : new double[] {0, 20, 34, 40, 70, 90, -60}) {
            var forward = pose(degrees);
            var back = pose(degrees + 180);
            assertEquals(-forward.offsetX(), back.offsetX(), 1e-3, "x at " + degrees);
            assertEquals(-forward.offsetY(), back.offsetY(), 1e-3, "y at " + degrees);
        }
    }

    @Test
    void withinTheTiltLimitTheLabelRunsAlongTheLink() {
        var down = pose(30);
        assertEquals(30, down.rotation(), 1e-3);
        assertEquals(DirectionLabelPose.Cap.RIGHT, down.cap());
        var up = pose(-30);
        assertEquals(-30, up.rotation(), 1e-3);
        // Heading left and down by 30 degrees reads as text tilted up by 30 degrees, its cap on the left.
        var leftDown = pose(150);
        assertEquals(-30, leftDown.rotation(), 1e-3);
        assertEquals(DirectionLabelPose.Cap.LEFT, leftDown.cap());
        var leftUp = pose(-150);
        assertEquals(30, leftUp.rotation(), 1e-3);
        assertEquals(DirectionLabelPose.Cap.LEFT, leftUp.cap());
        // Its centre sits off the line by half its height, square to the link.
        float distance = (float) Math.hypot(down.offsetX(), down.offsetY());
        assertEquals(BODY_HEIGHT / 2 + CLEARANCE, distance, 1e-3);
    }

    @Test
    void steeperThanTheLimitTheLabelStaysLevelAndPointsUpOrDown() {
        var down = pose(40);
        assertEquals(0, down.rotation(), 1e-4);
        assertEquals(DirectionLabelPose.Cap.DOWN, down.cap());
        assertEquals(BODY_WIDTH, down.width(), 1e-4);
        assertEquals(BODY_HEIGHT + CAP, down.height(), 1e-4);
        assertEquals(DirectionLabelPose.Cap.UP, pose(-40).cap());
        assertEquals(DirectionLabelPose.Cap.DOWN, pose(140).cap());
        var straightDown = pose(90);
        assertEquals(DirectionLabelPose.Cap.DOWN, straightDown.cap());
        // Heading down, left of travel is to the right of the screen; the box clears the line by its half width.
        assertEquals(BODY_WIDTH / 2 + CLEARANCE, straightDown.offsetX(), 1e-3);
        assertEquals(0, straightDown.offsetY(), 1e-3);
        assertEquals(-(BODY_WIDTH / 2 + CLEARANCE), pose(-90).offsetX(), 1e-3);
    }

    @Test
    void theLimitIsThirtyFiveDegrees() {
        assertTrue(pose(34.9).cap() == DirectionLabelPose.Cap.RIGHT);
        assertTrue(pose(35.1).cap() == DirectionLabelPose.Cap.DOWN);
    }

    @Test
    void aLevelLabelClearsTheLineWithItsWholeBox() {
        var pose = pose(60);
        // The box's extent along the normal, so its nearest corner sits the clearance off the line.
        double radians = Math.toRadians(60);
        float nx = (float) Math.sin(radians);
        float ny = (float) -Math.cos(radians);
        float along = pose.offsetX() * nx + pose.offsetY() * ny;
        assertEquals(Math.abs(nx) * pose.width() / 2 + Math.abs(ny) * pose.height() / 2 + CLEARANCE, along, 1e-3);
    }
}

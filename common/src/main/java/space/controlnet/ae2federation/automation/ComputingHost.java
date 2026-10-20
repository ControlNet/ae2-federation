package space.controlnet.ae2federation.automation;

/**
 * A block entity that is a computing device: it runs the functions installed on it, for the domain it is in. Its
 * installations are kept in {@link ComputingDevices} under its ID.
 */
public interface ComputingHost extends AutomationDevice {
    /** The compute and bandwidth it has now, which its installations reserve. */
    Capacity capacity();

    /** Compute in CU and bandwidth in AE2 bytes per second (user decisions 2026-10-20). */
    record Capacity(long compute, long bandwidth) {
        /** The Creative Computing Device's. */
        public static final Capacity UNLIMITED = new Capacity(Long.MAX_VALUE, Long.MAX_VALUE);
        /** A device that cannot run anything, such as a structure that is not formed. */
        public static final Capacity NONE = new Capacity(0, 0);

        public boolean unlimited() {
            return equals(UNLIMITED);
        }
    }
}

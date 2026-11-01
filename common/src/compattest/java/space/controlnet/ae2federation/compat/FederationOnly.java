package space.controlnet.ae2federation.compat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A compatibility test that checks only what Federation does, such as its return ledger after a cancelled job, so one
 * joined Grid has nothing to compare. {@link ProductionGameTestRunner} skips it in a joined run ({@link NativeParity}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface FederationOnly {
    /** What the test checks that one Grid does not have. */
    String value();
}

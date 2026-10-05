package space.controlnet.ae2federation.compat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A compatibility test that changes something the whole level shares, such as the time of day, so no other test may run
 * beside it. {@link ProductionGameTestRunner} gives it a batch of its own.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface RunsAlone {
}

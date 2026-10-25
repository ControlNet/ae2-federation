package space.controlnet.ae2federation.crafting.projection;

/**
 * A projection's public {@code getLogic()}, for add-ons that look a provider's logic up by that name, as Neo ECO does to
 * decide whether its CPU may push many runs of a processing pattern as one scaled batch. Public, so such reflection can
 * call it on the proxy AE2 holds for a projection.
 */
public interface ProjectedProviderLogic {
    /** The real provider's logic, which the add-on may read; it still pushes through the projection. */
    Object getLogic();
}

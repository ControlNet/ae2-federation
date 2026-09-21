package space.controlnet.ae2federation.test.energy;

import appeng.api.networking.IGrid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.energy.EnergyCapabilityBinding;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.policy.PolicyKey;

public final class EnergyAuthorityReceipt {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnergyAuthorityReceipt.class);

    private EnergyAuthorityReceipt() {
    }

    public static void captureCurrent(String testId, String phase, EnergyCapabilityBinding binding) {
        requireSelected(testId);
        if (!binding.isCurrent() || binding.providerService() != binding.providerGrid().getEnergyService()) {
            throw new IllegalStateException("Energy binding does not expose current native authority");
        }
        LOGGER.info("AE2F_ENERGY_AUTHORITY testId={} selected={} phase={} consumerNetwork={} providerNetwork={} "
                        + "consumerGrid={} providerGrid={} providerService={} binding={} generation={} "
                        + "access=current capability=true",
                testId, selectedTest(), phase, network(binding.consumerGrid()), network(binding.providerGrid()),
                identity(binding.consumerGrid()), identity(binding.providerGrid()), identity(binding.providerService()),
                identity(binding), binding.revision().providerGeneration().value());
    }

    public static void captureUnavailable(String testId, String phase, EnergyBindingService service, PolicyKey key,
            IGrid consumerGrid, IGrid providerGrid) {
        requireSelected(testId);
        if (service.capability(key).isPresent()) {
            throw new IllegalStateException("Unavailable receipt requires absent directional capability");
        }
        LOGGER.info("AE2F_ENERGY_AUTHORITY testId={} selected={} phase={} consumerNetwork={} providerNetwork={} "
                        + "consumerGrid={} providerGrid={} providerService={} binding=none generation=none "
                        + "access=unavailable capability=false",
                testId, selectedTest(), phase, network(consumerGrid), network(providerGrid), identity(consumerGrid),
                identity(providerGrid), identity(providerGrid.getEnergyService()));
    }

    public static void captureOperation(String testId, String phase, IGrid consumerGrid, IGrid providerGrid,
            double requested, double accepted, double providerBefore, double providerAfter) {
        requireSelected(testId);
        LOGGER.info("AE2F_ENERGY_OPERATION testId={} selected={} phase={} consumerGrid={} providerGrid={} "
                        + "providerService={} requested={} accepted={} providerBefore={} providerAfter={}",
                testId, selectedTest(), phase, identity(consumerGrid), identity(providerGrid),
                identity(providerGrid.getEnergyService()), number(requested), number(accepted), number(providerBefore),
                number(providerAfter));
    }

    private static String network(IGrid grid) {
        return FabricRegistryAccess.confirmedNetworkId(grid).orElseThrow().toString();
    }

    private static String selectedTest() {
        return System.getProperty("ae2federation.testId", "");
    }

    private static void requireSelected(String testId) {
        if (!selectedTest().equals(testId)) {
            throw new IllegalArgumentException("Authority receipt test ID does not match the selected child");
        }
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static String number(double value) {
        return Double.toString(value);
    }
}

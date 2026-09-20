package space.controlnet.ae2federation.crafting.binding;

final class CraftingBackendUnavailableException extends RuntimeException {
    CraftingBackendUnavailableException(String message) {
        super(message);
    }
}

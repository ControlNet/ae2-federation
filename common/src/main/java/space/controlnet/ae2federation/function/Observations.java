package space.controlnet.ae2federation.function;

/**
 * What a program may observe. The game implements it over a network; each method may answer
 * {@link Value#UNAVAILABLE} when it cannot tell, as when the network is unloaded.
 */
public interface Observations {
    Value stock(ResourceRef resource);

    Value online();

    Value redstoneIn();
}

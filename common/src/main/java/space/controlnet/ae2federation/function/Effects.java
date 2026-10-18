package space.controlnet.ae2federation.function;

/** What an action may do. Queries never receive one, so a query cannot act however it is written. */
public interface Effects {
    void redstone(int level);

    void craft(ResourceRef resource, long amount);
}

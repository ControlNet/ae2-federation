package space.controlnet.ae2federation.storage.subscription;

public interface NativeStorageRegistration extends AutoCloseable {
    long id();

    boolean active();

    @Override
    void close();
}

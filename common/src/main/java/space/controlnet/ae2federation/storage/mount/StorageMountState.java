package space.controlnet.ae2federation.storage.mount;

public enum StorageMountState {
    MOUNTED,
    UNCHANGED,
    INACTIVE,
    BACKEND_UNREADY,
    SOURCE_CHANGED_UNSUPPORTED
}

package space.controlnet.ae2federation.storage.mount;

record StorageMountLevelCloseResult(boolean servicePresentBefore, int mountedProvidersBefore,
        int mountedProvidersRemoved, boolean serviceRemoved) {
}

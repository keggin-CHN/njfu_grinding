package com.examapp.service;

import java.util.function.Consumer;

public interface SyncCallback {
    void onDiscoveryStarted();
    void onServiceFound(String serviceName, String ipAddress, int port);
    void onServiceLost();
    void onConnectionEstablished();
    void onAuthRequired(Consumer<String> authCodeConsumer);
    void onSyncProgress(String message);
    void onSyncCompleted();
    void onError(String errorMessage);
    void onSyncCancelled();
}
package com.examapp.service;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.examapp.data.QuestionManager;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Subject;
import com.examapp.model.SyncData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncClient {

    private static final String TAG = "SyncClient";
    private static final String SERVICE_TYPE = "_njfu-grinding-sync._tcp.";

    private final Context context;
    private final NsdManager nsdManager;
    private final SyncCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;
    private boolean isDiscovering = false;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    public SyncClient(Context context, SyncCallback callback) {
        this.context = context;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.callback = callback;
    }

    public void startDiscovery() {
        if (isDiscovering) {
            Log.w(TAG, "Discovery already in progress.");
            return;
        }
        initializeDiscoveryListener();
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        isDiscovering = true;
        mainHandler.post(callback::onDiscoveryStarted);
    }

    public void stopDiscovery() {
        if (!isDiscovering) return;
        if (discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
        }
        isDiscovering = false;
        discoveryListener = null;
        resolveListener = null;
    }

    private void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service discovery success: " + service);
                initializeResolveListener();
                nsdManager.resolveService(service, resolveListener);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "service lost: " + service);
                mainHandler.post(callback::onServiceLost);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mainHandler.post(() -> callback.onError("无法启动服务发现: " + errorCode));
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };
    }

    private void initializeResolveListener() {
        resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed: " + errorCode);
                mainHandler.post(() -> callback.onError("无法解析服务: " + errorCode));
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Resolve Succeeded. " + serviceInfo);
                mainHandler.post(() -> callback.onServiceFound(serviceInfo.getServiceName(), serviceInfo.getHost().getHostAddress(), serviceInfo.getPort()));
                // Stop discovery once a service is found and resolved
                stopDiscovery();
                // Start the sync process
                connectAndSync(serviceInfo.getHost(), serviceInfo.getPort());
            }
        };
    }

    private void connectAndSync(InetAddress host, int port) {
        executor.submit(() -> {
            try {
                socket = new Socket(host, port);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                mainHandler.post(callback::onConnectionEstablished);

                // Directly proceed with sync after connection
                Log.d(TAG, "Connection established, starting sync process.");
                // 1. Get local data and send to server
                mainHandler.post(() -> callback.onSyncProgress("正在发送本地数据..."));
                QuestionManager qm = QuestionManager.getInstance(context);
                List<Subject> localSubjects = qm.getSubjects();
                List<ExamHistoryEntry> localHistory = qm.getExamHistoryEntries();
                SyncData localData = new SyncData(localSubjects, localHistory);
                Gson gson = new Gson();
                String jsonToServer = gson.toJson(localData);
                writer.println(jsonToServer);
                Log.d(TAG, "Sent local data to server.");

                // 2. Receive merged data from server
                mainHandler.post(() -> callback.onSyncProgress("正在接收合并后的数据..."));
                String jsonFromServer = reader.readLine();
                if (jsonFromServer == null) {
                    throw new IOException("Server closed connection unexpectedly.");
                }
                Type syncDataType = new TypeToken<SyncData>() {}.getType();
                SyncData mergedData = gson.fromJson(jsonFromServer, syncDataType);
                Log.d(TAG, "Received merged data from server.");

                // Recalculate stats on the client side to ensure data consistency
                if (mergedData != null && mergedData.getSubjects() != null) {
                    for (Subject subject : mergedData.getSubjects()) {
                        subject.recalculateStats();
                    }
                }

                // 3. Save merged data
                mainHandler.post(() -> callback.onSyncProgress("正在更新本地数据..."));
                java.util.Map<String, Subject> mergedSubjectsMap = mergedData.getSubjects().stream()
                        .collect(java.util.stream.Collectors.toMap(Subject::getId, java.util.function.Function.identity()));
                qm.replaceAllSubjects(mergedSubjectsMap);
                qm.replaceAllHistory(mergedData.getExamHistory());
                Log.d(TAG, "Saved merged data locally.");

                mainHandler.post(callback::onSyncCompleted);
                cleanup();
            } catch (Exception e) { // Catch Exception to include InterruptedException
                Log.e(TAG, "Connection or Sync failed", e);
                mainHandler.post(() -> callback.onError("同步失败: " + e.getMessage()));
                cleanup();
            }
        });
    }

    public void cleanup() {
        // Stop discovery first
        stopDiscovery();
        try {
            // Close network resources
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error during network cleanup", e);
        } finally {
            // Force shutdown the executor to stop any pending tasks
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
            Log.d(TAG, "SyncClient cleaned up.");
        }
    }
}

package com.examapp.service;

import com.examapp.data.DataMerger;
import com.examapp.data.QuestionManager;
import com.examapp.model.ExamHistoryEntry;
import com.examapp.model.Subject;
import com.examapp.model.SyncData;
import com.examapp.util.LogManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SyncServer {

    private static final String SERVICE_TYPE = "_njfu-grinding-sync._tcp.local.";
    private static final String SERVICE_NAME = "NJFU Grinding Sync Service";
    private static final int PORT = 12345;

    private ServerSocket serverSocket;
    private JmDNS jmdns;
    private ExecutorService pool;
    private volatile boolean isRunning = false;
    private final SyncServerCallback callback;
    private final QuestionManager questionManager;

    public SyncServer(SyncServerCallback callback) {
        this.callback = callback;
        this.questionManager = QuestionManager.getInstance();
    }

    public void start() {
        if (isRunning) {
            LogManager.warning("SyncServer is already running.");
            return;
        }

        if (pool == null || pool.isShutdown()) {
            pool = Executors.newCachedThreadPool();
        }

        pool.submit(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                String ipAddress = InetAddress.getLocalHost().getHostAddress();
                LogManager.info("SyncServer started on " + ipAddress + ":" + PORT);

                // Register JmDNS service
                jmdns = JmDNS.create(InetAddress.getLocalHost());
                ServiceInfo serviceInfo = ServiceInfo.create(SERVICE_TYPE, SERVICE_NAME, PORT, "NJFU Grinding Sync Service");
                jmdns.registerService(serviceInfo);
                LogManager.info("JmDNS service registered: " + serviceInfo);

                javafx.application.Platform.runLater(() -> callback.onServerStarted(ipAddress, PORT));

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        LogManager.info("Client connected: " + clientSocket.getInetAddress());
                        pool.submit(new ClientHandler(clientSocket));
                    } catch (IOException e) {
                        if (isRunning) {
                            LogManager.severe("Error accepting client connection", e);
                            javafx.application.Platform.runLater(() -> callback.onError("接受客户端连接失败: " + e.getMessage()));
                        }
                    }
                }
            } catch (IOException e) {
                LogManager.severe("Could not start SyncServer", e);
                javafx.application.Platform.runLater(() -> callback.onError("无法启动同步服务: " + e.getMessage()));
                stop(); // Ensure cleanup
            }
        });
    }

    public void stop() {
        if (!isRunning) return;
        
        isRunning = false;
        try {
            if (jmdns != null) {
                jmdns.unregisterAllServices();
                jmdns.close();
                LogManager.info("JmDNS service unregistered and closed.");
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                LogManager.info("SyncServer socket closed.");
            }
        } catch (IOException e) {
            LogManager.severe("Error closing server socket", e);
        } finally {
            if (pool != null && !pool.isShutdown()) {
                pool.shutdownNow();
            }
            LogManager.info("SyncServer stopped.");
            javafx.application.Platform.runLater(callback::onServerStopped);
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }

    public String getIpAddress() {
        if (serverSocket != null && serverSocket.isBound()) {
            return serverSocket.getInetAddress().getHostAddress();
        }
        return "N/A";
    }

    public int getPort() {
        if (serverSocket != null && serverSocket.isBound()) {
            return serverSocket.getLocalPort();
        }
        return 0;
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final Gson gson = new Gson();

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            javafx.application.Platform.runLater(callback::onClientConnected);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // 1. Read data from client
                javafx.application.Platform.runLater(() -> callback.onSyncProgress("正在从客户端接收数据..."));
                String jsonFromClient = reader.readLine();
                if (jsonFromClient == null) {
                    throw new IOException("Client disconnected before sending data.");
                }
                Type syncDataType = new TypeToken<SyncData>() {}.getType();
                SyncData remoteData = gson.fromJson(jsonFromClient, syncDataType);
                LogManager.info("Received " + (remoteData.getSubjects() != null ? remoteData.getSubjects().size() : 0) + " subjects and " + (remoteData.getExamHistory() != null ? remoteData.getExamHistory().size() : 0) + " history entries from client.");

                // 2. Load local data
                List<Subject> localSubjects = questionManager.getSubjects();
                List<ExamHistoryEntry> localHistory = questionManager.getExamHistoryEntries();
                SyncData localData = new SyncData(localSubjects, localHistory);

                // 3. Merge data
                javafx.application.Platform.runLater(() -> callback.onSyncProgress("正在合并数据..."));
                DataMerger merger = new DataMerger();
                SyncData mergedData = merger.merge(localData, remoteData);
                LogManager.info("Data merged successfully.");

                // 4. Save merged data locally
                java.util.Map<String, Subject> mergedSubjectsMap = mergedData.getSubjects().stream()
                        .collect(java.util.stream.Collectors.toMap(Subject::getId, java.util.function.Function.identity()));
                questionManager.replaceAllSubjects(mergedSubjectsMap);
                questionManager.replaceAllHistory(mergedData.getExamHistory());
                LogManager.info("Merged data saved locally.");

                // 5. Send merged data back to client
                javafx.application.Platform.runLater(() -> callback.onSyncProgress("正在将合并后的数据发送回客户端..."));
                String jsonToClient = gson.toJson(mergedData);
                writer.println(jsonToClient);
                LogManager.info("Sent merged data back to client.");

                javafx.application.Platform.runLater(callback::onSyncCompleted);

            } catch (Exception e) {
                LogManager.severe("Error during sync with client " + clientSocket.getInetAddress(), e);
                javafx.application.Platform.runLater(() -> callback.onError("同步出错: " + e.getMessage()));
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    LogManager.severe("Error closing client socket", e);
                }
                LogManager.info("Client disconnected: " + clientSocket.getInetAddress());
                javafx.application.Platform.runLater(callback::onClientDisconnected);
            }
        }
    }
}

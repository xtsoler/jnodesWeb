package net.jnodesWeb;

import dataManagement.storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jnodes3clientse.Main;

public class MapRuntimeManager {

    // Current map id (if running)
    private static volatile String mapId = null;
    private static volatile boolean mapRunning = false;

    private static final Object LOCK = new Object();
    private static final AtomicLong lastActivity = new AtomicLong(0L);
    private static ScheduledExecutorService scheduler;

    private static final long IDLE_TIMEOUT_MS = 20_000L; // 90 seconds

    // Call on each /map.png request
    public static void noteActivity() {
        lastActivity.set(System.currentTimeMillis());
    }

    // Call on each /map.png request to ensure map is running
    public static void ensureStarted() {
        synchronized (LOCK) {
            if (mapRunning) {
                return;
            }

            try {
                String catalinaBase = System.getProperty("catalina.base");
                Path mapFile = Path.of(catalinaBase, "conf", "map.json");
                System.out.println("[MapRuntimeManager] Starting map, looking for: " + mapFile);

                if (!Files.exists(mapFile)) {
                    System.out.println("[MapRuntimeManager] map.json not found, cannot start map.");
                    return;
                }

                Path encryptionFile = Path.of(catalinaBase, "conf", "encryption.key");
                System.out.println("[MapRuntimeManager] Looking for: " + encryptionFile);

                if (!Files.exists(encryptionFile)) {
                    System.out.println("[MapRuntimeManager] encryption.key not found, cannot start map.");
                    return;
                }

                Main.encryption_password = java.nio.file.Files.readString(encryptionFile).trim();

                // create map / start pollers via storage
                String id = storage.addMapFromFile(mapFile.toString());
                mapId = id;
                mapRunning = true;
                lastActivity.set(System.currentTimeMillis());

                System.out.println("[MapRuntimeManager] Map started, id=" + mapId);

                // lazily start idle-check scheduler
                if (scheduler == null) {
                    scheduler = Executors.newSingleThreadScheduledExecutor();
                    scheduler.scheduleAtFixedRate(MapRuntimeManager::checkIdle, 30, 30, TimeUnit.SECONDS);
                }

            } catch (Exception ex) {
                System.err.println("[MapRuntimeManager] Error starting map");
                ex.printStackTrace();
            }
        }
    }

    // Get a fresh mapData snapshot for rendering
    public static message.mapData getCurrentMap() {
        synchronized (LOCK) {
            if (!mapRunning || mapId == null) {
                return null;
            }
            return storage.getMapDataById(mapId);
        }
    }

    // Optional: to be called from a context listener on app shutdown, if you add one later
    public static void shutdown() {
        synchronized (LOCK) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
            if (mapRunning && mapId != null) {
                System.out.println("[MapRuntimeManager] Shutdown: deleting map id=" + mapId);
                storage.deleteMap(mapId);
            }
            mapRunning = false;
            mapId = null;
        }
    }

    // ---- internal ----

    private static void checkIdle() {
        long now = System.currentTimeMillis();
        long idleFor = now - lastActivity.get();

        if (idleFor > IDLE_TIMEOUT_MS) {
            synchronized (LOCK) {
                long idleCheck = System.currentTimeMillis() - lastActivity.get();
                if (mapRunning && idleCheck > IDLE_TIMEOUT_MS) {
                    System.out.println("[MapRuntimeManager] Idle for " + idleCheck +
                            " ms, stopping map id=" + mapId);
                    if (mapId != null) {
                        storage.deleteMap(mapId);
                    }
                    mapRunning = false;
                    mapId = null;
                }
            }
        }
    }
}

package net.jnodesWeb;

import dataManagement.storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import jnodes3clientse.Main;
import java.util.Set;
import java.util.HashSet;

public class MapRuntimeManager {

    // Backward compatibility: first successfully loaded map id (if any).
    private static volatile String mapId = null;
    private static volatile boolean mapRunning = false;

    private static final Object LOCK = new Object();
    private static final AtomicLong lastActivity = new AtomicLong(0L);
    private static ScheduledExecutorService scheduler;

    private static final long IDLE_TIMEOUT_MS = 20_000L; // 20 seconds

    // Key: base filename without extension (e.g. "office" from "office.json")
    // Value: storage map id returned from storage.addMapFromFile(...)
    private static final ConcurrentHashMap<String, String> mapIdsByName = new ConcurrentHashMap<>();

    public static void noteActivity() {
        lastActivity.set(System.currentTimeMillis());
    }

    public static void ensureStarted() {
        synchronized (LOCK) {
            if (mapRunning) {
                return;
            }

            try {
                String catalinaBase = System.getProperty("catalina.base");
                Path jnodesConfDir = Path.of(catalinaBase, "conf", "jnodesWeb");

                // Ensure conf/jnodesWeb exists
                if (!Files.exists(jnodesConfDir)) {
                    Files.createDirectories(jnodesConfDir);
                    System.out.println("[MapRuntimeManager] Created directory: " + jnodesConfDir);
                }

                // encryption.key is required
                Path encryptionFile = jnodesConfDir.resolve("encryption.key");
                System.out.println("[MapRuntimeManager] Looking for: " + encryptionFile);

                if (!Files.exists(encryptionFile)) {
                    System.out.println("[MapRuntimeManager] encryption.key not found in jnodesWeb folder, cannot start maps.");
                    return;
                }

                Main.encryption_password = Files.readString(encryptionFile).trim();

                System.out.println("[MapRuntimeManager] Starting maps, loading *.json from: " + jnodesConfDir);

                // reset state before loading
                mapIdsByName.clear();
                mapId = null;

                int loaded = 0;

                try (Stream<Path> stream = Files.list(jnodesConfDir)) {
                    for (Path p : (Iterable<Path>) stream::iterator) {
                        if (p == null) {
                            continue;
                        }

                        String fn = p.getFileName().toString();
                        if (!fn.toLowerCase().endsWith(".json")) {
                            continue;
                        }

                        String baseName = stripJsonExtension(fn);

                        try {
                            String id = storage.addMapFromFile(p.toString());
                            if (id != null) {
                                mapIdsByName.put(baseName, id);
                                loaded++;

                                // backward compatible default = first loaded map
                                if (mapId == null) {
                                    mapId = id;
                                }

                                System.out.println("[MapRuntimeManager] Map loaded: " + fn + " -> id=" + id);
                            }
                        } catch (Exception ex) {
                            System.err.println("[MapRuntimeManager] Failed to load map file: " + p);
                            ex.printStackTrace();
                        }
                    }
                }

                if (loaded <= 0) {
                    System.out.println("[MapRuntimeManager] No .json map files found in jnodesWeb folder, cannot start maps.");
                    mapRunning = false;
                    mapId = null;
                    mapIdsByName.clear();
                    return;
                }

                mapRunning = true;
                lastActivity.set(System.currentTimeMillis());

                System.out.println("[MapRuntimeManager] Maps started, count=" + loaded);

                if (scheduler == null) {
                    scheduler = Executors.newSingleThreadScheduledExecutor();
                    scheduler.scheduleAtFixedRate(MapRuntimeManager::checkIdle, 30, 30, TimeUnit.SECONDS);
                }

            } catch (Exception ex) {
                System.err.println("[MapRuntimeManager] Error starting maps");
                ex.printStackTrace();
            }
        }
    }

    /**
     * Main API for your servlet: get map by base filename (without ".json"). If
     * it's not loaded, returns null (caller should 404).
     */
    public static message.mapData getMapByName(String mapName) {
        if (mapName == null || mapName.isEmpty()) {
            return null;
        }

        synchronized (LOCK) {
            if (!mapRunning) {
                return null;
            }

            String id = mapIdsByName.get(mapName);
            if (id == null) {
                return null;
            }

            return storage.getMapDataById(id);
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }

            if (mapRunning) {
                System.out.println("[MapRuntimeManager] Shutdown: deleting maps count=" + mapIdsByName.size());
                for (String id : mapIdsByName.values()) {
                    try {
                        if (id != null) {
                            storage.deleteMap(id);
                        }
                    } catch (Exception ex) {
                        System.err.println("[MapRuntimeManager] Failed to delete map id=" + id);
                        ex.printStackTrace();
                    }
                }
            }

            mapIdsByName.clear();
            mapRunning = false;
            mapId = null;
        }
    }

    private static void checkIdle() {
        long now = System.currentTimeMillis();
        long idleFor = now - lastActivity.get();

        if (idleFor <= IDLE_TIMEOUT_MS) {
            return;
        }

        synchronized (LOCK) {
            long idleCheck = System.currentTimeMillis() - lastActivity.get();
            if (!mapRunning || idleCheck <= IDLE_TIMEOUT_MS) {
                return;
            }

            System.out.println("[MapRuntimeManager] Idle for " + idleCheck
                    + " ms, stopping maps (count=" + mapIdsByName.size() + ")");

            for (String id : mapIdsByName.values()) {
                try {
                    if (id != null) {
                        storage.deleteMap(id);
                    }
                } catch (Exception ex) {
                    System.err.println("[MapRuntimeManager] Failed to delete map id=" + id + " during idle stop");
                    ex.printStackTrace();
                }
            }

            mapIdsByName.clear();
            mapRunning = false;
            mapId = null;
        }
    }

    private static String stripJsonExtension(String filename) {
        if (filename == null) {
            return null;
        }
        if (filename.toLowerCase().endsWith(".json")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }

    public static Set<String> getLoadedMapNames() {
        synchronized (LOCK) {
            return new HashSet<>(mapIdsByName.keySet());
        }
    }

}

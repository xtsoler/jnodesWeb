package net.jnodesWeb;

import dataManagement.storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import jnodes3clientse.Main;

public class MapRuntimeManager {

    // Backward compatibility: keep the original "single map" fields.
    // We will set mapId to the first successfully loaded map (if any).
    private static volatile String mapId = null;
    private static volatile boolean mapRunning = false;

    private static final Object LOCK = new Object();
    private static final AtomicLong lastActivity = new AtomicLong(0L);
    private static ScheduledExecutorService scheduler;

    private static final long IDLE_TIMEOUT_MS = 20_000L; // 20 seconds

    // ----------------------------------------------------------------------
    // NEW: Support multiple maps (load all .json files in conf/jnodesWeb)
    // ----------------------------------------------------------------------
    // Key: base filename without extension (e.g. "map1" from "map1.json")
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

                // Ensure conf/jnodesWeb exists
                Path jnodesConfDir = Path.of(catalinaBase, "conf", "jnodesWeb");
                try {
                    if (!Files.exists(jnodesConfDir)) {
                        Files.createDirectories(jnodesConfDir);
                        System.out.println("[MapRuntimeManager] Created directory: " + jnodesConfDir);
                    }
                } catch (Exception e) {
                    System.err.println("[MapRuntimeManager] Failed to create directory: " + jnodesConfDir);
                    e.printStackTrace();
                    return;
                }

                // NOTE:
                // Previously we required "map.json". Now we load ALL ".json" files in this folder.
                // We'll still refuse to start if there are no JSON files at all.
                System.out.println("[MapRuntimeManager] Starting maps, looking for *.json in: " + jnodesConfDir);

                // encryption.key is still required (same as before)
                Path encryptionFile = jnodesConfDir.resolve("encryption.key");
                System.out.println("[MapRuntimeManager] Looking for: " + encryptionFile);

                if (!Files.exists(encryptionFile)) {
                    System.out.println("[MapRuntimeManager] encryption.key not found in jnodesWeb folder, cannot start map.");
                    return;
                }

                Main.encryption_password = Files.readString(encryptionFile).trim();

                // ------------------------------------------------------------------
                // NEW: Load all JSON map files
                // ------------------------------------------------------------------
                int loaded = 0;
                try (Stream<Path> stream = Files.list(jnodesConfDir)) {
                    for (Path p : (Iterable<Path>) stream::iterator) {
                        if (p == null) continue;

                        String fn = p.getFileName().toString();

                        // Only load ".json" files. (We intentionally do NOT try to parse other files.)
                        if (!fn.toLowerCase().endsWith(".json")) {
                            continue;
                        }

                        String baseName = stripJsonExtension(fn);

                        try {
                            // create map / start pollers via storage
                            String id = storage.addMapFromFile(p.toString());
                            if (id != null) {
                                mapIdsByName.put(baseName, id);
                                loaded++;

                                // Backward compatibility: set "mapId" to the first loaded map.
                                if (mapId == null) {
                                    mapId = id;
                                }

                                System.out.println("[MapRuntimeManager] Map loaded: " + fn + " -> id=" + id);

                                // NEW: HC support logging you asked about earlier is in linkMaintainer,
                                // not here. This log is purely about loading map files.
                            }
                        } catch (Exception ex) {
                            System.err.println("[MapRuntimeManager] Failed to load map file: " + p);
                            ex.printStackTrace();
                        }
                    }
                }

                if (loaded <= 0) {
                    System.out.println("[MapRuntimeManager] No .json map files found in jnodesWeb folder, cannot start map.");
                    mapId = null;
                    mapRunning = false;
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
                System.err.println("[MapRuntimeManager] Error starting map");
                ex.printStackTrace();
            }
        }
    }

    /**
     * Backward-compatible: return the "default" map (first loaded), same behavior as before.
     */
    public static message.mapData getCurrentMap() {
        synchronized (LOCK) {
            if (!mapRunning || mapId == null) {
                return null;
            }
            return storage.getMapDataById(mapId);
        }
    }

    // ----------------------------------------------------------------------
    // NEW: Multi-map getter by name (base filename without ".json")
    // ----------------------------------------------------------------------
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

    // ----------------------------------------------------------------------
    // NEW: Serve a PNG image with the same base name as the .json file.
    // Example:
    //   "office.json" -> "office.png"
    //
    // This method returns the PNG bytes so your servlet/controller can write
    // them directly to the HTTP response (Content-Type: image/png).
    // ----------------------------------------------------------------------
    public static byte[] getPngImageBytesByName(String mapName) {
        if (mapName == null || mapName.isEmpty()) {
            return null;
        }

        synchronized (LOCK) {
            try {
                String catalinaBase = System.getProperty("catalina.base");
                Path jnodesConfDir = Path.of(catalinaBase, "conf", "jnodesWeb");

                Path pngFile = jnodesConfDir.resolve(mapName + ".png");
                if (!Files.exists(pngFile)) {
                    // Keep it quiet and predictable: just return null if not found.
                    // Caller can return 404.
                    return null;
                }
                return Files.readAllBytes(pngFile);
            } catch (Exception e) {
                System.err.println("[MapRuntimeManager] Failed to read PNG for mapName=" + mapName);
                e.printStackTrace();
                return null;
            }
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }

            // NEW: delete all loaded maps
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

        if (idleFor > IDLE_TIMEOUT_MS) {
            synchronized (LOCK) {
                long idleCheck = System.currentTimeMillis() - lastActivity.get();
                if (mapRunning && idleCheck > IDLE_TIMEOUT_MS) {
                    System.out.println("[MapRuntimeManager] Idle for " + idleCheck
                            + " ms, stopping maps (count=" + mapIdsByName.size() + ")");

                    // NEW: delete all maps, not just one
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
        }
    }

    // ----------------------------------------------------------------------
    // NEW: small helpers
    // ----------------------------------------------------------------------
    private static String stripJsonExtension(String filename) {
        if (filename == null) return null;
        String fn = filename;
        if (fn.toLowerCase().endsWith(".json")) {
            return fn.substring(0, fn.length() - 5);
        }
        return fn;
    }
}

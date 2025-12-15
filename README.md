# jnodesWeb

**jnodesWeb** is the web-based visualization component of the **jnodes** ecosystem.  
It exposes live-rendered network topology maps as images and provides a minimal web UI
for browsing and viewing those maps in real time.

The project is designed to run inside **Apache Tomcat** and integrates directly with
the core `jnodesOne` runtime and storage layer.

---

## Features

- Live rendering of network maps to PNG images
- Multiple map support (loaded from JSON files)
- Stateless HTTP endpoints
- Lightweight HTML/JS frontend
- Idle-time shutdown to conserve resources
- Designed for monitoring dashboards, wall displays, and embedding

---

## Architecture Overview

```
jnodesWeb
 ├─ Apache Tomcat (Servlet container)
 │   ├─ /image?map=NAME   → PNG snapshot of a map
 │   └─ /list             → JSON list of available maps
 │
 ├─ MapRuntimeManager
 │   ├─ Loads *.json map files
 │   ├─ Manages map lifecycle
 │   └─ Handles idle shutdown
 │
 └─ jnodesOne / storage
     └─ Core map + SNMP runtime
```

---

## Endpoints

### `GET /list`

Returns all available maps currently loaded by the runtime.

```json
{
  "maps": ["office", "datacenter", "home"]
}
```

---

### `GET /image?map=NAME`

Returns a **PNG image** of the requested map.

- `map` is the base filename (without `.json`)
- Returns `404` if the map is not loaded
- Returns `400` if `map` is missing

Example:

```
/image?map=office
```

---

## Frontend Behavior

The provided `index.html` implements two modes:

### 1. List Mode (no parameters)

```
/index.html
```

- Shows a centered list of available maps
- No map is rendered
- Clicking a map opens it in a **new tab**

### 2. Map Mode

```
/index.html?map=office
```

- Displays the live-rendered map
- Auto-refreshes every few seconds
- A small **home icon** in the top-left returns to the map list

---

## Map Configuration

Maps are loaded from:

```
$CATALINA_BASE/conf/jnodesWeb/
```

Required files:

- `encryption.key` — encryption password for jnodesOne
- `*.json` — map definition files (one map per file)

Example:

```
conf/jnodesWeb/
 ├─ encryption.key
 ├─ office.json
 ├─ datacenter.json
 └─ home.json
```

The base filename (e.g. `office.json`) becomes the map name (`office`).

---

## Runtime Behavior

- Maps are loaded lazily on first request
- Runtime activity is tracked
- If no requests occur for a configurable idle period, all maps are unloaded automatically

This allows jnodesWeb to remain lightweight when not actively used.

---

## Build & Deployment

### Requirements

- Java 17+
- Apache Tomcat 10+
- Maven 3.8+

### Build

```bash
mvn clean package
```

Deploy the generated WAR to Tomcat:

```
$CATALINA_BASE/webapps/jnodesWeb.war
```

---

## Dependencies

- `jnodesOne` (core runtime)
- SNMP4J
- Jakarta Servlet API
- Java AWT (for image rendering)

---

## Design Goals

- Use jnodesOne to design the map
- Minimal UI, no frameworks
- Server-side rendering for predictable output
- Easy embedding into dashboards or iframes
- Clear separation between runtime and presentation

---

## Related Projects

- **jnodesOne** — core runtime and desktop application https://github.com/xtsoler/jnodesOne
- **jnodesWeb** — web visualization layer for tomcat/jetty

---

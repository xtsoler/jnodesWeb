# jnodesWeb

**jnodesWeb** is the web-based visualization component of the **jnodes** ecosystem.
See https://github.com/xtsoler/jnodesOne for the desktop map editor application.
It exposes live-rendered network topology maps as images and provides a minimal web UI
for browsing and viewing those maps in real time.

The project is designed to run inside **Apache Tomcat** (should also work with jetty) and integrates directly with
the core `jnodesOne` runtime and storage layer.

Live demo: https://demo.jnodes.net

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

## Deployment

In a debian 12 system for example:
sudo apt install openjdk-17-jre
sudo useradd -r -m -U -d /opt/tomcat -s /bin/false tomcat
cd /tmp
wget https://dlcdn.apache.org/tomcat/tomcat-11/v11.0.15/bin/apache-tomcat-11.0.15.tar.gz # *adjust for later verion
sudo mkdir -p /opt/tomcat
sudo tar xzf apache-tomcat-11.0.15.tar.gz -C /opt/tomcat --strip-components=1
sudo chown -R tomcat:tomcat /opt/tomcat
sudo nano /etc/systemd/system/tomcat.service

Contents of the service file:
[Unit]
Description=Apache Tomcat 11
After=network.target

[Service]
Type=forking

User=tomcat
Group=tomcat

Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
Environment="CATALINA_HOME=/opt/tomcat"
Environment="CATALINA_BASE=/opt/tomcat"
Environment="CATALINA_OPTS=-Xms256M -Xmx1024M"
Environment="JAVA_OPTS=-Djava.awt.headless=true"

ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh

Restart=on-failure

[Install]
WantedBy=multi-user.target

sudo systemctl daemon-reexec
sudo systemctl daemon-reload
sudo systemctl enable --now tomcat
cd /opt/tomcat/webapps
sudo -u tomcat wget https://github.com/xtsoler/jnodesWeb/releases/latest/download/jnodesWeb.war

Then place your icons folder from jnodesOne into /opt/tomcat/conf/jnodesWeb/ as well as the map json files and the encryption.key file.

/opt/tomcat/conf/jnodesWeb# ls -lah
total 52K
drwxr-x--- 3 tomcat tomcat 4.0K Dec 15 16:46 .
drwx------ 4 tomcat tomcat 4.0K Dec 15 16:42 ..
-rw-r--r-- 1 root   root     10 Dec 15 16:44 encryption.key
drwxr-xr-x 2 root   root   4.0K Dec 15 16:46 icons
-rw-r--r-- 1 root   root   9.1K Dec 15 16:44 map.json
-rw-r--r-- 1 root   root    21K Dec 15 16:44 map_uh.json

Navigating to http://your_server:8080/jnodesWeb/ should now work

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

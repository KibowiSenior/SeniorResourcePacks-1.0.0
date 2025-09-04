
---

# SeniorResourcePacks

SeniorResourcePacks is a Bukkit/Spigot plugin designed to host Minecraft resource packs locally with zero hassle. Simply drop your .zip file into the plugin’s folder, and it automatically provides a direct download link that Minecraft can use.

No need for third-party hosting – the plugin runs its own lightweight HTTP server, allowing your server to deliver resource packs directly to players. Supports packs up to 1 GB, loads fast, and auto-detects the correct SHA-1 hash for client compatibility.

---

## Requirements

* **Minecraft Server**: Spigot or Bukkit 1.21+
* **Java Version**: Java 19 or higher
* **RAM**: Minimum 1GB available
* **Network**: Open HTTP port (default: 8080)

---

## Installation

### Step 1: Download

1. Download `senior-resource-packs-1.0.0.jar` from the releases
2. Place the JAR file in your server's `plugins/` folder

### Step 2: Start

1. Start or restart your Minecraft server
2. The plugin will automatically:

   * Create the `plugins/senior-resource-packs/` folder
   * Generate default `config.yml`
   * Create `pack/` folder for resource packs
   * Start the HTTP server

### Step 3: Verify

Check your server console for messages like:

```
[INFO]: [senior-resource-packs] Detected external IP: [your-ip]  
[INFO]: [senior-resource-packs] Simple HTTP server started on 0.0.0.0:8080  
[INFO]: [senior-resource-packs] Senior Resource Packs plugin has been enabled!  
[INFO]: [senior-resource-packs] HTTP server running on: http://[your-ip]:[port]/  
```

---

## Configuration

### Default config.yml

```yaml
server_ip: ""  
http_port: 8080  
force_pack: false  
auto_apply_all_worlds: true  
resource_packs:  
  pack1: ""  
  pack2: ""  
  pack3: ""  
messages:  
  resource_pack_applied: "§aResource pack applied successfully!"  
  resource_pack_failed: "§cFailed to apply resource pack!"  
  reload_success: "§aConfiguration reloaded successfully!"  
  no_permission: "§cYou don't have permission to use this command!"  
```

### Options

| Setting                 | Description                        | Default | Values      |
| ----------------------- | ---------------------------------- | ------- | ----------- |
| `server_ip`             | Server IP (auto-detected if empty) | `""`    | IP or empty |
| `http_port`             | HTTP server port                   | `8080`  | 1–65535     |
| `force_pack`            | Force pack acceptance              | `false` | true/false  |
| `auto_apply_all_worlds` | Auto-apply packs to all worlds     | `true`  | true/false  |
| `resource_packs`        | Pack slots                         | -       | Pack names  |

---

## Commands

| Command              | Description                |
| -------------------- | -------------------------- |
| `/rp reload`         | Reload configuration       |
| `/rp applyall`       | Apply packs to all players |
| `/rp info`           | Show plugin info           |
| `/rp apply <player>` | Apply packs to a player    |
| `/rp list`           | Show web interface URL     |

---

## Web Interface

### Access

* Open browser → `http://[server-ip]:[port]/`
* Default: `http://[server-ip]:8080/`

### Features

* **Dashboard**: Server status, port, IP, loaded packs
* **Pack Manager**: List, status, downloads, size info
* **Config Panel**: Change port, force pack, auto apply, reload plugin
* **Responsive**: Works on mobile, tablet, desktop

---

## Resource Packs

### Adding Packs

**File Upload**

* Place `.zip` packs in `plugins/senior-resource-packs/pack/`
* Restart or `/rp reload`

**Web Interface**

* Use upload or file manager
* Reload plugin

### Requirements

* Format: `.zip` only
* Location: `pack/` folder
* Names: Descriptive (avoid spaces)
* Size: No strict limit (consider player bandwidth)

### Loading Process

* Packs scanned on startup
* `.zip` files get URLs: `http://[ip]:[port]/pack.zip`
* Players receive packs on join if auto-apply is enabled

---

## Troubleshooting

### Download Issues

* Failed download → check IP, HTTP server, firewall, manual URL test

### Wrong IP

* Detected internal IP → set `server_ip` in config manually

### HTTP Errors

* Server won’t start → check port conflicts, permissions, other plugins

### Pack Issues

* Not loading → confirm `.zip`, permissions, use `/rp reload`

### Console Logs

* Always check startup and error logs for info

---

## Advanced

* **Manual IP**: `server_ip: "123.456.789.012"`
* **Custom Port**: `http_port: 9080`
* **Force Pack**: `force_pack: true`
* **Performance**: Uses thread pool, auto cleanup, MIME serving

---

## Support

* Check console logs
* Use web interface
* Test pack URL directly
* Verify permissions

**Plugin Info**

* Version: 1.0.0
* Author: YourSenior
* Powered by: CloudNord.net
* MC Version: 1.21+
* Java: 19+

---

## License & Credits

Powered by CloudNord.net – professional resource pack hosting.

*Made with ❤️ by CloudNord.net*

---

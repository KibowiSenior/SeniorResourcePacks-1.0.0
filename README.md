# Senior Resource Packs Plugin

**A professional Minecraft Spigot/Bukkit resource pack plugin with automatic HTTP server, and web management interface.**
---

##  Requirements

- **Minecraft Server**: Spigot or Bukkit 1.21+
- **Java Version**: Java 19 or higher
- **RAM**: Minimum 1GB available
- **Network**: Open HTTP port (default: 8080)

---

##  Installation

### Step 1: Download Plugin
1. Download `senior-resource-packs-1.0.0.jar` from the releases
2. Place the JAR file in your server's `plugins/` folder

### Step 2: Start Server
1. Start or restart your Minecraft server
2. The plugin will automatically:
   - Create the `plugins/senior-resource-packs/` folder
   - Generate default `config.yml`
   - Create `pack/` folder for resource packs
   - Start the HTTP server

### Step 3: Verify Installation
Check your server console for these messages:
```
[INFO]: [senior-resource-packs] Detected external IP: [your-ip]
[INFO]: [senior-resource-packs] Simple HTTP server started on 0.0.0.0:8080
[INFO]: [senior-resource-packs] Senior Resource Packs plugin has been enabled!
[INFO]: [senior-resource-packs] HTTP server running on: http://[your-ip]:[port]/
```

---

##  Configuration

### Default config.yml
```yaml
# Senior Resource Packs Configuration
# Server IP (leave empty for auto-detection)
server_ip: ""

# HTTP server port
http_port: 8080

# Force players to accept resource packs
force_pack: false

# Automatically apply packs to all worlds
auto_apply_all_worlds: true

# Resource pack slots (up to 3)
resource_packs:
  pack1: ""
  pack2: ""
  pack3: ""

# Custom messages
messages:
  resource_pack_applied: "§aResource pack applied successfully!"
  resource_pack_failed: "§cFailed to apply resource pack!"
  reload_success: "§aConfiguration reloaded successfully!"
  no_permission: "§cYou don't have permission to use this command!"
```

### Configuration Options

| Setting | Description | Default | Valid Values |
|---------|-------------|---------|--------------|
| `server_ip` | Server IP address (auto-detected if empty) | `""` | IP address or empty |
| `http_port` | HTTP server port | `8080` | 1-65535 |
| `force_pack` | Force pack acceptance | `false` | `true`/`false` |
| `auto_apply_all_worlds` | Auto-apply to all worlds | `true` | `true`/`false` |
| `resource_packs` | Pack slot configurations | - | Pack names |

---

##  Commands

### Player Commands
*No player commands available - packs are applied automatically*

### Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/srp reload` | `srp.admin` | Reload plugin configuration |
| `/srp status` | `srp.admin` | Show plugin status |
| `/srp info` | `srp.admin` | Display plugin information |
| `/srp apply <player>` | `srp.admin` | Manually apply packs to a player |
| `/srp web` | `srp.admin` | Get web interface URL |

### Permission Nodes

| Permission | Description | Default |
|------------|-------------|---------|
| `srp.admin` | Full plugin access | `op` |
| `srp.reload` | Reload configuration | `op` |
| `srp.status` | View status | `op` |

---

##  Web Interface

### Accessing the Web Interface
1. Open your web browser
2. Navigate to: `http://[your-server-ip]:[port]/`
3. Default: `http://[your-server-ip]:8080/`

### Web Interface Features

####  Server Status Dashboard
- **HTTP Server Status** - Running/Stopped indicator
- **Port Information** - Current HTTP port
- **Loaded Packs** - Number of active packs
- **Server IP** - Detected external IP

####  Resource Pack Management
- **Pack List** - View all available packs
- **Pack Status** - Loaded/Available indicators
- **Download Links** - Direct download URLs
- **File Sizes** - Pack size information

#### ⚙ Configuration Panel
- **HTTP Port** - Change server port
- **Force Pack** - Toggle forced acceptance
- **Auto Apply** - Toggle automatic application
- **Save Settings** - Apply configuration changes
- **Reload Plugin** - Hot reload without restart

### Mobile Support
The web interface is fully responsive and works on:
-  **Mobile Phones** - Optimized single-column layout
-  **Tablets** - Balanced 2-column design
-  **Desktop** - Full-featured interface
-  **Large Screens** - Professional appearance

---

##  Resource Pack Management

### Adding Resource Packs

#### Method 1: File Upload
1. Place `.zip` resource pack files in: `plugins/senior-resource-packs/pack/`
2. Restart server or use `/srp reload`
3. Packs will be automatically detected and loaded

#### Method 2: Web Interface
1. Access the web interface
2. View current pack folder location
3. Upload files via FTP/file manager
4. Click "Reload Plugin" button

### Pack Requirements
- **Format**: `.zip` files only
- **Location**: `plugins/senior-resource-packs/pack/` folder
- **Naming**: Use descriptive names (no spaces recommended)
- **Size**: No strict limit (consider player download time)

### Pack Loading Process
1. Plugin scans the `pack/` folder on startup
2. All `.zip` files are automatically loaded
3. Packs are assigned URLs: `http://[ip]:[port]/[pack-name].zip`
4. Players receive packs on join (if auto-apply enabled)

---

##  Troubleshooting

### Common Issues

####  Resource Packs Not Downloading
**Problem**: Players see "Failed to download resource pack"
**Solutions**:
1. Check server console for detected IP
2. Verify HTTP server is running
3. Test URL manually in browser
4. Check firewall settings for HTTP port
5. Set `server_ip` manually in config if auto-detection fails

####  Wrong IP Detected
**Problem**: Plugin detects internal IP instead of external
**Solutions**:
1. Manually set `server_ip` in config.yml:
   ```yaml
   server_ip: "your.external.ip.here"
   ```
2. Restart server or reload configuration
3. Use `/srp status` to verify new IP

####  HTTP Server Won't Start
**Problem**: "Failed to start simple HTTP server"
**Solutions**:
1. Check if port is already in use
2. Change `http_port` in config.yml
3. Verify server has permission to bind to port
4. Check for conflicting plugins

####  Packs Not Loading
**Problem**: Packs in folder but not showing as loaded
**Solutions**:
1. Verify files are `.zip` format
2. Check file permissions (readable by server)
3. Use `/srp reload` to refresh pack list
4. Check console for error messages

### Debug Information

#### Enable Debug Logging
Add to `config.yml`:
```yaml
debug: true
```

#### Check Server Status
Use command: `/srp status`
Output includes:
- HTTP server status
- Loaded pack count
- Base URL
- Port information

#### Console Messages
Monitor console for:
- IP detection messages
- HTTP server startup
- Pack loading information
- Error messages

---

##  Advanced Settings

### Manual IP Configuration
For servers behind NAT or with complex networking:
```yaml
server_ip: "123.456.789.012"  # Your external IP
```

### Custom Port Configuration
Change HTTP port if default conflicts:
```yaml
http_port: 9080  # Use different port
```

### Force Pack Settings
Require players to accept resource packs:
```yaml
force_pack: true
```

### World-Specific Settings
Currently auto-applies to all worlds. Future versions will support per-world configuration.

### Performance Tuning
The plugin uses:
- Thread pool for HTTP requests (5 threads)
- Automatic cleanup of resources
- Efficient pack serving with proper MIME types

---

##  Support & Contact

### Getting Help
1. **Check Console Logs** - Most issues show detailed error messages
2. **Use Web Interface** - Real-time status information
3. **Test Connectivity** - Try accessing pack URLs directly
4. **Check Permissions** - Verify admin permissions for commands

### CloudNord.net Integration
This plugin is optimized for CloudNord.net hosting:
- Automatic IP detection works seamlessly
- Professional branding included
- Optimized for cloud environments

### Plugin Information
- **Version**: 1.0.0
- **Author**: Senior Resource Packs Team
- **Powered by**: CloudNord.net
- **Minecraft Version**: 1.21+
- **Java Version**: 19+

---

##  License & Credits

**Powered by CloudNord.net** - Start your server with CloudNord

This plugin is designed to work seamlessly with CloudNord.net hosting services and provides professional resource pack management for Minecraft servers.

---

*Made with ❤️ by CloudNord.net*

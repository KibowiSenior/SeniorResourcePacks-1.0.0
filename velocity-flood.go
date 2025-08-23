package main

import (
	"encoding/binary"
	"fmt"
	"math/rand"
	"net"
	"os"
	"strconv"
	"sync"
	"time"
)

// VelocityPacket represents a Minecraft Velocity protocol packet
type VelocityPacket struct {
	PacketID   int32
	Data       []byte
	Length     int
}

// AttackConfig holds configuration for the velocity attack simulation
type AttackConfig struct {
	TargetIP      string
	TargetPort    int
	MaxThreads    int
	Duration      int
	PacketsPerSec int
	PacketSize    int
}

// AttackStats tracks attack statistics
type AttackStats struct {
	TotalPackets int
	TotalBytes   int
	Errors       int
	StartTime    time.Time
	mu           sync.Mutex
}

func main() {
	fmt.Println("Minecraft Velocity Protocol Flood Simulation")
	fmt.Println("============================================")
	fmt.Println("Educational purposes only - Do not use on real servers")
	fmt.Println()

	if len(os.Args) != 6 {
		fmt.Printf("Usage: %s <ip> <port> <threads> <duration> <packets_per_second>\n", os.Args[0])
		fmt.Println("Example: ./velocity-flood 127.0.0.1 25565 10 30 100")
		os.Exit(1)
	}

	// Parse command line arguments
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

	packetsPerSec, err := strconv.Atoi(os.Args[5])
	if err != nil || packetsPerSec <= 0 {
		fmt.Printf("Invalid packets per second: %s\n", os.Args[5])
		os.Exit(1)
	}

	config := AttackConfig{
		TargetIP:      ip,
		TargetPort:    port,
		MaxThreads:    threads,
		Duration:      duration,
		PacketsPerSec: packetsPerSec,
		PacketSize:    512, // Typical packet size for Velocity
	}

	fmt.Printf("Target: %s:%d\n", config.TargetIP, config.TargetPort)
	fmt.Printf("Threads: %d\n", config.MaxThreads)
	fmt.Printf("Duration: %d seconds\n", config.Duration)
	fmt.Printf("Requested rate: %d packets/second\n", config.PacketsPerSec)
	fmt.Println("============================================")
	fmt.Println("Starting simulation in 3 seconds...")
	time.Sleep(3 * time.Second)

	// Run the simulation
	stats := &AttackStats{StartTime: time.Now()}
	simulateVelocityFlood(config, stats)

	// Display results
	displayResults(config, stats)
}

func simulateVelocityFlood(config AttackConfig, stats *AttackStats) {
	var wg sync.WaitGroup
	done := make(chan struct{})

	// Calculate packets per thread
	packetsPerThread := config.PacketsPerSec / config.MaxThreads
	remainder := config.PacketsPerSec % config.MaxThreads

	// Start attack threads
	for i := 0; i < config.MaxThreads; i++ {
		wg.Add(1)
		
		// Distribute packets evenly across threads
		threadPackets := packetsPerThread
		if i < remainder {
			threadPackets++
		}
		
		go velocityAttackWorker(&wg, done, config, threadPackets, stats, i)
	}

	// Run for the specified duration
	time.Sleep(time.Duration(config.Duration) * time.Second)
	close(done)
	wg.Wait()
}

func velocityAttackWorker(wg *sync.WaitGroup, done <-chan struct{}, config AttackConfig, 
	packetsPerSecond int, stats *AttackStats, workerID int) {
	defer wg.Done()

	// Calculate interval between packets
	interval := time.Duration(1e9 / packetsPerSecond) * time.Nanosecond
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			// Simulate creating and sending a Velocity protocol packet
			packet := createVelocityPacket(config.PacketSize)
			
			// In a real attack, this would be sent to the server
			// For simulation, we just track statistics
			stats.mu.Lock()
			stats.TotalPackets++
			stats.TotalBytes += packet.Length
			// Simulate occasional errors
			if rand.Intn(100) < 5 { // 5% error rate
				stats.Errors++
			}
			stats.mu.Unlock()
		}
	}
}

func createVelocityPacket(size int) VelocityPacket {
	// Create a simulated Velocity protocol packet
	// These are the typical packet types in Velocity protocol
	packetTypes := []int32{
		0x00, // Handshake
		0x01, // Legacy Handshake
		0x02, // Status Request
		0x03, // Ping
		0x04, // Connect Request
		0x05, // Player Info
	}

	packetID := packetTypes[rand.Intn(len(packetTypes))]
	
	// Create packet data with appropriate structure
	data := make([]byte, size)
	binary.BigEndian.PutUint32(data[0:4], uint32(packetID))
	
	// Fill the rest with random data
	rand.Read(data[4:])
	
	return VelocityPacket{
		PacketID: packetID,
		Data:     data,
		Length:   size,
	}
}

func displayResults(config AttackConfig, stats *AttackStats) {
	elapsed := time.Since(stats.StartTime).Seconds()
	actualRate := int(float64(stats.TotalPackets) / elapsed)
	
	fmt.Println()
	fmt.Println("SIMULATION RESULTS")
	fmt.Println("==================")
	fmt.Printf("Target: %s:%d\n", config.TargetIP, config.TargetPort)
	fmt.Printf("Duration: %.1f seconds\n", elapsed)
	fmt.Printf("Requested rate: %d packets/second\n", config.PacketsPerSec)
	fmt.Printf("Actual rate: %d packets/second\n", actualRate)
	fmt.Printf("Total packets: %d\n", stats.TotalPackets)
	fmt.Printf("Total data: %.2f MB\n", float64(stats.TotalBytes)/(1024*1024))
	fmt.Printf("Errors: %d\n", stats.Errors)
	fmt.Println()
	fmt.Println("MINECRAFT VELOCITY PROTOCOL INFORMATION")
	fmt.Println("=======================================")
	fmt.Println("Velocity is a high-performance proxy framework for Minecraft")
	fmt.Println("It uses a custom protocol for communication between proxy and backend servers")
	fmt.Println()
	fmt.Println("COMMON VELOCITY PACKET TYPES SIMULATED:")
	fmt.Println("• 0x00 - Handshake")
	fmt.Println("• 0x01 - Legacy Handshake")
	fmt.Println("• 0x02 - Status Request")
	fmt.Println("• 0x03 - Ping")
	fmt.Println("• 0x04 - Connect Request")
	fmt.Println("• 0x05 - Player Info")
	fmt.Println()
	fmt.Println("DEFENSE MECHANISMS FOR MINECRAFT SERVERS")
	fmt.Println("========================================")
	fmt.Println("1. Use Velocity's built-in connection throttling")
	fmt.Println("2. Implement IP-based rate limiting")
	fmt.Println("3. Use a firewall to filter malicious traffic")
	fmt.Println("4. Configure reverse proxy with DDoS protection")
	fmt.Println("5. Use cloud-based mitigation services")
	fmt.Println("6. Enable Velocity's player info forwarding for better authentication")
	fmt.Println()
	fmt.Println("This simulation demonstrates the importance of:")
	fmt.Println("- Implementing proper rate limiting on Minecraft servers")
	fmt.Println("- Using proxy frameworks like Velocity with security features")
	fmt.Println("- Monitoring network traffic for abnormal patterns")
}

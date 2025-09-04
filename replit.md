# Project Documentation

## Overview

This project is a Minecraft server plugin called "senior-resource-packs" built for Minecraft 1.21+. The plugin allows server administrators to load up to 3 local resource packs from configuration files, automatically applying them to players when they join the server.

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Plugin Architecture
- Built using Java 19 with Spigot API 1.21.1
- Maven-based project structure
- Event-driven architecture using Bukkit event system
- Configuration-based resource pack loading

### Core Components
- Main plugin class: SeniorResourcePacks.java
- Configuration system with config.yml
- Pack folder for resource pack files
- Player join event handler for automatic resource pack application
- Command system for configuration reloading

### Configuration System
- YAML-based configuration (config.yml)
- Support for 3 resource pack slots
- Customizable messages for player feedback
- Permission-based command access

### Features
- Automatic resource pack application on player join
- Support for up to 3 simultaneous resource packs
- Local file-based resource pack loading
- Admin reload command with permission control
- Configurable user messages and feedback

## External Dependencies

### Third-Party Services
- External APIs and integrations
- Payment processing services
- Email and notification services
- File storage and CDN services

### Development Tools
- Package managers and build tools
- Testing frameworks and utilities
- Code quality and linting tools
- Deployment and CI/CD pipeline tools

### Database & Infrastructure
- Database hosting and management
- Cloud services and hosting platforms
- Monitoring and analytics tools
- Environment configuration management
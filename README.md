# DC DNS Changer

<p align="center">
  <b>Fast · Secure · Customizable DNS Changer for Android</b>
</p>

<p align="center">
  <a href="https://github.com/deepcodecreate/Dc-Dns-Changer/stargazers">
    <img src="https://img.shields.io/github/stars/deepcodecreate/Dc-Dns-Changer?style=for-the-badge&logo=github" alt="Stars"/>
  </a>
  <a href="https://github.com/deepcodecreate/Dc-Dns-Changer/issues">
    <img src="https://img.shields.io/github/issues/deepcodecreate/Dc-Dns-Changer?style=for-the-badge" alt="Issues"/>
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License"/>
  </a>
  <img src="https://img.shields.io/badge/Android-24%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Version-4.1-orange?style=for-the-badge" alt="Version"/>
</p>

<p align="center">
  <a href="https://github.com/deepcodecreate">GitHub</a> ·
  <a href="https://t.me/deepcodecreate">Telegram</a> ·
  <a href="LICENSE">License</a>
</p>

---

## Overview

**DC DNS Changer** is an open-source Android app for changing DNS with full support for classic and encrypted protocols.  
It is designed for speed, privacy, and flexibility — from gaming DNS pairs to public DoH/DoT resolvers.

---

## Features

| Feature | Description |
|--------|-------------|
| **Protocols** | UDP · TCP · DoT (853) · DoH (443) |
| **Catalog** | Gaming DNS pairs + Cloudflare, Google, Quad9, AdGuard, Vanilla |
| **Custom servers** | Manual entry or import from clipboard |
| **Themes** | 14 Material themes (dark & light) |
| **Live ping** | Latency measurement per server |
| **Root mode** | Optional DNS change without VPN |
| **Language** | فارسی / English |
| **Background** | Auto-reconnect on network change |
| **Logs** | Real-time DNS query viewer |

---

## Supported Protocols

| Protocol | Port | Encryption | Best for |
|----------|------|------------|----------|
| **UDP** | 53 | No | Maximum speed |
| **TCP** | 53 | No | Restricted networks |
| **DoT** | 853 | TLS | System-level privacy |
| **DoH** | 443 | HTTPS | Harder to block / censored networks |

DoH endpoints prefer direct IPs when available to avoid poisoned responses (`10.10.34.x`).

---

## Themes

**Dark**  
Ember · Slate · Twilight · Midnight · Neon · Ocean · Rose · Forest · Amber · Violet · Crimson

**Light**  
Sand · Sky · Autumn

---

## Requirements

- Android **7.0 (API 24)** or higher  
- Target SDK **34**  
- VPN permission (or root for optional mode)

---

## Build

```bash
git clone https://github.com/deepcodecreate/Dc-Dns-Changer.git
cd Dc-Dns-Changer
./gradlew assembleDebug

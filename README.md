# DC DNS Changer

<p align="center">
  <b>Fast · Secure · Customizable DNS Changer for Android</b>
</p>

<p align="center">
  <a href="https://github.com/deepcodecreate/Dc-Dns-Changer/stargazers"><img src="https://img.shields.io/github/stars/deepcodecreate/Dc-Dns-Changer?style=for-the-badge&logo=github" alt="Stars"/></a>
  <a href="https://github.com/deepcodecreate/Dc-Dns-Changer/issues"><img src="https://img.shields.io/github/issues/deepcodecreate/Dc-Dns-Changer?style=for-the-badge" alt="Issues"/></a>
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License"/>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Version-4.5-orange?style=for-the-badge" alt="Version"/>
</p>

<p align="center">
  <a href="https://github.com/deepcodecreate">GitHub</a> ·
  <a href="https://t.me/deepcodecreate">Telegram</a>
</p>

---

## Features

- **Protocols:** UDP · TCP · DoT (853) · DoH (443)
- **Catalog:** Gaming DNS pairs + Cloudflare, Google, Quad9, AdGuard, Vanilla
- **Custom servers:** manual entry or import from clipboard
- **Modern UI:** new layout with floating navigation and a redesigned icon
- **Themes:** 14 Material themes (11 dark, 3 light)
- **Live ping** per server, **real-time DNS logs**
- **Root mode:** optional DNS change without VPN
- **Auto-reconnect** on network change
- **Languages:** فارسی / English

## Protocols

| Protocol | Port | Encrypted | Best for |
|----------|------|-----------|----------|
| UDP | 53 | No | Max speed |
| TCP | 53 | No | Restricted networks |
| DoT | 853 | TLS | System-level privacy |
| DoH | 443 | HTTPS | Censored networks |

## Requirements

Android 7.0 (API 24)+ · Target SDK 34 · VPN permission (or root)

## Build

```bash
git clone https://github.com/deepcodecreate/Dc-Dns-Changer.git
cd Dc-Dns-Changer
./gradlew assembleDebug

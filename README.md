# MJOLNIR TERMINAL

Android terminal emulator running Fedora Linux via PRoot, with split-pane Claude AI integration.

---

## Architecture

```
┌─────────────────────────────────┐
│      MJOLNIR // TERMINAL        │
│  Fedora 40 · PRoot · ARM64      │
│  [ESC][TAB][CTRL][↑][↓][→ AI]  │  ← keyboard bar
├─────────────── ≡ ───────────────┤  ← drag to resize
│      MJOLNIR // CLAUDE          │
│  Streaming · Conversation ctx   │
│  [message input          ] [↑]  │
└─────────────────────────────────┘
```

## First Build (Termux)

```bash
# Clone or copy project to phone
cd ~/mjolnir-terminal
chmod +x setup.sh
./setup.sh

# Build
gradle assembleDebug

# APK location
app/build/outputs/apk/debug/app-debug.apk
```

Install the APK: long-press the file in a file manager → Install, or:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## First Launch

On first launch the app downloads:
- **proot binary** (~2MB) from proot-me/proot releases
- **Fedora 40 rootfs** (~100MB) from termux/proot-distro releases

This requires internet. After extraction, runs fully offline.

## Claude API Key

Tap **SET KEY** in the Claude panel header. Key is stored encrypted in Android Keystore — never written to disk in plaintext.

## → AI Button

Tap `→ AI` in the terminal keyboard bar to capture visible terminal output and inject it into the Claude input field. Edit as needed, then send.

## Components

| File | Purpose |
|---|---|
| `MainActivity.kt` | Split-pane host, drag divider |
| `TerminalFragment.kt` | TerminalView + PRoot session |
| `ClaudeFragment.kt` | Streaming chat UI |
| `ClaudeApiService.kt` | Anthropic SSE streaming client |
| `ProotManager.kt` | PRoot binary + session config |
| `BootstrapInstaller.kt` | First-boot download + extraction |
| `ApiKeyStore.kt` | Android Keystore AES-GCM key storage |
| `TerminalViewModel.kt` | Shared state, terminal↔Claude bridge |

## Rootfs URL

Defined in `BootstrapInstaller.kt`. Update `FEDORA_ROOTFS_URL` if the proot-distro release URL changes.

## Package manager inside Fedora

```bash
dnf install python3 nmap curl git vim
```

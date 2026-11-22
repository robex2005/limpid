<p align="center"><img src="docs/icon.svg" width="112" alt="Limpid icon"></p>

<h1 align="center">Limpid</h1>
<p align="center">Fix gray, washed-out videos on Samsung One UI by toggling <b>Disable HW overlays</b> automatically, through <a href="https://shizuku.rikka.app/">Shizuku</a>. No root.</p>

🇷🇴 [Română mai jos](#română)

## The problem

On some Samsung phones running One UI, apps like Facebook play videos with a gray haze. The picture becomes correct the moment any system overlay is drawn on top (the volume panel, a notification). The workaround is *Developer options → Disable HW overlays*, which moves screen composition from the display controller to the GPU. That switch resets on every reboot, and leaving it on all the time costs battery.

## What Limpid does

| Mode | Behavior |
|---|---|
| **Enable globally** | HW overlays disabled everywhere until reboot |
| **Only for selected apps** | Disabled while an app from your list is in the foreground, normal otherwise |
| **Disable** | Normal system behavior |

## How it works

- Limpid calls SurfaceFlinger transaction `1008` (the same call the Developer options switch makes) through **Shizuku**, which runs as shell. There is no background service of its own.
- An **accessibility service** reports the focused application window for the per-app mode: event-driven, no polling, and it ignores system overlays like the volume panel or the keyboard.
- After a **reboot**, Shizuku (with *Start on boot*) wakes Limpid through its provider and the saved mode is applied again.
- Through Shizuku, Limpid grants itself `WRITE_SECURE_SETTINGS` so it can enable its own accessibility service (*Enable automatically*).

## Setup

1. Install [Shizuku](https://shizuku.rikka.app/download/) and start it via *Wireless debugging*. On Samsung the pairing dialog closes when you switch apps: use split screen or Shizuku's notification for the code.
2. In Shizuku, enable *Start on boot*.
3. In Limpid, tap *Allow Limpid in Shizuku* and pick a mode.

## Install

- **Test builds:** [Releases → nightly](https://github.com/robex2005/limpid/releases/tag/nightly), built by GitHub Actions from `main` and signed with a public test key. Uninstall before installing another signed build.
- **F-Droid:** submission in progress.

On Android 13+, Google Play Protect may block a downloaded APK that declares an accessibility service. Install with `adb install`, or pause Play Protect scanning during the install.

## Build

```sh
./gradlew assembleDebug     # or assembleRelease (unsigned)
```

JDK 17, Android SDK 35, `minSdk` 30.

## Privacy

No internet permission, no analytics, no ads. The accessibility service reads only the package name of the focused app.

## License

GPL-3.0-or-later

---

## Română

**Limpid** elimină filtrul gri din videoclipuri pe telefoanele Samsung cu One UI, aplicând automat *Opțiuni dezvoltator → Dezactivare suprapuneri HW* prin **Shizuku**: global, doar pentru aplicațiile alese, sau deloc. Fără root.

- Comanda către SurfaceFlinger e trimisă prin Shizuku; aplicația nu are serviciu propriu în fundal.
- Modul pe aplicații folosește un serviciu de accesibilitate care citește doar numele aplicației din prim-plan.
- După repornire, Shizuku (cu *Pornire la boot*) trezește Limpid, iar modul salvat se reaplică automat.
- Interfață în română și engleză.

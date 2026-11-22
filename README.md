<p align="center"><img src="docs/icon.svg" width="112" alt="Limpid icon"></p>

<h1 align="center">Limpid</h1>
<p align="center">Fix gray, washed-out videos on Samsung One UI by toggling <b>Disable HW overlays</b> automatically. No root, no Shizuku.</p>

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

1. **Built-in ADB client** ([libadb-android](https://github.com/MuntashirAkon/libadb-android)) pairs once with the phone's own *Wireless debugging* (Android 11+), using a code typed into a notification.
2. Over that connection it grants itself `WRITE_SECURE_SETTINGS` and starts a **helper** with `app_process`, running as `shell` (uid 2000), detached from ADB.
3. The helper listens on `127.0.0.1` only, requires a per-install secret token, and exposes a single operation: SurfaceFlinger transaction `1008` (the same call the Developer options switch makes).
4. An **accessibility service** reports the focused application window (event-driven, no polling, ignores system overlays) for the per-app mode.
5. After a **reboot**, Limpid re-enables wireless debugging through `WRITE_SECURE_SETTINGS`, reconnects with the already-paired key as soon as the phone is on Wi-Fi, and restarts the helper. Once running, it works on mobile data until the next reboot.
6. Optionally (default on) it turns wireless debugging off again once the helper is running.

## Install

- **Test builds:** [Releases → nightly](https://github.com/robex2005/limpid/releases/tag/nightly), built by GitHub Actions from `main` and signed with a public test key. Uninstall before installing another signed build.
- **F-Droid:** submission in progress.

On Android 13+, an APK installed from a browser may need *Settings → Apps → Limpid → ⋮ → Allow restricted settings* before you can enable the accessibility service manually. After pairing, *Enable automatically* on the main screen does it for you.

## Build

```sh
./gradlew assembleDebug     # or assembleRelease (unsigned)
```

Requirements: JDK 17, Android SDK 35. `minSdk` 30.

## Privacy

No analytics, no ads, no network access beyond the device itself (local ADB and the loopback helper). The accessibility service reads only the package name of the focused app.

## License

GPL-3.0-or-later

---

## Română

**Limpid** elimină filtrul gri din videoclipuri pe telefoanele Samsung cu One UI, aplicând automat *Opțiuni dezvoltator → Dezactivare suprapuneri HW*: global, doar pentru aplicațiile alese, sau deloc. Funcționează fără root și fără Shizuku.

- Asociere unică cu **Depanarea wireless** (Android 11+), cu codul introdus direct în notificare.
- Un helper minim rulează ca `shell` și poate face un singur lucru: comutarea flag-ului din SurfaceFlinger. Merge și pe date mobile, până la repornire.
- După repornire, Limpid repornește singur depanarea wireless și helper-ul imediat ce telefonul e pe Wi-Fi.
- Modul pe aplicații folosește un serviciu de accesibilitate care citește doar numele aplicației din prim-plan.
- Interfață în română și engleză.

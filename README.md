# eyeBROWSer

A minimal, remote-friendly web browser for Fire TV / Android TV.

## Features

- **Full-screen browsing** — just a WebView, no clutter. Pages open inside the app.
- **Configurable start page** — loads your chosen URL on launch (default: google.com).
- **TV options menu** — press **MENU** on the remote, or **long-press SELECT**, for:
  - Go to address…
  - Set start page…
  - Phone keyboard (QR)…
  - Reload
  - Go forward
- **BACK** walks WebView history, then exits.
- **Phone keyboard** — scan a QR code on the TV and type from your phone. Text appears live in the TV browser's focused field; an Enter button submits forms. Works entirely over your home Wi-Fi — no internet, accounts, or cloud involved.
- **Polish** — accent progress bar while pages load, graceful error page with Retry, first-run hint overlay, styled dialogs with clear D-pad focus states.

## Getting the APK / AAB

Every push to `main` builds automatically via GitHub Actions:

1. Open the repo → **Actions** → latest **Build APK** run.
2. Download the **eyebrowser-apk** artifact (sign in to GitHub if asked) and unzip it.
3. The **eyebrowser-aab** artifact is the signed release bundle for Google Play (see below).

## Publish on Google Play (Android TV / Google TV)

The app is Play-ready: `targetSdk 35`, `LEANBACK_LAUNCHER` intent, TV banner, no phone-only permissions.

**One-time setup — upload key:**
```sh
# Generate an upload keystore (guard this file + passwords carefully)
keytool -genkeypair -v -keystore eyebrowser-upload.keystore \
  -alias eyebrowser -keyalg RSA -keysize 2048 -validity 10000

# Base64 it for the CI secret
base64 -w0 eyebrowser-upload.keystore
```
In the repo → **Settings → Secrets and variables → Actions**, add:
- `ANDROID_KEYSTORE_BASE64` — the base64 output above
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

Push to `main`; the workflow signs the release AAB with this key (without the secrets it falls back to debug signing so builds never break — **never upload a debug-signed AAB to Play**).

**Play Console checklist:**
1. **Create app** → check the **Android TV** form factor under device targeting.
2. **Upload the AAB** (`eyebrowser-aab` artifact) to an internal testing track first.
3. **Store listing (TV):** at least 2 TV screenshots (1280×720), a feature graphic (1024×500), and the 512×512 app icon.
4. **Content rating:** complete the IARC questionnaire (it's a browser — answer the user-generated-content / web-access questions honestly).
5. **Privacy policy:** host `PRIVACY.md` (e.g. as a GitHub Pages page or repo raw link) and paste the URL in the listing.
6. **Data safety form:** declare *no data collected* (matches the policy; no analytics in the app).
7. **Target audience & countries**, then promote the tested release to production and roll out.

Note: with Play App Signing enabled (default for new apps), Google re-signs the final APKs — keep your upload keystore backed up regardless.

## Sideloading on Fire TV

- **Easiest:** install the *Downloader* app on your Fire TV and point it at a direct APK link, or
- **Via computer:** install *Send Files to TV* on both devices and transfer the APK, then open it with a file manager — or use `adb`:
  ```sh
  adb connect <fire-tv-ip>:5555
  adb install app-debug.apk
  ```
  (Enable *ADB debugging* and *Apps from Unknown Sources* under Fire TV Settings → My Fire TV → Developer options.)

## Using the phone keyboard

1. On the TV: long-press **SELECT** (or press **MENU**) → **Phone keyboard (QR)…**
2. Scan the QR code with your phone's camera (or type the shown `http://…` URL manually). Your phone must be on the **same Wi-Fi**.
3. Tap a text field on the TV (e.g. a search box), then type on the phone — text mirrors live. Hit **Enter** on the phone page to submit.

The TV runs a tiny local web server (ports 8080–8085, first free one wins) that serves the keyboard page and relays keystrokes over a WebSocket.

## Changing the start page

Long-press **SELECT** → **Set start page…** → type the address → **Save**. It's stored on the device and loaded on every launch.

## Tech notes

- Kotlin, single `MainActivity`, programmatic UI (no XML layouts)
- `WebView` with JS + DOM storage enabled
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) (`nanohttpd-websocket`) for the local keyboard server
- [ZXing](https://github.com/zxing/zxing) for QR generation
- Built with AGP 8.5.2 · Kotlin 1.9.24 · compileSdk 35 · targetSdk 35 · minSdk 21 · Java 17

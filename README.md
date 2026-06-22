<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="96" alt="N I C O">
</p>

<br />

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)&nbsp;
[![Release](https://img.shields.io/github/v/release/haecksenwerk/nico)](https://github.com/haecksenwerk/nico/releases/latest)&nbsp;
[![Ko-Fi](https://img.shields.io/badge/Ko--fi-Support%20this%20project-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/haecksenwerk)&nbsp;

<br />

**N I C O** - an Android app for controlling Nikon Z-Series cameras.

Control your Nikon camera over USB (PTP/OTG) directly from your Android device. Built with Kotlin and Jetpack Compose (Material 3).

The app has been tested exclusively on a Google Pixel 9 using a Nikon Z fc. While other Nikon Z models should be compatible, certain functions may vary by camera model. 
Additionally, please ensure your Android device supports USB-OTG mode.

---

## Installation

### Obtainium

The easiest way to install and keep nico up to date is via [Obtainium](https://github.com/ImranR98/Obtainium) — it fetches updates directly from GitHub Releases, no app store required.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium)
2. Add the following URL as a new app source:

```
https://github.com/haecksenwerk/nico
```

<br />
<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22:%22com.haecksenwerk.nico%22,%22url%22:%22https://github.com/haecksenwerk/nico%22,%22author%22:%22haecksenwerk%22,%22name%22:%22nico%22%7D">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" 
         alt="Get it on Obtainium" 
         height="40"
         style="vertical-align: middle;">
  </a>
</p>
<br />

### Manual download

Grab the latest signed APK directly from the [Releases](https://github.com/haecksenwerk/nico/releases/latest) page.

**Minimum Android version:** Android 7 (API 24)

---

## Usage

1. **Connect** the camera to your phone with a USB-OTG cable and power it on - the app connects automatically.
2. **Live view** starts on the main screen. Tap the camera icon to toggle it on or off.
3. **Change settings** by tapping any property tile (aperture, white balance, metering, focus mode) and selecting a value from the picker.
   - Mode, Shutter speed, EV compensation, and ISO are **display-only** - they reflect the current camera state but cannot be changed from the app.
4. **Capture** a photo with the shutter button. Use the timer selector to add a delay before release.
5. **Browse** pictures on the camera card in the browser tab. Tap a picture to preview it.
6. **Download** pictures from the browser - saved to the **Pictures** folder on your phone and visible in the **nico** album in Google Photos.

---

## Requirements

- Android phone with USB OTG / USB host support
- Nikon camera set to **PTP** (not MTP or "iPhone") USB mode

---

## License

[MIT License](https://opensource.org/licenses/MIT)

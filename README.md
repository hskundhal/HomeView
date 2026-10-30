# Home View

An Android 10+ tablet app that shows 4 CamHi cameras live in a 2×2 grid, with a floating, transparent Brampton weather and Google Calendar box.

## 1. Build the APK (free, about 5 minutes)

### Option A: GitHub (no software to install)

1. Sign in at github.com (a free account is fine), click **New repository**, name it `HomeView`, choose **Private**, and click **Create**.
2. On the new repo page, click **uploading an existing file**. Drag in **everything inside** the `HomeView` folder, then click **Commit changes**.
   - The `.github` folder is hidden on a Mac. In Finder, press **Cmd + Shift + .** to show it before dragging.
   - If it still doesn't upload: click **Add file → Create new file**, name it `.github/workflows/build.yml`, paste in the contents of that file from this folder, and commit.
3. Open the **Actions** tab. The **Build APK** job runs on its own and takes 3–5 minutes. If it doesn't start, click **Build APK → Run workflow**.
4. When it shows a green tick, open the run and download **HomeView-apk** under *Artifacts*. Unzip it to get `app-debug.apk`.

### Option B: Android Studio

Open this folder in Android Studio, wait for it to sync, then choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## 2. Install on the tablet

Copy `app-debug.apk` to the tablet (USB, Google Drive or email), tap it, and allow **Install unknown apps** when Android asks.

## 3. Set up

The settings screen opens on first launch. Afterwards, **long-press any camera** (or the weather box) to get back to it.

- **Cameras:** enter each camera's IP address, username and password. The defaults suit most CamHi cameras:
  - port `554`
  - HD stream `/11` (used in full screen)
  - low-res stream `/12` (used in the grid)

  You can also paste a full `rtsp://...` link into the IP field.
- **Google Calendar:** in Google Calendar on a computer, open **Settings → your calendar → Integrate calendar** and copy **Secret address in iCal format**. Paste it here, one link per line. Keep this link private.
- **Weather location:** Brampton by default.
- **Use as home screen:** tick this and pick **Home View** as the default home app. The tablet then opens straight into it after a restart and whenever Home is pressed. To leave, long-press a camera and use **Open Android settings** or **Choose default home app**.

## Using it

- **Tap a camera:** it goes full screen in HD. Tap again (or press Back) to return to the grid.
- **Drag the weather box:** it snaps to the nearest corner and remembers that corner.
- **Weather:** refreshes every 10 minutes. **Calendar:** refreshes every 15 minutes.
- **Screen:** stays on, with no status bar or navigation bar.

## Troubleshooting

- **"Offline · retrying":**
  - Check the IP address, password and port. Make sure the camera works in the CamHi app on the same Wi-Fi.
  - Some models use `/12` and `/13`, or `/live/ch0` and `/live/ch1`, as their stream paths.
- **Black or stuttering video in the grid:** the tablet may not handle 4 HD streams. Make sure the grid uses the low-res stream (`/12`).
- **No events:** check that the iCal link ends in `.ics` and that the tablet's date and time zone are correct.

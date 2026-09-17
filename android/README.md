# Trime Theme Studio Android

This Android project wraps the existing offline web editor in a native WebView and adds a small Storage Access Framework bridge for Rime configuration files.

## Features

- Open one `.yaml` / `.yml` file with the Android system file picker.
- Grant a Rime folder with the Android folder picker, list YAML files in that folder, and open them in the editor.
- Save the edited full YAML back to the currently opened document URI.
- Save as a new YAML file through the Android system document creator.
- Use a landscape WebView with fit-to-width rendering and pinch zoom for the current three-column editor.

Modern Android does not allow normal apps to silently read and overwrite arbitrary shared-storage paths. The first file or folder selection is therefore required so Android can grant this app read/write access.

## Build

Open the `android` folder in Android Studio, let it sync the Gradle project, then run `Build > Build APK(s)`.

The Gradle build copies the web files from the repository root into the APK assets before compiling:

- `index.html`
- `styles.css`
- `app.js`
- `bundled-config.js`
- `单手特化.trime.yaml`
- `trime.yaml`
- `tongwenfeng.trime.yaml`

The generated debug APK will be under `android/app/build/outputs/apk/debug/` after a successful build.

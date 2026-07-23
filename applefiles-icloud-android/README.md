# Apple Books for Supernote Nomad

WebView-based Apple Books launcher app for Supernote Nomad (Chauvet OS), ready for sideloaded APK installation.

## Features

- Opens Apple Books directly: `https://books.apple.com/`
- E-ink friendly simple UI with toolbar + pull-to-refresh
- Download support to Supernote internal folder: `/storage/emulated/0/Document/AppleBooks`

## Build debug APK

```bash
cd applefiles-icloud-android
./gradlew assembleDebug --no-daemon
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Sideload on Supernote Nomad

1. Enable unknown app installs on the device.
2. Transfer APK to the device (USB or ADB), for example:
   ```bash
   adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
   ```
3. Open the APK from the file manager and install.

## License

This project is licensed under the GPL-3.0 License - see the LICENSE file for details.

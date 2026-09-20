# Android Native Hub Implementation Plan

I will convert the existing Android project into a high-performance **Native Hub** that wraps your perfectly mirrored web terminal. This will give you a real installed app on your phone with the exact same "Institutional" look and feel we just perfected.

## User Review Required

> [!IMPORTANT]
> This will replace the existing Compose screens with a dedicated **Institutional WebView**. This ensures that any update I make to the web hub is automatically reflected in your Android App without needing to rebuild it.

## Proposed Changes

### [Android Application]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Kunal/Desktop/Stock/android_app/app/src/main/java/com/Kunal/stock/MainActivity.kt)
- Replace Compose logic with a dedicated `WebView`.
- Enable **JavaScript** (for Charts and technical rows).
- Enable **DOM Storage** (so your Login and GitHub Token stay saved on your phone).
- Handle the **System Back Button** so you can navigate between stock lists without closing the app.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Kunal/Desktop/Stock/android_app/app/src/main/AndroidManifest.xml)
- Ensure Internet permissions are active.
- Set `hardwareAccelerated="true"` for smooth chart scrolling.

## Verification Plan

### Manual Verification
1. I will provide instructions to build the APK.
2. I will verify that the app loads `https://kunal-analysis.web.app` directly into the full-screen interface.

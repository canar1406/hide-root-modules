# 🛡️ Hide Root & Bypass Suite (LSPosed / Xposed Modules)

Bộ công cụ **LSPosed / Xposed Modules** chuyên dụng dành cho Android (**Android 8 – Android 15 / Xiaomi HyperOS**), giúp ẩn **Tùy chọn nhà phát triển (Developer Options)**, **Gỡ lỗi USB/Wi-Fi (ADB)**, **Vị trí giả lập (Mock Location)** và **Mở khóa chụp ảnh / quay màn hình (`FLAG_SECURE`)** ở tầng **System Framework (`system_server`)** mà **không cần tiêm Xposed trực tiếp vào ứng dụng ngân hàng hay VNeID**.

---

## 📦 Danh sách Module trong Bộ Công Cụ

### 1. 🛡️ [`HideDevMode.apk`](./HideDevMode.apk) (`v1.1.0` — `com.heavietnam.hidedevmode`)
Ẩn triệt để trạng thái Developer Options, USB/Wi-Fi Debugging và Mock Location trước mọi ứng dụng kiểm tra bảo mật gắt (**VCB Digibank, Techcombank, MB Bank, VNeID, MoMo, vRace...**).

* **Cơ chế Hook 2 tầng**:
  1. **Tầng Khung Hệ Thống (`System Framework` / `SettingsProvider`)**:
     * Can thiệp trực tiếp vào `com.android.providers.settings.SettingsProvider.call("GET_global" | "GET_secure" | "GET_system", ...)` và `query(...)` ngay trong tiến trình `system_server`.
     * Tự động kiểm tra `Binder.getCallingUid()`: Chỉ trả về `"0"` đối với các ứng dụng người dùng (`UID >= 10000`), giữ nguyên giá trị thật cho `System (UID 1000)`, ứng dụng **Cài đặt (`com.android.settings`)** và **ADB Shell (`UID 2000`)**.
     * **Ưu điểm quyết định**: Các ứng dụng ngân hàng (như VCB Digibank, Techcombank, MB Bank) và VNeID nhận giá trị `development_settings_enabled = 0`, `adb_enabled = 0`, `adb_wifi_enabled = 0`, `mock_location = 0` mà **KHÔNG cần tích các app ngân hàng đó trong LSPosed** $\rightarrow$ tuyệt đối không bị phát hiện Xposed hook trong tiến trình app!
  2. **Tầng Ứng Dụng (`App Scope`)**:
     * Hook `Settings.Global`, `Settings.Secure`, `Settings.System` (`getInt`, `getString`, `getLong`, `getFloat`).
     * Hook `Location.isFromMockProvider()` & `Location.isMock()` (Android 12+ / SDK 31+) $\rightarrow$ luôn trả về `false`, tự động xóa cờ `mockLocation` trong `Location.getExtras()`.
     * Giả lập `android.os.SystemProperties` (`ro.debuggable -> 0`, `ro.secure -> 1`, `sys.usb.state / sys.usb.config -> mtp`, `init.svc.adbd -> stopped`, `service.adb.tcp.port -> -1`, `persist.adb.tls_server.enable -> 0`).
     * Hook `Debug.isDebuggerConnected()` & `Debug.waitingForDebugger()` $\rightarrow$ `false`.

---

### 2. 📸 [`SpoofScreenshot.apk`](./SpoofScreenshot.apk) (`v1.0.0` — `com.heavietnam.spoofscreenshot`)
Mở khóa chụp ảnh màn hình và quay màn hình trên **mọi ứng dụng chặn chụp màn hình**, đồng thời vô hiệu hóa cơ chế phát hiện chụp màn hình trên Android 14/15 & Xiaomi HyperOS.

* **Cơ chế Hook toàn diện**:
  1. **Tầng Khung Hệ Thống (`system_server` — `android`)**:
     * `com.android.server.wm.WindowState.isSecureLocked()` $\rightarrow$ luôn trả về `false`.
     * `com.android.server.wm.WindowManagerServiceImpl.notAllowCaptureDisplay()` *(cơ chế chặn riêng của Xiaomi MIUI / HyperOS)* $\rightarrow$ luôn trả về `false`.
     * `com.android.server.wm.WindowSurfaceController` (Constructor & `setSecure`) $\rightarrow$ xóa cờ `SurfaceControl.SECURE` (`0x80`) và ép `false`.
     * `com.android.server.wm.WindowManagerService.captureDisplay()` $\rightarrow$ ép `mCaptureSecureLayers = true` cho mọi `CaptureArgs`.
     * `com.android.server.devicepolicy.DevicePolicyManagerService.getScreenCaptureDisabled()` $\rightarrow$ luôn trả về `false`.
  2. **Tầng Chặn Phát Hiện Chụp Màn Hình (`Anti-Screenshot Detection`)**:
     * `com.android.server.wm.ActivityTaskManagerService.registerScreenCaptureObserver()` (Android 14/15) $\rightarrow$ vô hiệu hóa (`no-op`).
     * `com.android.server.wm.WindowManagerService.notifyScreenshotListeners()` (MIUI / AOSP) $\rightarrow$ trả về danh sách rỗng `Collections.emptyList()`.
     * `android.app.Activity.registerScreenCaptureCallback()` (Android 14/15) $\rightarrow$ vô hiệu hóa (`no-op`).
  3. **Tầng Framework / Ứng Dụng (`SystemUI`, `MIUI Screenshot`, `MIUI ScreenRecorder` & App Scope)**:
     * `android.window.ScreenCapture$CaptureArgs` & `Builder` $\rightarrow$ luôn ép `mCaptureSecureLayers = true`.
     * `android.view.SurfaceControl$Builder` & `SurfaceControl$Transaction` (`setSecure`, `setFlags`) $\rightarrow$ xóa bit `0x80` (`SECURE`).
     * `android.view.Window.setFlags()` / `addFlags()` & `WindowManagerImpl` / `WindowManagerGlobal` (`addView`, `updateViewLayout`) $\rightarrow$ xóa cờ `WindowManager.LayoutParams.FLAG_SECURE` (`0x2000`).
     * `android.view.SurfaceView.setSecure(boolean)` $\rightarrow$ luôn ép `false`.
     * `android.app.Activity.setRecentsScreenshotEnabled(boolean)` $\rightarrow$ luôn ép `true` (hiển thị ảnh xem trước trong màn hình Đa nhiệm).

---

## 🚀 Hướng dẫn Cài đặt & Cấu hình Chuẩn (VNeID + Ngân hàng + Chụp màn hình)

### Bước 1: Cài đặt 2 file APK
```bash
adb install -r HideDevMode.apk
adb install -r SpoofScreenshot.apk
```

### Bước 2: Bật trong LSPosed
1. Mở **LSPosed Manager** $\rightarrow$ mục **Mô-đun (Modules)**:
   * **Hide DevMode**: Bật mô-đun, chỉ cần tích **Khung hệ thống (`system`)** *(không tích Cài đặt `com.android.settings`, không tích app ngân hàng)*.
   * **Spoof Screenshot**: Bật mô-đun, tích chọn:
     * ✅ **Framework Hệ thống (`system`)**
     * ✅ **Giao diện người dùng hệ thống (`com.android.systemui`)**
     * ✅ **Chụp ảnh màn hình (`com.miui.screenshot`)**
     * ✅ **Quay phim Màn hình (`com.miui.screenrecorder`)**
2. **Khởi động lại máy (Reboot) 1 lần** để hook trong `system_server` có hiệu lực toàn cục.

### Bước 3: Ẩn danh sách ứng dụng bằng HMA-OSS (`Hide My Applist`)
1. Mở **HMA-OSS (`com.tsng.hidemyapplist`)** $\rightarrow$ vào **Template (`hide root`)**:
   * Đảm bảo đã tích ẩn các gói: `com.rifsxd.ksunext`, `org.lsposed.manager`, `com.tsng.hidemyapplist`, `com.heavietnam.hidedevmode`, `com.heavietnam.spoofscreenshot`, `com.smoothrunner.gps`...
2. Trong mục **Quản lý ứng dụng (App Manage)** của HMA-OSS:
   * Bật ẩn (`Enable Hide`) với template `hide root` cho **VNeID (`com.vnid`)** và toàn bộ các ứng dụng ngân hàng (**VCB Digibank, Techcombank, MB Bank, Viettel Money...**).

### Bước 4: Mẹo xử lý lỗi VNeID `CA-E006` / `CA-E012` (Cache chứng chỉ Key Attestation cũ)
* **Nguyên nhân**: Ở lần mở đầu tiên, VNeID tạo cặp khóa phần cứng (`iwslpu`, `lwpcas`) trong Android KeyStore rồi mã hóa XOR và lưu đệm (cache) phần mở rộng X.509 Key Attestation (`OID 1.3.6.1.4.1.11129.2.1.17`) vào 2 file nội bộ:
  * `/data/user/0/com.vnid/files/cmwoawp.ogg`
  * `/data/user/0/com.vnid/files/xwoccmwldwasxm.dat`
* Nếu trước đó máy từng mở VNeID khi chưa bật **AlwaysStrong / TrickyStore** (hoặc khi dùng keybox bị thu hồi), VNeID sẽ đọc lại 2 file cache cũ này và báo lỗi `CA-E006, CA-E012` dù Play Integrity đã đạt 3 tích xanh (`MEETS_STRONG_INTEGRITY`).
* **Cách khắc phục mà KHÔNG mất dữ liệu đăng nhập VNeID**:
  ```bash
  adb shell su -c "am force-stop com.vnid && rm -f /data/user/0/com.vnid/files/cmwoawp.ogg /data/user/0/com.vnid/files/xwoccmwldwasxm.dat /data/misc/keystore/user_0/*10343*"
  ```
  *(Thay `10343` bằng UID của `com.vnid` trên máy bạn)*. Sau khi xóa 2 file cache này, VNeID sẽ sinh lại chứng chỉ phần cứng mới từ **AlwaysStrong / TEESimulator** và đăng nhập bình thường.

---

## 📂 Cấu trúc Thư mục

```text
├── HideDevMode.apk                       # APK cài đặt ngay của module Hide DevMode (v1.1.0)
├── SpoofScreenshot.apk                   # APK cài đặt ngay của module Spoof Screenshot (v1.0.0)
├── README.md                             # Tài liệu hướng dẫn chi tiết
├── HideDevModeAndroid/                   # Mã nguồn module Hide DevMode
│   └── app/
│       ├── libs/xposed-api.jar           # Thư viện stub Xposed API 82
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── assets/xposed_init
│           ├── res/values/arrays.xml
│           └── java/com/heavietnam/hidedevmode/
│               ├── HideDevModeHook.java
│               └── MainActivity.java
└── SpoofScreenshotAndroid/               # Mã nguồn module Spoof Screenshot
    └── app/
        ├── libs/xposed-api.jar           # Thư viện stub Xposed API 82
        └── src/main/
            ├── AndroidManifest.xml
            ├── assets/xposed_init
            ├── res/values/arrays.xml
            └── java/com/heavietnam/spoofscreenshot/
                ├── SpoofScreenshotHook.java
                └── MainActivity.java
```

---

## 📄 Bản quyền (Copyright)

**© 2026 [Heavietnam](https://home.heavietnam.com/). All rights reserved.**
Phát triển & tối ưu bởi **[Heavietnam](https://home.heavietnam.com/)**.

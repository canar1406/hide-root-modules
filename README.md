# 🛡️ Hide DevMode & Root Bypass (`v1.1.0`)

Module **LSPosed / Xposed** chuyên dụng dành cho Android (**Android 8 – Android 15 / Xiaomi HyperOS**), giúp ẩn triệt để **Tùy chọn nhà phát triển (Developer Options)**, **Gỡ lỗi USB/Wi-Fi (ADB)** và **Vị trí giả lập (Mock Location)** ở tầng **System Framework (`system_server`)** mà **không cần tiêm Xposed trực tiếp vào ứng dụng ngân hàng hay VNeID**.

---

## ✨ Tính năng của [`HideDevMode.apk`](./HideDevMode.apk) (`v1.1.0` — `com.heavietnam.hidedevmode`)

1. **Tầng Khung Hệ Thống (`System Framework` / `SettingsProvider`)**:
   * Can thiệp trực tiếp vào `com.android.providers.settings.SettingsProvider.call("GET_global" | "GET_secure" | "GET_system", ...)` và `query(...)` ngay trong tiến trình `system_server`.
   * Tự động kiểm tra `Binder.getCallingUid()`: Chỉ trả về `"0"` đối với các ứng dụng người dùng (`UID >= 10000`), giữ nguyên giá trị thật cho `System (UID 1000)`, ứng dụng **Cài đặt (`com.android.settings`)** và **ADB Shell (`UID 2000`)**.
   * **Ưu điểm quyết định**: Các ứng dụng ngân hàng có chống hook gắt (**VCB Digibank, Techcombank, MB Bank...**) và **VNeID** nhận giá trị `development_settings_enabled = 0`, `adb_enabled = 0`, `adb_wifi_enabled = 0`, `mock_location = 0` mà **KHÔNG cần tích các app ngân hàng đó trong LSPosed** $\rightarrow$ tuyệt đối không bị phát hiện Xposed hook trong tiến trình app!
2. **Tầng Ứng Dụng (`App Scope`)**:
   * Hook `Settings.Global`, `Settings.Secure`, `Settings.System` (`getInt`, `getString`, `getLong`, `getFloat`).
   * Hook `Location.isFromMockProvider()` & `Location.isMock()` (Android 12+ / SDK 31+) $\rightarrow$ luôn trả về `false`, tự động xóa cờ `mockLocation` trong `Location.getExtras()`.
   * Giả lập `android.os.SystemProperties` (`ro.debuggable -> 0`, `ro.secure -> 1`, `sys.usb.state / sys.usb.config -> mtp`, `init.svc.adbd -> stopped`, `service.adb.tcp.port -> -1`, `persist.adb.tls_server.enable -> 0`).
   * Hook `Debug.isDebuggerConnected()` & `Debug.waitingForDebugger()` $\rightarrow$ `false`.

---

## 🚀 Hướng dẫn Cài đặt & Cấu hình Chuẩn (VNeID + Ngân hàng)

### Bước 1: Cài đặt APK
```bash
adb install -r HideDevMode.apk
```

### Bước 2: Bật trong LSPosed
1. Mở **LSPosed Manager** $\rightarrow$ mục **Mô-đun (Modules)** $\rightarrow$ bật **Hide DevMode**.
2. Chỉ cần tích **Khung hệ thống (`system`)** *(không tích Cài đặt `com.android.settings`, không cần tích app ngân hàng)*.
3. **Khởi động lại máy (Reboot) 1 lần** để hook trong `system_server` có hiệu lực toàn cục.

### Bước 3: Ẩn danh sách ứng dụng bằng HMA-OSS (`Hide My Applist`)
1. Mở **HMA-OSS (`com.tsng.hidemyapplist`)** $\rightarrow$ vào **Template (`hide root`)**:
   * Đảm bảo đã tích ẩn các gói: `com.rifsxd.ksunext`, `org.lsposed.manager`, `com.tsng.hidemyapplist`, `com.heavietnam.hidedevmode`, `com.smoothrunner.gps`...
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
├── README.md                             # Tài liệu hướng dẫn chi tiết
└── HideDevModeAndroid/                   # Mã nguồn module Hide DevMode
    └── app/
        ├── libs/xposed-api.jar           # Thư viện stub Xposed API 82
        └── src/main/
            ├── AndroidManifest.xml
            ├── assets/xposed_init
            ├── res/values/arrays.xml
            └── java/com/heavietnam/hidedevmode/
                ├── HideDevModeHook.java
                └── MainActivity.java
```

---

## 📄 Bản quyền (Copyright)

**© 2026 [Heavietnam](https://home.heavietnam.com/). All rights reserved.**
Phát triển & tối ưu bởi **[Heavietnam](https://home.heavietnam.com/)**.

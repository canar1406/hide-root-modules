package com.heavietnam.hidedevmode;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.provider.Settings;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * HideDevModeHook v1.1.0 (© Heavietnam - https://home.heavietnam.com/)
 * Module Xposed / LSPosed chuyên dụng giúp ẩn triệt để:
 * 1. Tầng Khung hệ thống (System Framework / SettingsProvider):
 *    Chặn trực tiếp truy vấn GET_global / GET_secure / GET_system từ các ứng dụng ngân hàng
 *    có chống hook gắt (như VCB Digibank, Techcombank, MB Bank, VNeID) mà KHÔNG cần tiêm Xposed
 *    vào tiến trình của app ngân hàng!
 * 2. Tầng Ứng dụng (App Scope):
 *    - Settings.Global / Secure / System (development_settings_enabled, adb_enabled, adb_wifi_enabled -> 0)
 *    - Cờ vị trí giả lập (mock_location, allow_mock_location, Location.isMock / isFromMockProvider -> false)
 *    - Thuộc tính hệ thống ADB / Debug (SystemProperties: sys.usb.state, sys.usb.config, init.svc.adbd,
 *      service.adb.tcp.port, persist.adb.tls_server.enable, ro.debuggable, ro.secure)
 *    - Trạng thái Debugger (Debug.isDebuggerConnected / waitingForDebugger -> false)
 */
public class HideDevModeHook implements IXposedHookLoadPackage {

    private static final Set<Class<?>> HOOKED_PROVIDER_CLASSES =
            Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    private static boolean isHiddenDevKey(Object keyObj) {
        if (!(keyObj instanceof String)) return false;
        String k = (String) keyObj;
        return "development_settings_enabled".equals(k)
                || "adb_enabled".equals(k)
                || "adb_wifi_enabled".equals(k)
                || "mock_location".equals(k)
                || "allow_mock_location".equals(k)
                || "wait_for_debugger".equals(k)
                || "stay_on_while_plugged_in".equals(k)
                || "verifier_verify_adb_installs".equals(k);
    }

    private static boolean shouldHideForCallingUid(int callingUid) {
        int appId = callingUid % 100000;
        // Chỉ ẩn đối với các ứng dụng người dùng (UID >= 10000).
        // Giữ nguyên cho hệ thống (1000), Cài đặt gốc com.android.settings (1000) và ADB shell (2000).
        return appId >= 10000;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || lpparam.packageName == null) return;
        String pkg = lpparam.packageName;

        // 1. Chế độ System Framework (android / system / com.android.providers.settings):
        // Giúp ẩn DevMode & ADB cho VCB Digibank và các app ngân hàng chống hook mà KHÔNG cần tích VCB trong LSPosed!
        if ("android".equals(pkg)
                || "system".equals(pkg)
                || "com.android.providers.settings".equals(pkg)
                || "system".equals(lpparam.processName)) {
            if ("android".equals(pkg)
                    || "system".equals(pkg)
                    || "com.android.providers.settings".equals(pkg)) {
                hookSystemSettingsProvider(lpparam.classLoader);
            }
            return;
        }

        // Không can thiệp vào trình Cài đặt gốc của máy, ADB shell và các tiến trình hệ thống UID < 10000
        if ("com.android.settings".equals(pkg)
                || "com.android.shell".equals(pkg)
                || android.os.Process.myUid() < 10000) {
            return;
        }

        if ("com.heavietnam.hidedevmode".equals(pkg)) {
            try {
                XposedHelpers.findAndHookMethod(
                        "com.heavietnam.hidedevmode.MainActivity",
                        lpparam.classLoader,
                        "isModuleActive",
                        XC_MethodReplacement.returnConstant(true));
            } catch (Throwable ignored) {
            }
        }

        XposedBridge.log("[HideDevMode] Active in package: " + pkg);

        hookSettingsClasses();
        hookContentResolverCall();
        hookSystemProperties();
        hookDebuggerStatus();
        hookMockLocationFlags();
    }

    private void hookSystemSettingsProvider(ClassLoader cl) {
        XposedBridge.log("[HideDevMode] System Framework SettingsProvider hook initialized");

        // Hook ContentProvider$Transport.call(...) trong framework.jar (chạy trong system_server)
        try {
            Class<?> transportClass = XposedHelpers.findClass("android.content.ContentProvider$Transport", cl);
            XposedBridge.hookAllMethods(
                    transportClass,
                    "call",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 2) return;
                            if (!shouldHideForCallingUid(Binder.getCallingUid())) return;

                            for (int i = 0; i < param.args.length - 1; i++) {
                                Object mObj = param.args[i];
                                if (mObj instanceof String) {
                                    String m = (String) mObj;
                                    if ("GET_global".equals(m) || "GET_secure".equals(m) || "GET_system".equals(m)) {
                                        Object keyObj = param.args[i + 1];
                                        if (isHiddenDevKey(keyObj)) {
                                            Object resObj = param.getResult();
                                            if (resObj instanceof Bundle && ((Bundle) resObj).size() > 1) {
                                                ((Bundle) resObj).putString("value", "0");
                                            } else {
                                                Bundle b = new Bundle(1);
                                                b.putString("value", "0");
                                                param.setResult(b);
                                            }
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[HideDevMode] Transport hook error: " + t.getMessage());
        }

        // Hook trực tiếp SettingsProvider khi khởi tạo trong system_server
        try {
            XposedBridge.hookAllMethods(
                    ContentProvider.class,
                    "attachInfo",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject == null) return;
                            Class<?> cls = param.thisObject.getClass();
                            if ("com.android.providers.settings.SettingsProvider".equals(cls.getName())) {
                                hookSettingsProviderClass(cls);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> spClass = XposedHelpers.findClass("com.android.providers.settings.SettingsProvider", cl);
            hookSettingsProviderClass(spClass);
        } catch (Throwable ignored) {
        }
    }

    private void hookSettingsProviderClass(Class<?> spClass) {
        if (spClass == null || !HOOKED_PROVIDER_CLASSES.add(spClass)) return;
        try {
            XposedBridge.hookAllMethods(
                    spClass,
                    "call",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 2) return;
                            if (!shouldHideForCallingUid(Binder.getCallingUid())) return;
                            Object mObj = param.args[0];
                            Object keyObj = param.args[1];
                            if (mObj instanceof String && isHiddenDevKey(keyObj)) {
                                String m = (String) mObj;
                                if ("GET_global".equals(m) || "GET_secure".equals(m) || "GET_system".equals(m)) {
                                    Object resObj = param.getResult();
                                    if (resObj instanceof Bundle && ((Bundle) resObj).size() > 1) {
                                        ((Bundle) resObj).putString("value", "0");
                                    } else {
                                        Bundle b = new Bundle(1);
                                        b.putString("value", "0");
                                        param.setResult(b);
                                    }
                                }
                            }
                        }
                    });
            XposedBridge.log("[HideDevMode] Hooked SettingsProvider.call successfully");
        } catch (Throwable ignored) {
        }

        try {
            Class<?> settingClass = XposedHelpers.findClass(
                    "com.android.providers.settings.SettingsState$Setting",
                    spClass.getClassLoader());
            XposedBridge.hookAllMethods(
                    settingClass,
                    "getValue",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject == null) return;
                            if (!shouldHideForCallingUid(Binder.getCallingUid())) return;
                            try {
                                Object nameObj = XposedHelpers.getObjectField(param.thisObject, "name");
                                if (isHiddenDevKey(nameObj)) {
                                    param.setResult("0");
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log("[HideDevMode] Hooked SettingsState$Setting.getValue successfully");
        } catch (Throwable ignored) {
        }
    }

    private void hookSettingsClasses() {
        XC_MethodHook stringZeroHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length >= 2 && isHiddenDevKey(param.args[1])) {
                    param.setResult("0");
                }
            }
        };

        XC_MethodHook intZeroHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length >= 2 && isHiddenDevKey(param.args[1])) {
                    param.setResult(0);
                }
            }
        };

        XC_MethodHook longZeroHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length >= 2 && isHiddenDevKey(param.args[1])) {
                    param.setResult(0L);
                }
            }
        };

        XC_MethodHook floatZeroHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length >= 2 && isHiddenDevKey(param.args[1])) {
                    param.setResult(0.0f);
                }
            }
        };

        Class<?>[] settingsClasses = new Class<?>[]{
                Settings.Global.class,
                Settings.Secure.class,
                Settings.System.class
        };

        for (Class<?> cls : settingsClasses) {
            try {
                XposedBridge.hookAllMethods(cls, "getString", stringZeroHook);
                XposedBridge.hookAllMethods(cls, "getStringForUser", stringZeroHook);
                XposedBridge.hookAllMethods(cls, "getInt", intZeroHook);
                XposedBridge.hookAllMethods(cls, "getIntForUser", intZeroHook);
                XposedBridge.hookAllMethods(cls, "getLong", longZeroHook);
                XposedBridge.hookAllMethods(cls, "getLongForUser", longZeroHook);
                XposedBridge.hookAllMethods(cls, "getFloat", floatZeroHook);
                XposedBridge.hookAllMethods(cls, "getFloatForUser", floatZeroHook);
            } catch (Throwable ignored) {
            }
        }
    }

    private void hookContentResolverCall() {
        try {
            XposedBridge.hookAllMethods(
                    ContentResolver.class,
                    "call",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 3) return;
                            Object methodObj = param.args[1];
                            Object argObj = param.args[2];
                            if (methodObj instanceof String && isHiddenDevKey(argObj)) {
                                String m = (String) methodObj;
                                if (m.startsWith("GET_")) {
                                    Bundle b = new Bundle();
                                    b.putString("value", "0");
                                    param.setResult(b);
                                }
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void hookSystemProperties() {
        try {
            Class<?> sysProps = XposedHelpers.findClass("android.os.SystemProperties", null);

            XposedBridge.hookAllMethods(
                    sysProps,
                    "get",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof String)) {
                                return;
                            }
                            String key = (String) param.args[0];
                            if ("sys.usb.state".equals(key)
                                    || "sys.usb.config".equals(key)
                                    || "persist.sys.usb.config".equals(key)) {
                                param.setResult("mtp");
                            } else if ("init.svc.adbd".equals(key)) {
                                param.setResult("stopped");
                            } else if ("service.adb.tcp.port".equals(key)
                                    || "persist.adb.tls_server.enable".equals(key)
                                    || "ro.debuggable".equals(key)) {
                                param.setResult("0");
                            } else if ("ro.secure".equals(key)) {
                                param.setResult("1");
                            }
                        }
                    });

            XposedBridge.hookAllMethods(
                    sysProps,
                    "getInt",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof String)) {
                                return;
                            }
                            String key = (String) param.args[0];
                            if ("ro.debuggable".equals(key)
                                    || "service.adb.tcp.port".equals(key)
                                    || "persist.adb.tls_server.enable".equals(key)) {
                                param.setResult(0);
                            } else if ("ro.secure".equals(key)) {
                                param.setResult(1);
                            }
                        }
                    });

            XposedBridge.hookAllMethods(
                    sysProps,
                    "getLong",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof String)) {
                                return;
                            }
                            String key = (String) param.args[0];
                            if ("ro.debuggable".equals(key)
                                    || "service.adb.tcp.port".equals(key)
                                    || "persist.adb.tls_server.enable".equals(key)) {
                                param.setResult(0L);
                            } else if ("ro.secure".equals(key)) {
                                param.setResult(1L);
                            }
                        }
                    });

            XposedBridge.hookAllMethods(
                    sysProps,
                    "getBoolean",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof String)) {
                                return;
                            }
                            String key = (String) param.args[0];
                            if ("ro.debuggable".equals(key) || "persist.adb.tls_server.enable".equals(key)) {
                                param.setResult(false);
                            } else if ("ro.secure".equals(key)) {
                                param.setResult(true);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void hookDebuggerStatus() {
        try {
            XposedHelpers.findAndHookMethod(
                    Debug.class,
                    "isDebuggerConnected",
                    XC_MethodReplacement.returnConstant(false));
            XposedHelpers.findAndHookMethod(
                    Debug.class,
                    "waitingForDebugger",
                    XC_MethodReplacement.returnConstant(false));
        } catch (Throwable ignored) {
        }
    }

    private void hookMockLocationFlags() {
        try {
            XposedHelpers.findAndHookMethod(
                    Location.class,
                    "isFromMockProvider",
                    XC_MethodReplacement.returnConstant(false));
            if (Build.VERSION.SDK_INT >= 31) {
                XposedHelpers.findAndHookMethod(
                        Location.class,
                        "isMock",
                        XC_MethodReplacement.returnConstant(false));
            }
        } catch (Throwable ignored) {
        }
    }
}

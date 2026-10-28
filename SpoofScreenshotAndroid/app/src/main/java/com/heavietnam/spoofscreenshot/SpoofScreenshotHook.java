package com.heavietnam.spoofscreenshot;

import android.app.Activity;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SpoofScreenshotHook implements IXposedHookLoadPackage {

    private static final String SELF_PKG = "com.heavietnam.spoofscreenshot";
    private static final int FLAG_SECURE = WindowManager.LayoutParams.FLAG_SECURE; // 0x00002000
    private static final int SURFACE_SECURE = 0x80; // SurfaceControl.SECURE

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) {
            return;
        }

        final String pkg = lpparam.packageName;

        // 1. Báo trạng thái kích hoạt cho giao diện Self-Test của SpoofScreenshot
        if (SELF_PKG.equals(pkg)) {
            try {
                XposedHelpers.findAndHookMethod(
                        SELF_PKG + ".MainActivity",
                        lpparam.classLoader,
                        "isModuleActive",
                        XC_MethodReplacement.returnConstant(true)
                );
            } catch (Throwable t) {
                XposedBridge.log("SpoofScreenshot: self-hook error: " + t.getMessage());
            }
        }

        // 2. Đánh dấu trạng thái kích hoạt ở tầng SystemUI (UID 1000) & System Server (UID 1000)
        if ("com.android.systemui".equals(pkg)) {
            setSysPropSafe("debug.hwui.capture_ready", "1");
        }

        // 3. Hook trong System Framework (system_server) -> hiệu lực toàn cục cho MỌI app (kể cả app ngân hàng & VNeID không cần tích)
        if ("android".equals(pkg) || "system".equals(pkg)) {
            setSysPropSafe("debug.hwui.capture_ready", "1");
            setSysPropSafe("debug.hwui.capture_sys", "1");
            hookSystemServer(lpparam.classLoader);
        }

        // 4. Hook các class Framework trong tiến trình hiện tại
        hookFrameworkClasses(lpparam.classLoader);
    }

    private void hookSystemServer(ClassLoader cl) {
        // 3.1. WindowState.isSecureLocked() -> luôn trả về false
        try {
            Class<?> windowStateCls = XposedHelpers.findClass("com.android.server.wm.WindowState", cl);
            XposedBridge.hookAllMethods(windowStateCls, "isSecureLocked", XC_MethodReplacement.returnConstant(false));
        } catch (Throwable ignored) {
        }

        // 3.2. Xiaomi MIUI / HyperOS: WindowManagerServiceImpl.notAllowCaptureDisplay(...) -> luôn trả về false
        try {
            Class<?> wmsImplCls = XposedHelpers.findClass("com.android.server.wm.WindowManagerServiceImpl", cl);
            XposedBridge.hookAllMethods(wmsImplCls, "notAllowCaptureDisplay", XC_MethodReplacement.returnConstant(false));
        } catch (Throwable ignored) {
        }

        // 3.3. WindowSurfaceController: xóa cờ SECURE (0x80) khi khởi tạo & chặn setSecure(true)
        try {
            Class<?> wscCls = XposedHelpers.findClass("com.android.server.wm.WindowSurfaceController", cl);
            hookAllConstructorsSafe(wscCls, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 3 && param.args[2] instanceof Integer) {
                        int flags = (Integer) param.args[2];
                        param.args[2] = flags & ~SURFACE_SECURE;
                    }
                }
            });
            XposedBridge.hookAllMethods(wscCls, "setSecure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean) {
                        param.args[0] = false;
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // 3.4. Chặn phát hiện chụp màn hình Android 14/15 (ActivityTaskManagerService.registerScreenCaptureObserver)
        try {
            Class<?> atmsCls = XposedHelpers.findClass("com.android.server.wm.ActivityTaskManagerService", cl);
            XposedBridge.hookAllMethods(atmsCls, "registerScreenCaptureObserver", XC_MethodReplacement.returnConstant(null));
        } catch (Throwable ignored) {
        }

        // 3.5. Chặn thông báo chụp màn hình MIUI/AOSP (WindowManagerService.notifyScreenshotListeners)
        //      và ép mCaptureSecureLayers = true trong captureDisplay / captureLayers
        try {
            Class<?> wmsCls = XposedHelpers.findClass("com.android.server.wm.WindowManagerService", cl);
            XposedBridge.hookAllMethods(wmsCls, "notifyScreenshotListeners",
                    XC_MethodReplacement.returnConstant(Collections.emptyList()));
            XposedBridge.hookAllMethods(wmsCls, "captureDisplay", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null) {
                        for (Object arg : param.args) {
                            if (arg != null && arg.getClass().getName().contains("CaptureArgs")) {
                                setBooleanFieldSafe(arg, "mCaptureSecureLayers", true);
                            }
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // 3.6. Chặn chính sách quản trị thiết bị cấm chụp màn hình (DevicePolicyManagerService.getScreenCaptureDisabled)
        try {
            Class<?> dpmsCls = XposedHelpers.findClass("com.android.server.devicepolicy.DevicePolicyManagerService", cl);
            XposedBridge.hookAllMethods(dpmsCls, "getScreenCaptureDisabled", XC_MethodReplacement.returnConstant(false));
        } catch (Throwable ignored) {
        }
    }

    private void hookFrameworkClasses(ClassLoader cl) {
        // 4.1. ScreenCapture$CaptureArgs & Builder: luôn bật mCaptureSecureLayers = true
        try {
            Class<?> builderCls = XposedHelpers.findClass("android.window.ScreenCapture$CaptureArgs$Builder", cl);
            XposedBridge.hookAllMethods(builderCls, "setCaptureSecureLayers", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean) {
                        param.args[0] = true;
                    }
                }
            });
            XposedBridge.hookAllMethods(builderCls, "build", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    setBooleanFieldSafe(param.thisObject, "mCaptureSecureLayers", true);
                }
            });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> captureArgsCls = XposedHelpers.findClass("android.window.ScreenCapture$CaptureArgs", cl);
            hookAllConstructorsSafe(captureArgsCls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    setBooleanFieldSafe(param.thisObject, "mCaptureSecureLayers", true);
                }
            });
        } catch (Throwable ignored) {
        }

        // 4.2. SurfaceControl$Builder: chặn setSecure(true) và xóa bit 0x80 trong setFlags
        try {
            Class<?> scBuilderCls = XposedHelpers.findClass("android.view.SurfaceControl$Builder", cl);
            XposedBridge.hookAllMethods(scBuilderCls, "setSecure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean) {
                        param.args[0] = false;
                    }
                }
            });
            XposedBridge.hookAllMethods(scBuilderCls, "setFlags", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Integer) {
                        param.args[0] = ((Integer) param.args[0]) & ~SURFACE_SECURE;
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // 4.3. SurfaceControl$Transaction: chặn setSecure(sc, true) và xóa bit 0x80 trong setFlags
        try {
            Class<?> scTxCls = XposedHelpers.findClass("android.view.SurfaceControl$Transaction", cl);
            XposedBridge.hookAllMethods(scTxCls, "setSecure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 2 && param.args[1] instanceof Boolean) {
                        param.args[1] = false;
                    }
                }
            });
            XposedBridge.hookAllMethods(scTxCls, "setFlags", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 2 && param.args[1] instanceof Integer) {
                        param.args[1] = ((Integer) param.args[1]) & ~SURFACE_SECURE;
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        // 4.4. Window.setFlags & Window.addFlags: xóa FLAG_SECURE (0x2000)
        try {
            XposedHelpers.findAndHookMethod(Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int flags = (Integer) param.args[0];
                    param.args[0] = flags & ~FLAG_SECURE;
                }
            });
            XposedHelpers.findAndHookMethod(Window.class, "addFlags", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int flags = (Integer) param.args[0];
                    param.args[0] = flags & ~FLAG_SECURE;
                }
            });
        } catch (Throwable ignored) {
        }

        // 4.5. WindowManagerImpl & WindowManagerGlobal: xóa FLAG_SECURE trong LayoutParams khi addView / updateViewLayout
        XC_MethodHook stripLpHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null) return;
                for (Object arg : param.args) {
                    if (arg instanceof WindowManager.LayoutParams) {
                        ((WindowManager.LayoutParams) arg).flags &= ~FLAG_SECURE;
                    }
                }
            }
        };
        try {
            Class<?> wmiCls = XposedHelpers.findClass("android.view.WindowManagerImpl", cl);
            XposedBridge.hookAllMethods(wmiCls, "addView", stripLpHook);
            XposedBridge.hookAllMethods(wmiCls, "updateViewLayout", stripLpHook);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> wmgCls = XposedHelpers.findClass("android.view.WindowManagerGlobal", cl);
            XposedBridge.hookAllMethods(wmgCls, "addView", stripLpHook);
            XposedBridge.hookAllMethods(wmgCls, "updateViewLayout", stripLpHook);
        } catch (Throwable ignored) {
        }

        // 4.6. SurfaceView.setSecure(boolean) -> luôn ép false
        try {
            XposedHelpers.findAndHookMethod(SurfaceView.class, "setSecure", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = false;
                }
            });
        } catch (Throwable ignored) {
        }

        // 4.7. Activity.registerScreenCaptureCallback (Android 14+) -> vô hiệu hóa callback báo chụp màn hình
        //      Activity.setRecentsScreenshotEnabled -> luôn ép true
        try {
            XposedBridge.hookAllMethods(Activity.class, "registerScreenCaptureCallback",
                    XC_MethodReplacement.returnConstant(null));
            XposedBridge.hookAllMethods(Activity.class, "setRecentsScreenshotEnabled", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Boolean) {
                        param.args[0] = true;
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookAllConstructorsSafe(Class<?> clazz, XC_MethodHook callback) {
        try {
            Method m = XposedBridge.class.getMethod("hookAllConstructors", Class.class, XC_MethodHook.class);
            m.invoke(null, clazz, callback);
        } catch (Throwable ignored) {
        }
    }

    private static void setBooleanFieldSafe(Object obj, String fieldName, boolean value) {
        if (obj == null) return;
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setBoolean(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return;
            }
        }
    }

    private static void setSysPropSafe(String key, String val) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method set = sp.getMethod("set", String.class, String.class);
            set.invoke(null, key, val);
        } catch (Throwable ignored) {
        }
    }
}

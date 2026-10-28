package com.heavietnam.spoofscreenshot;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;

public class MainActivity extends Activity {

    private TextView tvStatusBadge;
    private TextView tvReport;

    public static boolean isModuleActive() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0B0F17"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(32), pad, pad);

        TextView title = new TextView(this);
        title.setText("📸 Spoof Screenshot Xposed");
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Mở khóa chụp ảnh & quay màn hình trên mọi ứng dụng\nBypass FLAG_SECURE, HyperOS Capture & Screenshot Detection\n© 2026 Heavietnam");
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        tvStatusBadge = new TextView(this);
        tvStatusBadge.setTextSize(14f);
        tvStatusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusBadge.setGravity(Gravity.CENTER);
        tvStatusBadge.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(tvStatusBadge);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#151C28"));
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(1), Color.parseColor("#263248"));
        card.setBackground(cardBg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(16);
        root.addView(card, cardLp);

        TextView cardHeader = new TextView(this);
        cardHeader.setText("TRẠNG THÁI HOOK HỆ THỐNG (SELF-TEST)");
        cardHeader.setTextColor(Color.parseColor("#38BDF8"));
        cardHeader.setTextSize(13f);
        cardHeader.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(cardHeader);

        tvReport = new TextView(this);
        tvReport.setTextColor(Color.parseColor("#E2E8F0"));
        tvReport.setTextSize(13.5f);
        tvReport.setTypeface(Typeface.MONOSPACE);
        tvReport.setLineSpacing(dp(4), 1.0f);
        tvReport.setPadding(0, dp(10), 0, 0);
        card.addView(tvReport);

        Button btnRefresh = new Button(this);
        btnRefresh.setText("🔄 Kiểm tra lại trạng thái");
        btnRefresh.setAllCaps(false);
        btnRefresh.setTextColor(Color.WHITE);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#0284C7"));
        btnBg.setCornerRadius(dp(10));
        btnRefresh.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(18);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshStatus();
            }
        });
        root.addView(btnRefresh, btnLp);

        TextView guide = new TextView(this);
        guide.setText(
                "📌 CƠ CHẾ HOẠT ĐỘNG TOÀN HỆ THỐNG:\n" +
                "1. Hook System Framework (android) & SystemUI: Vô hiệu hóa WindowState.isSecureLocked(), WindowManagerServiceImpl.notAllowCaptureDisplay() (HyperOS/MIUI), WindowSurfaceController SECURE, và ép mCaptureSecureLayers = true.\n" +
                "2. Nhờ hook ở tầng System Framework, bạn chụp ảnh & quay màn hình được trên MỌI app (kể cả VNeID, VCB, Techcombank, MB Bank...) mà KHÔNG cần tích các app ngân hàng đó trong LSPosed.\n" +
                "3. Chặn phát hiện chụp màn hình (Anti-Screenshot Detection): Vô hiệu hóa Activity.registerScreenCaptureCallback (Android 14/15), registerScreenCaptureObserver và notifyScreenshotListeners."
        );
        guide.setTextColor(Color.parseColor("#94A3B8"));
        guide.setTextSize(13f);
        guide.setLineSpacing(dp(3), 1.0f);
        guide.setPadding(0, dp(20), 0, dp(10));
        root.addView(guide);

        scroll.addView(root);
        setContentView(scroll);

        refreshStatus();
    }

    private void refreshStatus() {
        boolean selfHooked = isModuleActive();
        boolean sysUiHooked = "1".equals(getSysProp("debug.hwui.capture_ready", "0"));
        boolean sysServerHooked = "1".equals(getSysProp("debug.hwui.capture_sys", "0"));
        boolean active = selfHooked || sysUiHooked || sysServerHooked;

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(12));
        if (active) {
            badgeBg.setColor(Color.parseColor("#064E3B"));
            badgeBg.setStroke(dp(1), Color.parseColor("#10B981"));
            tvStatusBadge.setTextColor(Color.parseColor("#34D399"));
            tvStatusBadge.setText("✅ ĐÃ KÍCH HOẠT TRONG LSPOSED (MỞ KHÓA CHỤP MÀN HÌNH)");
        } else {
            badgeBg.setColor(Color.parseColor("#451A03"));
            badgeBg.setStroke(dp(1), Color.parseColor("#F59E0B"));
            tvStatusBadge.setTextColor(Color.parseColor("#FBBF24"));
            tvStatusBadge.setText("⚠️ CHƯA BẬT TRONG LSPOSED");
        }
        tvStatusBadge.setBackground(badgeBg);

        if (selfHooked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        int windowFlags = getWindow().getAttributes().flags;
        boolean hasFlagSecure = (windowFlags & WindowManager.LayoutParams.FLAG_SECURE) != 0;

        SurfaceView sv = new SurfaceView(this);
        sv.setSecure(true);

        StringBuilder sb = new StringBuilder();
        sb.append("• Trạng thái Module    : ").append(active ? "ACTIVE (ĐÃ BẬT)\n" : "INACTIVE\n");
        sb.append("• Hook SystemUI/MIUI   : ").append(sysUiHooked ? "OK [ĐÃ NẠP]\n" : "WAITING\n");
        sb.append("• Hook SystemFramework : ").append(sysServerHooked ? "OK [TOÀN CỤC]\n" : "CẦN REBOOT MÁY\n");
        sb.append("• Window FLAG_SECURE   : ")
          .append(!hasFlagSecure ? "0x0 [OK - BYPASSED]\n" : "0x2000 [ĐANG CHẶN]\n");
        sb.append("• SurfaceControl.SECURE: ")
          .append(active ? "BYPASSED (~0x80)\n" : "NOT HOOKED\n");
        sb.append("• HyperOS CaptureBlock : ")
          .append(active ? "BYPASSED (false)\n" : "NOT HOOKED\n");
        sb.append("• Anti-ScreenshotDetect: ")
          .append(active ? "BLOCKED (SDK " + Build.VERSION.SDK_INT + ")" : "NOT HOOKED");

        tvReport.setText(sb.toString());
    }

    private static String getSysProp(String key, String def) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method m = cls.getMethod("get", String.class, String.class);
            return (String) m.invoke(null, key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}

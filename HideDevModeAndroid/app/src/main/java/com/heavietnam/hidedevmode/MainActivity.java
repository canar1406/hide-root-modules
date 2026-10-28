package com.heavietnam.hidedevmode;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Debug;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
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
        title.setText("🛡️ Hide DevMode Xposed");
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Ẩn Tùy chọn nhà phát triển, USB/Wi-Fi ADB & Mock Location\n© 2026 Heavietnam (home.heavietnam.com)");
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
        cardHeader.setText("KIỂM TRA TRỰC TIẾP (SELF-TEST)");
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
                "📌 HƯỚNG DẪN SỬ DỤNG TRONG LSPOSED:\n" +
                "1. Mở LSPosed -> Mô-đun -> Bật 'Hide DevMode'.\n" +
                "2. Với VCB Digibank & app ngân hàng chống hook: Chỉ cần tích 'Khung hệ thống (System Framework)' trong LSPosed, KHÔNG tích trực tiếp VCB trong LSPosed, và thêm 'Hide DevMode' vào template ẩn của Hide My Applist (HMA).\n" +
                "3. Với các app thông thường (MoMo, VNeID, vRace...): Có thể tích trực tiếp từng app để có hiệu lực ngay không cần khởi động lại.\n" +
                "4. KHÔNG tích ứng dụng 'Cài đặt (Settings)' của máy."
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
        boolean active = isModuleActive();
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(12));
        if (active) {
            badgeBg.setColor(Color.parseColor("#064E3B"));
            badgeBg.setStroke(dp(1), Color.parseColor("#10B981"));
            tvStatusBadge.setTextColor(Color.parseColor("#34D399"));
            tvStatusBadge.setText("✅ ĐÃ KÍCH HOẠT TRONG LSPOSED (ĐANG ẨN DEVMODE)");
        } else {
            badgeBg.setColor(Color.parseColor("#451A03"));
            badgeBg.setStroke(dp(1), Color.parseColor("#F59E0B"));
            tvStatusBadge.setTextColor(Color.parseColor("#FBBF24"));
            tvStatusBadge.setText("⚠️ CHƯA BẬT TRONG LSPOSED (HOẶC CHƯA TÍCH APP NÀY)");
        }
        tvStatusBadge.setBackground(badgeBg);

        int devGlobal = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, -1);
        int adbGlobal = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, -1);
        int adbWifi = Settings.Global.getInt(getContentResolver(), "adb_wifi_enabled", 0);
        int mockSecure = Settings.Secure.getInt(getContentResolver(), "mock_location", 0);
        String usbState = getSysProp("sys.usb.state", "unknown");
        String adbdSvc = getSysProp("init.svc.adbd", "unknown");
        String debuggable = getSysProp("ro.debuggable", "0");
        boolean debugger = Debug.isDebuggerConnected();

        StringBuilder sb = new StringBuilder();
        sb.append("• development_settings : ").append(devGlobal)
          .append(devGlobal == 0 ? "  [OK - ĐÃ ẨN]\n" : "  [ĐANG LỘ = 1]\n");
        sb.append("• adb_enabled (USB)    : ").append(adbGlobal)
          .append(adbGlobal == 0 ? "  [OK - ĐÃ ẨN]\n" : "  [ĐANG LỘ = 1]\n");
        sb.append("• adb_wifi_enabled     : ").append(adbWifi)
          .append(adbWifi == 0 ? "  [OK - ĐÃ ẨN]\n" : "  [ĐANG LỘ = 1]\n");
        sb.append("• mock_location        : ").append(mockSecure)
          .append(mockSecure == 0 ? "  [OK - ĐÃ ẨN]\n" : "  [ĐANG LỘ = 1]\n");
        sb.append("• sys.usb.state        : ").append(usbState)
          .append(!usbState.contains("adb") ? "  [OK]\n" : "  [CÓ ADB]\n");
        sb.append("• init.svc.adbd        : ").append(adbdSvc).append("\n");
        sb.append("• ro.debuggable        : ").append(debuggable).append("\n");
        sb.append("• isDebuggerConnected  : ").append(debugger);

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

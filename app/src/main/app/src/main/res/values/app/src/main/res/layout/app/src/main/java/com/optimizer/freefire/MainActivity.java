package com.optimizer.freefire;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class MainActivity extends Activity {
    TextView tvRamFree, tvRamTotal, tvStatus, tvCacheSize, tvShizukuStatus;
    Button btnOptimize, btnFreeFire, btnClearCache, btnAnimations, btnShizuku;
    ProgressBar pbRam;
    boolean animationsDisabled = false;
    boolean shizukuAvailable = false;
    private static final int SHIZUKU_CODE = 100;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener =
        (requestCode, grantResult) -> {
            if (requestCode == SHIZUKU_CODE) {
                shizukuAvailable = grantResult == PackageManager.PERMISSION_GRANTED;
                updateShizukuStatus(shizukuAvailable);
                setStatus(shizukuAvailable ? "✓ Shizuku conectado!" : "✗ Permissão negada");
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        tvRamFree       = findViewById(R.id.tvRamFree);
        tvRamTotal      = findViewById(R.id.tvRamTotal);
        tvStatus        = findViewById(R.id.tvStatus);
        tvCacheSize     = findViewById(R.id.tvCacheSize);
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        pbRam           = findViewById(R.id.pbRam);
        btnOptimize     = findViewById(R.id.btnOptimize);
        btnFreeFire     = findViewById(R.id.btnFreeFire);
        btnClearCache   = findViewById(R.id.btnClearCache);
        btnAnimations   = findViewById(R.id.btnAnimations);
        btnShizuku      = findViewById(R.id.btnShizuku);
        btnOptimize.setOnClickListener(v -> runOptimize());
        btnFreeFire.setOnClickListener(v -> runFreeFireMode());
        btnClearCache.setOnClickListener(v -> clearAppCache());
        btnAnimations.setOnClickListener(v -> toggleAnimations());
        btnShizuku.setOnClickListener(v -> requestShizuku());
        Shizuku.addRequestPermissionResultListener(shizukuListener);
        updateRamInfo();
        updateCacheSize();
        checkShizuku();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(shizukuListener);
    }

    void checkShizuku() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuAvailable = true;
                updateShizukuStatus(true);
                setStatus("✓ Shizuku conectado!");
            } else {
                updateShizukuStatus(false);
            }
        } catch (Exception e) {
            updateShizukuStatus(false);
        }
    }

    void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Abra o Shizuku e inicie o serviço", Toast.LENGTH_LONG).show();
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (i != null) startActivity(i);
                } catch (Exception ignored) {}
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuAvailable = true;
                updateShizukuStatus(true);
                setStatus("✓ Shizuku já conectado!");
            } else {
                Shizuku.requestPermission(SHIZUKU_CODE);
            }
        } catch (Exception e) {
            setStatus("Erro: " + e.getMessage());
        }
    }

    void updateShizukuStatus(boolean on) {
        if (on) {
            tvShizukuStatus.setText("● Shizuku conectado");
            tvShizukuStatus.setTextColor(0xFF4ADE80);
            btnShizuku.setText("Shizuku: Ativo ✓");
            btnAnimations.setAlpha(1.0f);
            btnAnimations.setEnabled(true);
        } else {
            tvShizukuStatus.setText("○ Shizuku desconectado");
            tvShizukuStatus.setTextColor(0xFFFF6B6B);
            btnShizuku.setText("Conectar Shizuku");
            btnAnimations.setAlpha(0.5f);
        }
    }

    void toggleAnimations() {
        if (!shizukuAvailable) {
            Toast.makeText(this, "Conecte o Shizuku primeiro", Toast.LENGTH_SHORT).show();
            return;
        }
        float scale = animationsDisabled ? 1.0f : 0.0f;
        boolean ok = false;
        try {
            Settings.Global.putFloat(getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, scale);
            ok = true;
        } catch (SecurityException ignored) {}
        if (!ok) ok = runCmd("settings put global window_animation_scale " + scale + " && settings put global transition_animation_scale " + scale + " && settings put global animator_duration_scale " + scale);
        if (ok) {
            animationsDisabled = !animationsDisabled;
            btnAnimations.setText(animationsDisabled ? "Animações: OFF ✓" : "Animações: ON");
            setStatus(animationsDisabled ? "✓ Animações desativadas" : "✓ Animações reativadas");
        }
    }

    boolean runCmd(String cmd) {
        try {
            return Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null).waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    void updateRamInfo() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long free = mi.availMem / (1024*1024), total = mi.totalMem / (1024*1024);
        tvRamFree.setText("Livre: " + free + " MB");
        tvRamTotal.setText("Total: " + total + " MB");
        pbRam.setProgress((int)(100 - mi.availMem * 100 / mi.totalMem));
    }

    void killBackground() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        PackageManager pm = getPackageManager();
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        int k = 0;
        if (procs == null) return;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(p.processName, 0);
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !p.processName.equals(getPackageName())) {
                        am.killBackgroundProcesses(p.processName); k++;
                    }
                } catch (Exception ignored) {}
            }
        }
        setStatus("✓ " + k

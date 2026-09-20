package com.catkiss.whalelive2ddeskpet;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements WhaleView.Listener {
    private static final String VERSION = "v0.1.0 · 原生目录与悬浮桌宠测试";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WhaleView whaleView;
    private TextView status;
    private TextView catalogSummary;
    private LinearLayout catalogArea;
    private WhaleCatalog catalog;
    private boolean overlayRequested;

    private final ActivityResultLauncher<String[]> zipPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importZip);
    private final ActivityResultLauncher<Intent> overlayPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Settings.canDrawOverlays(this)) startOverlayNow();
                else setStatus("尚未授予悬浮窗权限");
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overlayRequested = getPreferences(MODE_PRIVATE)
                .getBoolean("overlay_requested", false);
        buildUi();
        loadImportedModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (whaleView != null) whaleView.onHostResume();
    }

    @Override
    protected void onPause() {
        if (whaleView != null) whaleView.onHostPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (whaleView != null) whaleView.release();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(23, 19, 34));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(6), dp(4), dp(6), dp(4));
        Button importButton = button("导入ZIP");
        importButton.setOnClickListener(v -> zipPicker.launch(
                new String[]{"application/zip", "application/octet-stream"}));
        toolbar.addView(importButton);
        Button reloadButton = button("重载");
        reloadButton.setOnClickListener(v -> loadImportedModel());
        toolbar.addView(reloadButton);
        status = new TextView(this);
        status.setText(VERSION);
        status.setTextColor(Color.WHITE);
        status.setTextSize(10);
        status.setSingleLine(true);
        toolbar.addView(status, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        page.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.rgb(34, 28, 48));
        whaleView = new WhaleView(this);
        whaleView.setListener(this);
        stage.addView(whaleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f));

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(9), dp(7), dp(9), dp(16));
        scroll.addView(panel);

        TextView heading = heading("Q版鲸鱼 Live2D 独立测试");
        panel.addView(heading);
        TextView note = note("支持直接导入鼠控版 ZIP，或包含“DS鼠控版.zip”的外层购买包；模型、贴图、表情和动作只保存在 App 私有目录。");
        panel.addView(note);

        LinearLayout overlayRow = new LinearLayout(this);
        overlayRow.setOrientation(LinearLayout.HORIZONTAL);
        Button showOverlay = button("开启系统桌宠");
        showOverlay.setOnClickListener(v -> requestOverlay());
        overlayRow.addView(showOverlay, weighted());
        Button stopOverlay = button("关闭系统桌宠");
        stopOverlay.setOnClickListener(v -> {
            overlayRequested = false;
            getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("overlay_requested", false).apply();
            OverlayPetService.stop(this);
            setStatus("系统桌宠已关闭");
        });
        overlayRow.addView(stopOverlay, weighted());
        panel.addView(overlayRow);

        catalogSummary = note("尚未导入模型");
        panel.addView(catalogSummary);
        catalogArea = new LinearLayout(this);
        catalogArea.setOrientation(LinearLayout.VERTICAL);
        panel.addView(catalogArea);

        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(page);
    }

    private void loadImportedModel() {
        File modelFile = WhaleModelImporter.findImportedModel(this);
        if (modelFile == null) {
            catalog = null;
            rebuildCatalog();
            setStatus("请导入 Q版鲸鱼 ZIP");
            return;
        }
        executor.execute(() -> {
            try {
                WhaleCatalog loaded = WhaleCatalog.scan(modelFile);
                runOnUiThread(() -> installCatalog(loaded));
            } catch (Exception error) {
                runOnUiThread(() -> setStatus("读取已导入模型失败：" + error.getMessage()));
            }
        });
    }

    private void importZip(Uri uri) {
        if (uri == null) return;
        setStatus("准备导入模型…");
        executor.execute(() -> {
            try {
                WhaleCatalog imported = WhaleModelImporter.importZip(
                        this, uri, message -> runOnUiThread(() -> setStatus(message)));
                runOnUiThread(() -> {
                    installCatalog(imported);
                    Toast.makeText(this, "Q版鲸鱼模型导入完成", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> setStatus("导入失败：" + readable(error)));
            }
        });
    }

    private void installCatalog(WhaleCatalog loaded) {
        catalog = loaded;
        whaleView.loadModel(loaded);
        rebuildCatalog();
        setStatus("模型目录已载入，正在创建原生渲染器…");
    }

    private void rebuildCatalog() {
        catalogArea.removeAllViews();
        if (catalog == null) {
            catalogSummary.setText("尚未导入模型；仓库与 APK 均不包含购买素材。");
            return;
        }
        catalogSummary.setText("已扫描 " + catalog.expressions.size() + " 个原生表情 / "
                + catalog.motions.size() + " 个原生动作；idle 自动循环，其余动作单次播放。");

        catalogArea.addView(heading("13类陪玩反应（预览与已开启桌宠同步）"));
        addReactionGrid(WhaleReactionEngine.REACTIONS);
        Button reset = button("还原全部表情与动作");
        reset.setOnClickListener(v -> {
            whaleView.resetAll();
            if (overlayRequested) OverlayPetService.react(this, "__reset__");
        });
        catalogArea.addView(reset);

        catalogArea.addView(heading("模型原生动作（idle循环，其余强制单次）"));
        addEntryGrid(catalog.motions, false);
        catalogArea.addView(heading("模型原生表情/道具（再次点击关闭）"));
        addEntryGrid(catalog.expressions, true);
    }

    private void addReactionGrid(List<WhaleReactionEngine.Reaction> reactions) {
        for (int start = 0; start < reactions.size(); start += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 2; i++) {
                int index = start + i;
                if (index >= reactions.size()) {
                    row.addView(new View(this), weighted());
                    continue;
                }
                WhaleReactionEngine.Reaction reaction = reactions.get(index);
                Button item = button(reaction.label);
                item.setOnClickListener(v -> {
                    whaleView.applyReaction(reaction.id);
                    if (overlayRequested) OverlayPetService.react(this, reaction.id);
                });
                row.addView(item, weighted());
            }
            catalogArea.addView(row);
        }
    }

    private void addEntryGrid(List<WhaleCatalog.Entry> entries, boolean expression) {
        for (int start = 0; start < entries.size(); start += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 3; i++) {
                int index = start + i;
                if (index >= entries.size()) {
                    row.addView(new View(this), weighted());
                    continue;
                }
                WhaleCatalog.Entry entry = entries.get(index);
                Button item = button(entry.label());
                item.setTextSize(9);
                item.setOnClickListener(v -> {
                    if (expression) whaleView.toggleExpression(entry.id);
                    else whaleView.playMotion(entry.id);
                });
                row.addView(item, weighted());
            }
            catalogArea.addView(row);
        }
    }

    private void requestOverlay() {
        if (catalog == null) {
            setStatus("请先导入 Q版鲸鱼模型");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermission.launch(intent);
            return;
        }
        startOverlayNow();
    }

    private void startOverlayNow() {
        overlayRequested = true;
        getPreferences(MODE_PRIVATE).edit().putBoolean("overlay_requested", true).apply();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 120);
        }
        OverlayPetService.show(this);
        setStatus("系统桌宠已开启；可拖动，轻点触发调皮反应");
    }

    @Override
    public void onStatus(String message) {
        runOnUiThread(() -> setStatus(message));
    }

    @Override
    public void onReady(String detail) {
        runOnUiThread(() -> setStatus(detail));
    }

    @Override
    public void onError(Throwable error) {
        runOnUiThread(() -> setStatus("原生渲染失败：" + readable(error)));
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    private String readable(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? (error == null ? "未知错误" : error.getClass().getSimpleName()) : message;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(238, 207, 255));
        view.setTextSize(12);
        view.setPadding(dp(2), dp(8), 0, dp(3));
        return view;
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(205, 194, 220));
        view.setTextSize(10);
        view.setPadding(dp(2), dp(2), dp(2), dp(4));
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(10);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(77, 61, 105));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1.0f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

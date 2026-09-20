package com.catkiss.whalelive2ddeskpet;

import com.live2d.sdk.cubism.framework.CubismModelSettingJson;
import com.live2d.sdk.cubism.framework.ICubismModelSetting;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.model.CubismUserModel;
import com.live2d.sdk.cubism.framework.motion.ACubismMotion;
import com.live2d.sdk.cubism.framework.motion.ACubismUpdater;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion;
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotionManager;
import com.live2d.sdk.cubism.framework.motion.CubismMotion;
import com.live2d.sdk.cubism.framework.motion.CubismPhysicsUpdater;
import com.live2d.sdk.cubism.framework.motion.CubismPoseUpdater;
import com.live2d.sdk.cubism.framework.motion.CubismUpdateOrder;
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class WhaleLive2DModel extends CubismUserModel implements WhaleReactionEngine.Target {
    private final Map<String, CubismExpressionMotion> expressions = new LinkedHashMap<>();
    private final Map<String, CubismExpressionMotionManager> expressionManagers =
            new LinkedHashMap<>();
    private final Set<String> activeExpressions = new LinkedHashSet<>();
    private final Map<String, CubismMotion> motions = new LinkedHashMap<>();
    private ICubismModelSetting setting;
    private WhaleCatalog catalog;
    private File homeDirectory;
    private String idleMotionId = "";
    private String currentMotionId = "";
    private final WhaleIdleController idleController = new WhaleIdleController();

    void load(WhaleCatalog requestedCatalog, int width, int height,
              NativeTextureManager textures, WhaleRenderer.Listener listener) throws IOException {
        catalog = requestedCatalog;
        File modelFile = requestedCatalog.modelFile;
        homeDirectory = modelFile.getParentFile();
        if (homeDirectory == null) throw new IOException("model3 所在目录无效");

        listener.onStatus("正在读取 Q版鲸鱼 model3.json…");
        setting = new CubismModelSettingJson(NativeFileLoader.readFile(modelFile));
        if (setting.getJson() == null) throw new IOException("无法解析 model3.json");
        String mocName = setting.getModelFileName();
        if (mocName == null || mocName.isEmpty()) throw new IOException("model3 没有登记 moc3");
        File mocFile = child(mocName);
        listener.onStatus("正在创建 Cubism 5 模型 · moc3 "
                + String.format(java.util.Locale.ROOT, "%.1f MiB",
                mocFile.length() / 1048576.0));
        loadModel(NativeFileLoader.readFile(mocFile), false);
        if (model == null || modelMatrix == null) throw new IOException("Cubism Core 无法创建模型");

        loadExpressions(listener);
        loadMotions(listener);
        loadPhysicsAndPose(listener);
        Map<String, Float> layout = new HashMap<>();
        if (setting.getLayoutMap(layout)) modelMatrix.setupFromLayout(layout);
        updateScheduler.sortUpdatableList();
        model.saveParameters();
        model.update();

        listener.onStatus("正在创建透明 OpenGL 渲染器…");
        setupNativeRenderer(width, height);
        setupTextures(textures, listener);
    }

    void reloadRenderer(int width, int height, NativeTextureManager textures,
                        WhaleRenderer.Listener listener) throws IOException {
        deleteRenderer();
        setupNativeRenderer(width, height);
        setupTextures(textures, listener);
    }

    void update(float deltaSeconds) {
        if (model == null) return;
        model.loadParameters();
        updateScheduler.onLateUpdate(model, Math.max(0.0f, Math.min(0.05f, deltaSeconds)));
        model.update();
    }

    void draw(CubismMatrix44 matrix) {
        if (model == null || getRenderer() == null) return;
        CubismMatrix44.multiply(modelMatrix.getArray(), matrix.getArray(), matrix.getArray());
        CubismRendererAndroid renderer = getRenderer();
        renderer.setMvpMatrix(matrix);
        renderer.drawModel();
    }

    float getCanvasWidth() {
        return model == null ? 1.0f : model.getCanvasWidth();
    }

    float getCanvasHeight() {
        return model == null ? 1.0f : model.getCanvasHeight();
    }

    void fitWidth(float width) {
        if (modelMatrix != null) modelMatrix.setWidth(width);
    }

    void fitHeight(float height) {
        if (modelMatrix != null) modelMatrix.setHeight(height);
    }

    void toggleExpression(String id) {
        if (activeExpressions.contains(id)) {
            CubismExpressionMotionManager manager = expressionManagers.get(id);
            if (manager != null) manager.stopAllMotions();
            activeExpressions.remove(id);
        } else {
            enableExpression(id);
        }
    }

    @Override
    public void enableExpression(String id) {
        CubismExpressionMotion motion = expressions.get(id);
        CubismExpressionMotionManager manager = expressionManagers.get(id);
        if (motion == null || manager == null || activeExpressions.contains(id)) return;
        manager.startMotionPriority(motion, 3);
        activeExpressions.add(id);
    }

    @Override
    public void resetExpressions() {
        for (CubismExpressionMotionManager manager : expressionManagers.values()) {
            manager.stopAllMotions();
        }
        activeExpressions.clear();
    }

    @Override
    public void playMotion(String id) {
        CubismMotion motion = motions.get(id);
        if (motion == null) return;
        motion.setLoop(false);
        currentMotionId = id;
        motionManager.startMotionPriority(motion, 3);
    }

    void applyReaction(String id) {
        WhaleReactionEngine.apply(id, this);
    }

    void setIdleMode(WhaleIdleController.Mode mode) {
        idleController.setMode(mode);
    }

    void resetAll() {
        resetExpressions();
        motionManager.stopAllMotions();
        currentMotionId = "";
        idleController.reset();
    }

    void closeModel() {
        delete();
    }

    String readyDetail() {
        return catalog.expressions.size() + "个表情 · " + catalog.motions.size()
                + "个动作 · 灵动待机 " + idleController.getMode().label
                + " · 原生idle " + (idleMotionId.isEmpty() ? "未找到" : "手动保留");
    }

    private void loadExpressions(WhaleRenderer.Listener listener) throws IOException {
        int total = catalog.expressions.size();
        for (int i = 0; i < total; i++) {
            WhaleCatalog.Entry entry = catalog.expressions.get(i);
            CubismExpressionMotion motion = loadExpression(NativeFileLoader.readFile(entry.file));
            if (motion != null) {
                expressions.put(entry.id, motion);
                expressionManagers.put(entry.id, new CubismExpressionMotionManager());
            }
            listener.onStatus("正在读取模型原生表情 " + (i + 1) + "/" + total + "…");
        }
        updateScheduler.addUpdatableList(new ACubismUpdater(300) {
            @Override
            public void onLateUpdate(
                    com.live2d.sdk.cubism.framework.model.CubismModel target,
                    float deltaTimeSeconds) {
                for (CubismExpressionMotionManager manager : expressionManagers.values()) {
                    manager.updateMotion(target, deltaTimeSeconds);
                }
            }
        });
    }

    private void loadMotions(WhaleRenderer.Listener listener) throws IOException {
        int total = catalog.motions.size();
        for (int i = 0; i < total; i++) {
            WhaleCatalog.Entry entry = catalog.motions.get(i);
            CubismMotion motion = loadMotion(NativeFileLoader.readFile(entry.file));
            if (motion != null) motions.put(entry.id, motion);
            listener.onStatus("正在读取模型原生动作 " + (i + 1) + "/" + total + "…");
        }
        idleMotionId = catalog.idleMotionId;
        updateScheduler.addUpdatableList(new ACubismUpdater(250) {
            @Override
            public void onLateUpdate(
                    com.live2d.sdk.cubism.framework.model.CubismModel target,
                    float deltaTimeSeconds) {
                motionManager.updateMotion(target, deltaTimeSeconds);
            }
        });
        updateScheduler.addUpdatableList(new ACubismUpdater(CubismUpdateOrder.LOOK.order) {
            @Override
            public void onLateUpdate(
                    com.live2d.sdk.cubism.framework.model.CubismModel target,
                    float deltaTimeSeconds) {
                idleController.update(target, deltaTimeSeconds, !motionManager.isFinished());
                if (motionManager.isFinished()) currentMotionId = "";
            }
        });
    }

    private void loadPhysicsAndPose(WhaleRenderer.Listener listener) throws IOException {
        String physicsName = setting.getPhysicsFileName();
        if (physicsName != null && !physicsName.isEmpty()) {
            listener.onStatus("正在读取模型原生物理…");
            loadPhysics(NativeFileLoader.readFile(child(physicsName)));
            if (physics != null) {
                updateScheduler.addUpdatableList(new CubismPhysicsUpdater(physics));
            }
        }
        String poseName = setting.getPoseFileName();
        if (poseName != null && !poseName.isEmpty()) {
            loadPose(NativeFileLoader.readFile(child(poseName)));
            if (pose != null) updateScheduler.addUpdatableList(new CubismPoseUpdater(pose));
        }
    }

    private void setupNativeRenderer(int width, int height) {
        CubismRendererAndroid renderer = (CubismRendererAndroid)
                CubismRendererAndroid.create(width, height);
        setupRenderer(renderer, 1);
        renderer.setDrawableClippingMaskBufferSize(512, 512);
        renderer.isUsingHighPrecisionMask(true);
    }

    private void setupTextures(NativeTextureManager textures,
                               WhaleRenderer.Listener listener) throws IOException {
        int count = setting.getTextureCount();
        for (int i = 0; i < count; i++) {
            String relative = setting.getTextureFileName(i);
            if (relative == null || relative.isEmpty()) continue;
            listener.onStatus("正在上传贴图 " + (i + 1) + "/" + count
                    + " · GL_LINEAR 无 mipmap…");
            NativeTextureManager.TextureInfo texture = textures.loadPng(child(relative));
            CubismRendererAndroid renderer = getRenderer();
            renderer.bindTexture(i, texture.id);
            renderer.isPremultipliedAlpha(true);
        }
    }

    private File child(String relative) throws IOException {
        File file = new File(homeDirectory, relative);
        String safeRoot = homeDirectory.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(safeRoot) || !file.isFile()) {
            throw new IOException("模型引用文件不存在或路径不安全：" + relative);
        }
        return file;
    }
}

package com.catkiss.whalelive2ddeskpet;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.CubismFrameworkConfig;
import com.live2d.sdk.cubism.framework.math.CubismMatrix44;
import com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class WhaleRenderer implements GLSurfaceView.Renderer {
    interface Listener {
        void onStatus(String status);
        void onReady(String detail);
        void onError(Throwable error);
    }

    private static final String TAG = "WhaleNativeCubism";
    private final Context context;
    private final Listener listener;
    private final NativeTextureManager textures = new NativeTextureManager();
    private final CubismMatrix44 projection = CubismMatrix44.create();
    private WhaleLive2DModel model;
    private WhaleCatalog pendingCatalog;
    private int surfaceWidth;
    private int surfaceHeight;
    private int maxTextureSize;
    private boolean frameworkReady;
    private boolean contextRecreated;
    private boolean released;
    private long lastFrameNanos;
    private volatile float stageScale = 1.0f;
    private volatile float stageTranslateX;
    private volatile float stageTranslateY;

    WhaleRenderer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void requestModel(WhaleCatalog catalog) {
        if (!released) pendingCatalog = catalog;
    }

    void setStageTransform(float scale, float translateX, float translateY) {
        stageScale = Math.max(0.35f, Math.min(5.0f, scale));
        stageTranslateX = Math.max(-2.0f, Math.min(2.0f, translateX));
        stageTranslateY = Math.max(-2.0f, Math.min(2.0f, translateY));
    }

    void toggleExpression(String id) {
        if (model != null) model.toggleExpression(id);
    }

    void resetExpressions() {
        if (model != null) model.resetExpressions();
    }

    void playMotion(String id) {
        if (model != null) model.playMotion(id);
    }

    void applyReaction(String id) {
        if (model != null) model.applyReaction(id);
    }

    void resetAll() {
        if (model != null) model.resetAll();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        if (released) return;
        try {
            initializeFramework();
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            int[] value = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0);
            maxTextureSize = value[0];
            textures.forgetAfterContextLoss();
            CubismShaderAndroid.getInstance().releaseInvalidShaderProgram();
            CubismShaderAndroid.deleteInstance();
            contextRecreated = model != null;
            listener.onStatus("原生 OpenGL 已启动 · 最大贴图 " + maxTextureSize + "px");
        } catch (Throwable error) {
            listener.onError(error);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        if (released) return;
        surfaceWidth = width;
        surfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
        if (contextRecreated && model != null) {
            try {
                model.reloadRenderer(width, height, textures, listener);
                contextRecreated = false;
                listener.onReady(readyDetail());
            } catch (Throwable error) {
                listener.onError(error);
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearDepthf(1.0f);
        if (released) return;
        if (pendingCatalog != null && surfaceWidth > 0 && surfaceHeight > 0) {
            WhaleCatalog catalog = pendingCatalog;
            pendingCatalog = null;
            loadRequestedModel(catalog);
        }
        if (model == null) return;
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 1.0f / 60.0f
                : Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f);
        lastFrameNanos = now;
        try {
            model.update(delta);
            projection.loadIdentity();
            float aspect = (float) surfaceWidth / (float) surfaceHeight;
            float displayRatio = (float) surfaceHeight / (float) surfaceWidth;
            float canvasRatio = model.getCanvasHeight() / model.getCanvasWidth();
            if (canvasRatio < displayRatio) {
                model.fitWidth(2.0f);
                projection.scale(1.0f, aspect);
            } else {
                model.fitHeight(2.0f);
                projection.scale(1.0f / aspect, 1.0f);
            }
            projection.scaleRelative(stageScale, stageScale);
            projection.translateRelative(stageTranslateX, stageTranslateY);
            model.draw(projection);
        } catch (Throwable error) {
            listener.onError(error);
            releaseCurrentModel();
        }
    }

    void release() {
        if (released) return;
        released = true;
        pendingCatalog = null;
        releaseCurrentModel();
        if (frameworkReady && CubismFramework.isInitialized()) CubismFramework.dispose();
        CubismFramework.cleanUp();
        frameworkReady = false;
    }

    private void initializeFramework() {
        if (frameworkReady && CubismFramework.isInitialized()) return;
        CubismFramework.Option option = new CubismFramework.Option();
        option.logFunction = message -> Log.d(TAG, message);
        option.loggingLevel = CubismFrameworkConfig.LogLevel.INFO;
        option.loadFileFunction = new NativeFileLoader(context);
        CubismFramework.cleanUp();
        if (!CubismFramework.startUp(option)) {
            throw new IllegalStateException("Cubism Framework 启动失败");
        }
        CubismFramework.initialize();
        if (!CubismFramework.isInitialized()) {
            throw new IllegalStateException("Cubism Framework 初始化失败");
        }
        frameworkReady = true;
    }

    private void loadRequestedModel(WhaleCatalog catalog) {
        try {
            releaseCurrentModel();
            lastFrameNanos = 0L;
            WhaleLive2DModel next = new WhaleLive2DModel();
            model = next;
            next.load(catalog, surfaceWidth, surfaceHeight, textures, listener);
            listener.onReady(readyDetail());
        } catch (Throwable error) {
            releaseCurrentModel();
            listener.onError(error);
        }
    }

    private String readyDetail() {
        return "Q版鲸鱼 Cubism 5 已就绪 · GL_LINEAR · GL_MAX_TEXTURE_SIZE="
                + maxTextureSize + (model == null ? "" : " · " + model.readyDetail());
    }

    private void releaseCurrentModel() {
        textures.releaseAll();
        if (model != null) {
            model.closeModel();
            model = null;
        }
    }
}

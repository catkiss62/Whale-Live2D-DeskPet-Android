package com.catkiss.whalelive2ddeskpet;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public final class WhaleView extends GLSurfaceView {
    public interface Listener {
        void onStatus(String status);
        void onReady(String detail);
        void onError(Throwable error);
    }

    private final WhaleRenderer renderer;
    private volatile boolean released;
    private Listener listener = new Listener() {
        @Override public void onStatus(String status) { }
        @Override public void onReady(String detail) { }
        @Override public void onError(Throwable error) { }
    };

    public WhaleView(Context context) {
        this(context, null);
    }

    public WhaleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setPreserveEGLContextOnPause(true);
        renderer = new WhaleRenderer(context, new WhaleRenderer.Listener() {
            @Override public void onStatus(String status) {
                listener.onStatus(status);
            }
            @Override public void onReady(String detail) {
                listener.onReady(detail);
            }
            @Override public void onError(Throwable error) {
                listener.onError(error);
            }
        });
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public void setListener(Listener requested) {
        listener = requested == null ? listener : requested;
    }

    public void loadModel(WhaleCatalog catalog) {
        queueRenderer(() -> renderer.requestModel(catalog));
    }

    public void toggleExpression(String id) {
        queueRenderer(() -> renderer.toggleExpression(id));
    }

    public void resetExpressions() {
        queueRenderer(renderer::resetExpressions);
    }

    public void playMotion(String id) {
        queueRenderer(() -> renderer.playMotion(id));
    }

    public void applyReaction(String id) {
        queueRenderer(() -> renderer.applyReaction(id));
    }

    public void setIdleMode(WhaleIdleController.Mode mode) {
        queueRenderer(() -> renderer.setIdleMode(mode));
    }

    public void resetAll() {
        queueRenderer(renderer::resetAll);
    }

    public void setStageTransform(float scale, float x, float y) {
        if (!released) renderer.setStageTransform(scale, x, y);
    }

    public void onHostResume() {
        if (!released) onResume();
    }

    public void onHostPause() {
        if (!released) onPause();
    }

    public void release() {
        if (released) return;
        released = true;
        queueEvent(renderer::release);
    }

    private void queueRenderer(Runnable command) {
        if (!released) queueEvent(command);
    }
}

package com.catkiss.whalelive2ddeskpet;

import com.live2d.sdk.cubism.framework.CubismFramework;
import com.live2d.sdk.cubism.framework.model.CubismModel;

import java.util.Random;

/**
 * Small, removable autonomous performance layer for the Q-whale model. It only uses parameters
 * present in the author's VTube Studio mapping and yields completely while an authored motion is
 * playing. Purchased expressions and motions remain untouched.
 */
final class WhaleIdleController {
    enum Mode {
        OFF("关闭"), NATURAL("自然"), LIVELY("活泼");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final Random random = new Random(0x5748414c45L);
    private Mode mode = Mode.NATURAL;
    private float elapsed;
    private float targetTimer;
    private float headX;
    private float headY;
    private float headZ;
    private float targetHeadX;
    private float targetHeadY;
    private float targetHeadZ;
    private float gazeX;
    private float gazeY;
    private float targetGazeX;
    private float targetGazeY;
    private float blinkTimer = 2.2f;
    private float blinkPhase = -1.0f;

    void setMode(Mode requested) {
        mode = requested == null ? Mode.NATURAL : requested;
        if (mode != Mode.OFF) targetTimer = 0.0f;
    }

    Mode getMode() {
        return mode;
    }

    void reset() {
        headX = headY = headZ = 0.0f;
        gazeX = gazeY = 0.0f;
        targetTimer = 0.0f;
        blinkTimer = 1.4f;
        blinkPhase = -1.0f;
    }

    void update(CubismModel model, float deltaSeconds, boolean authoredMotionPlaying) {
        if (mode == Mode.OFF || authoredMotionPlaying) return;
        float delta = Math.max(0.0f, Math.min(0.05f, deltaSeconds));
        elapsed += delta;
        targetTimer -= delta;
        if (targetTimer <= 0.0f) chooseTarget();

        float followSpeed = mode == Mode.LIVELY ? 2.0f : 1.25f;
        float blend = 1.0f - (float) Math.exp(-followSpeed * delta);
        headX = mix(headX, targetHeadX, blend);
        headY = mix(headY, targetHeadY, blend);
        headZ = mix(headZ, targetHeadZ, blend);
        gazeX = mix(gazeX, targetGazeX, Math.min(1.0f, blend * 1.6f));
        gazeY = mix(gazeY, targetGazeY, Math.min(1.0f, blend * 1.6f));

        add(model, "ParamAngleX", headX);
        add(model, "ParamAngleY", headY);
        add(model, "ParamAngleZ", headZ);
        add(model, "ParamBodyAngleX", headX * 0.28f);
        add(model, "ParamBodyAngleY", headY * 0.25f);
        add(model, "ParamBodyAngleZ", headZ * 0.32f);
        add(model, "ParamEyeBallX", gazeX);
        add(model, "ParamEyeBallY", gazeY);

        float breathSpeed = mode == Mode.LIVELY ? 1.75f : 1.35f;
        float breath = 0.5f + 0.42f * (float) Math.sin(elapsed * breathSpeed);
        set(model, "ParamBreath", breath);

        float eyeOpen = updateBlink(delta);
        multiply(model, "ParamEyeLOpen", eyeOpen);
        multiply(model, "ParamEyeROpen", eyeOpen);
    }

    private void chooseTarget() {
        float amplitude = mode == Mode.LIVELY ? 1.0f : 0.58f;
        targetHeadX = signed() * 10.0f * amplitude;
        targetHeadY = signed() * 6.5f * amplitude;
        targetHeadZ = signed() * 4.5f * amplitude;
        targetGazeX = clamp(targetHeadX / 16.0f + signed() * 0.12f, -0.68f, 0.68f);
        targetGazeY = clamp(targetHeadY / 18.0f + signed() * 0.08f, -0.28f, 0.42f);
        float minimum = mode == Mode.LIVELY ? 1.5f : 2.8f;
        float spread = mode == Mode.LIVELY ? 2.2f : 2.8f;
        targetTimer = minimum + random.nextFloat() * spread;
    }

    private float updateBlink(float delta) {
        if (blinkPhase < 0.0f) {
            blinkTimer -= delta;
            if (blinkTimer > 0.0f) return 1.0f;
            blinkPhase = 0.0f;
        }
        blinkPhase += delta;
        final float close = 0.085f;
        final float hold = 0.035f;
        final float open = 0.12f;
        if (blinkPhase < close) return 1.0f - blinkPhase / close;
        if (blinkPhase < close + hold) return 0.0f;
        if (blinkPhase < close + hold + open) {
            return (blinkPhase - close - hold) / open;
        }
        blinkPhase = -1.0f;
        blinkTimer = (mode == Mode.LIVELY ? 2.0f : 2.8f) + random.nextFloat() * 3.2f;
        return 1.0f;
    }

    private float signed() {
        return random.nextFloat() * 2.0f - 1.0f;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void add(CubismModel model, String id, float value) {
        model.addParameterValue(CubismFramework.getIdManager().getId(id), value);
    }

    private static void set(CubismModel model, String id, float value) {
        model.setParameterValue(CubismFramework.getIdManager().getId(id), value);
    }

    private static void multiply(CubismModel model, String id, float value) {
        model.multiplyParameterValue(CubismFramework.getIdManager().getId(id), value);
    }
}

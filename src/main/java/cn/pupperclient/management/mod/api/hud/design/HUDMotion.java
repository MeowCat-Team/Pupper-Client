package cn.pupperclient.management.mod.api.hud.design;

/** Time-based, interruptible motion shared by HUD components. */
public final class HUDMotion {
    private long lastFrame;
    public float deltaSeconds() {
        long now = System.nanoTime();
        float delta = lastFrame == 0 ? 1f / 60 : (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        return Math.min(0.1f, Math.max(0, delta));
    }

    public static float approach(float value, float target, float seconds, boolean reduced) {
        if (reduced || Math.abs(target - value) < 0.01f) return target;
        return target + (value - target) * (float) Math.exp(-18 * seconds);
    }

    /** Critically damped spatial spring; keeps velocity when its target changes. */
    public static final class Spring {
        private float value;
        private float velocity;
        public Spring(float initial) { value = initial; }
        public float update(float target, float seconds, boolean reduced) {
            if (reduced) {
                value = target;
                velocity = 0;
                return value;
            }
            double decay = Math.exp(-20 * seconds);
            float offset = value - target;
            float impulse = velocity + 20 * offset;
            value = target + (float) ((offset + impulse * seconds) * decay);
            velocity = (float) ((velocity - 20 * impulse * seconds) * decay);
            if (Math.abs(value - target) < 0.01f && Math.abs(velocity) < 0.01f) {
                value = target;
                velocity = 0;
            }
            return value;
        }
    }
}
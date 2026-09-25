package cn.pupperclient.animation;

public class SimpleAnimation {

	private float currentValue;
	private boolean firstTick;
    private float targetValue;
    private long lastUpdateNanos;

	public SimpleAnimation() {
		this.firstTick = true;
        this.currentValue = 0;
        this.targetValue = 0;
		resetClock();
	}

    public void setValue(float value) {
        this.currentValue = value;
        this.targetValue = value;
		resetClock();
    }
    public void setTarget(float target) {
        this.targetValue = target;
		resetClock();
    }

    public void update(float speed) {
		approach(targetValue, speed);
    }

	public void onTick(float value, float speed) {
		if (firstTick) {
			currentValue = value;
			firstTick = false;
			resetClock();
		} else {
			approach(value, speed);
		}
	}

	private void approach(float value, float speed) {
		long now = System.nanoTime();
		float deltaSeconds = (now - lastUpdateNanos) / 1_000_000_000F;
		lastUpdateNanos = now;
		if (deltaSeconds <= 0) {
			return;
		}

		float factor = Math.min(1, Math.max(0, speed) * deltaSeconds);
		currentValue += (value - currentValue) * factor;
		if (Math.abs(value - currentValue) < 0.01F) {
			currentValue = value;
		}
	}

	private void resetClock() {
		lastUpdateNanos = System.nanoTime();
	}

	public float getValue() {
		return currentValue;
	}

	public void setFirstTick(boolean firstTick) {
		this.firstTick = firstTick;
		resetClock();
	}
}

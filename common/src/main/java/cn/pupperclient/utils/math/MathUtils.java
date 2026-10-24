/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.utils.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

	public static float roundToPlace(double value, int places) {
		if (places < 0) {
			throw new IllegalArgumentException();
		}

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.floatValue();
	}

	public static int clamp(int number, int min, int max) {
		return number < min ? min : Math.min(number, max);
	}

	public static float clamp(float number, float min, float max) {
		return number < min ? min : Math.min(number, max);
	}

	public static double clamp(double number, double min, double max) {
		return number < min ? min : Math.min(number, max);
	}

    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float interpolateFloat(float oldValue, float newValue, double interpolationValue){
        return interpolate(oldValue, newValue, interpolationValue).floatValue();
    }

    public static int interpolateInt(int oldValue, int newValue, double interpolationValue){
        return interpolate(oldValue, newValue, interpolationValue).intValue();
    }

    public static double getRandomDouble(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static int getRandomInt(int min, int max) {
        return min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static long getRandomLong(long min, long max) {
        return min >= max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}

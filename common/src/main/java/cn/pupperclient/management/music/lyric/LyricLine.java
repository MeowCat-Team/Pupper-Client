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

package cn.pupperclient.management.music.lyric;

public class LyricLine {
    private final long millis;
    private final String text;
    private final String translation;

    public LyricLine(float time, String text) {
        this(Math.round(time * 1000d), text, "");
    }

    public LyricLine(long millis, String text, String translation) {
        this.millis = millis;
        this.text = text;
        this.translation = translation;
    }

    public float getTime() {
        return millis / 1000f;
    }

    public long getMillis() { return millis; }
    public String getTranslation() { return translation; }

    public String getText() {
        return text;
    }
}

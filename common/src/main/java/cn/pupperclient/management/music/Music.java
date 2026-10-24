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

package cn.pupperclient.management.music;

import java.awt.Color;
import java.io.File;

public class Music {

	private final File audio;
	private final String title, artist;
	private final File album;
	private final Color color;
	private final MusicTrack track;

	public Music(File audio, String title, String artist, File album, Color color) {
		this(audio, title, artist, album, color, null);
	}

	public Music(File audio, String title, String artist, File album, Color color, MusicTrack track) {
		this.audio = audio;
		this.title = title;
		this.artist = artist;
		this.album = album;
		this.color = color;
		this.track = track;
	}

	public MusicTrack getTrack() {
		return track == null ? new MusicTrack(0, title, artist, "", "", 0) : track;
	}

	public File getAudio() {
		return audio;
	}

	public String getTitle() {
		return title;
	}

	public String getArtist() {
		return artist;
	}

	public File getAlbum() {
		return album;
	}

	public Color getColor() {
		return color;
	}
}

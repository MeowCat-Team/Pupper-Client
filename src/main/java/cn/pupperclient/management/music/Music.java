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

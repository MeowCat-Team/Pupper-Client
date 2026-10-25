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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import cn.pupperclient.PupperLogger;
import cn.pupperclient.libraries.flac.FLACDecoder;
import cn.pupperclient.libraries.flac.metadata.Metadata;
import cn.pupperclient.libraries.flac.metadata.Picture;
import cn.pupperclient.libraries.flac.metadata.VorbisComment;
import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.file.FileUtils;
import cn.pupperclient.utils.render.ImageUtils;

/**
 * Turns the audio files in the music directory into library entries.
 *
 * <p>Reading tags, decoding an embedded cover and hashing the file are the expensive
 * part of a library scan, so this is separate from the manager that owns playback.
 */
final class MusicLibraryScanner {

    private MusicLibraryScanner() {}

    /** Reads every supported file, sorted by name, skipping the ones that fail. */
    static List<Music> scan(MusicLibraryStore library) {
        File directory = FileLocation.MUSIC_DIR;
        File[] files = directory.listFiles();
        if (files == null) {
            return List.of();
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        List<Music> loaded = new ArrayList<>(files.length);
        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            try {
                if (name.endsWith(".flac")) {
                    loaded.add(flac(file, library));
                } else if (name.endsWith(".mp3")) {
                    loaded.add(mp3(file, library));
                }
            } catch (Exception invalid) {
                PupperLogger.error("MusicLibraryScanner", "Could not load " + file.getName(), invalid);
            }
        }
        return List.copyOf(loaded);
    }

    private static Music flac(File file, MusicLibraryStore library) throws Exception {
        Metadata[] metadata;
        try (FileInputStream input = new FileInputStream(file)) {
            metadata = new FLACDecoder(input).readMetadata();
        }

        String title = null;
        String artist = null;
        byte[] cover = null;
        for (Metadata meta : metadata) {
            if (meta instanceof VorbisComment comment) {
                String[] titles = comment.getCommentByName("TITLE");
                if (titles != null && titles.length > 0) {
                    title = titles[0];
                } else {
                    String name = file.getName();
                    title = name.substring(0, name.lastIndexOf("."));
                }
                String[] artists = comment.getCommentByName("ARTIST");
                if (artists != null && artists.length > 0) {
                    artist = artists[0];
                }
            } else if (meta instanceof Picture picture) {
                cover = picture.getImage();
            }
        }
        return create(file, title, artist, cover, library);
    }

    private static Music mp3(File file, MusicLibraryStore library) throws Exception {
        String title = null;
        String artist = null;
        byte[] cover = null;

        try {
            Mp3File mp3 = new Mp3File(file);
            if (mp3.hasId3v2Tag()) {
                ID3v2 tag = mp3.getId3v2Tag();
                title = tag.getTitle();
                artist = tag.getArtist();
                cover = tag.getAlbumImage();
            } else if (mp3.hasId3v1Tag()) {
                ID3v1 tag = mp3.getId3v1Tag();
                title = tag.getTitle();
                artist = tag.getArtist();
            }
        } catch (Exception unreadable) {
            PupperLogger.error("MusicLibraryScanner", "Failed to load MP3 tags", unreadable);
        }
        return create(file, title, artist, cover, library);
    }

    /**
     * Builds the entry, preferring the provider's metadata over the file's own tags.
     *
     * <p>The cover is written to the cache under the file's hash so the artwork is
     * decoded once rather than on every scan.
     */
    private static Music create(File file, String title, String artist, byte[] cover,
                                MusicLibraryStore library) throws Exception {
        MusicTrack known = library.metadata(file.getName());
        if (known != null) {
            title = known.title();
            artist = known.artist();
        }

        String hash = cover == null ? "unused" : FileUtils.getMd5Checksum(file);
        File artwork = new File(FileLocation.CACHE_DIR, hash);
        Color color = Color.BLACK;

        if (cover != null && !artwork.exists()) {
            try (FileOutputStream output = new FileOutputStream(artwork)) {
                output.write(cover);
            }
        }
        if (cover != null && artwork.exists()) {
            color = ImageUtils.calculateAverageColor(ImageIO.read(artwork));
        }

        File providerCover = known == null ? null : new File(FileLocation.CACHE_DIR, known.coverFilename());
        if (providerCover != null && providerCover.isFile()) {
            artwork = providerCover;
        }

        String fallback = file.getName().substring(0, file.getName().lastIndexOf('.'));
        return new Music(file, title == null || title.isBlank() ? fallback : title,
            artist == null ? "" : artist, artwork.exists() ? artwork : null, color, known);
    }
}

package cn.pupperclient.management.music;

import java.net.URI;
import java.util.List;

/** Providers own identifiers and access rules; UI and storage use provider-qualified track keys. */
public interface MusicProvider {
    record SearchResult(List<MusicTrack> tracks, int total, int offset, int nextOffset) {
        public SearchResult(List<MusicTrack> tracks, int total, int offset) { this(tracks, total, offset, offset + tracks.size()); }
    }
    record CatalogResult(MusicSearchType type, List<MusicTrack> tracks, List<MusicCollection> collections,
            int total, int offset, int nextOffset) {
        public CatalogResult { tracks = List.copyOf(tracks); collections = List.copyOf(collections); }
    }
    record AudioSource(URI uri, String extension, int fee, long previewMillis) {
        public AudioSource(URI uri, String extension) { this(uri, extension, -1, 0); }
    }
    record Lyrics(String original, String translated) { }
    String id();
    default String nameKey() { return "music.provider." + id(); }
    List<String> qualities();
    default String defaultQuality() { return qualities().getFirst(); }
    SearchResult search(String keyword, int limit, int offset) throws MusicError;
    default CatalogResult search(String keyword, MusicSearchType type, int limit, int offset) throws MusicError {
        if (type != MusicSearchType.SONGS) throw new MusicError("music.error.unsupported");
        SearchResult songs = search(keyword, limit, offset);
        return new CatalogResult(type, songs.tracks(), List.of(), songs.total(), songs.offset(), songs.nextOffset());
    }
    default SearchResult collectionTracks(MusicCollection collection, int limit, int offset, String cookie) throws MusicError {
        throw new MusicError("music.error.unsupported");
    }
    default SearchResult collectionTracks(MusicCollection collection, int limit, int offset, String cookie,
            MusicPreparation.Cancellation cancellation) throws MusicError {
        try {
            cancellation.check();
            SearchResult result = collectionTracks(collection, limit, offset, cookie);
            cancellation.check(); return result;
        } catch (java.io.InterruptedIOException cancelled) { throw new MusicError("music.error.network"); }
    }
    MusicTrack track(String id) throws MusicError;
    default MusicTrack track(String id, MusicPreparation.Cancellation cancellation) throws MusicError {
        try {
            cancellation.check(); MusicTrack track = track(id); cancellation.check(); return track;
        } catch (java.io.InterruptedIOException cancelled) { throw new MusicError("music.error.network"); }
    }
    AudioSource audio(MusicTrack track, String quality, String cookie, boolean download) throws MusicError;
    /** Older adapters remain compatible; network adapters should override to release their in-flight metadata I/O. */
    default AudioSource audio(MusicTrack track, String quality, String cookie, boolean download,
            MusicPreparation.Cancellation cancellation) throws MusicError {
        try {
            cancellation.check();
            AudioSource source = audio(track, quality, cookie, download);
            cancellation.check(); return source;
        } catch (java.io.InterruptedIOException cancelled) { throw new MusicError("music.error.network"); }
    }
    default Lyrics lyrics(MusicTrack track) throws MusicError { return new Lyrics("", ""); }
    default boolean cloudLikes() { return false; }
    default List<MusicTrack> likes(String userId, String cookie) throws MusicError { throw new MusicError("music.error.unsupported"); }
    default void like(MusicTrack track, boolean liked, String cookie) throws MusicError { throw new MusicError("music.error.unsupported"); }
}

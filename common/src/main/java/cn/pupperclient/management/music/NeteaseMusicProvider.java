package cn.pupperclient.management.music;

import java.util.List;

/** Account credentials are used only by the NetEase adapter. */
public final class NeteaseMusicProvider implements MusicProvider {
    public static final List<String> QUALITIES = List.of("standard", "higher", "exhigh", "lossless", "hires",
        "jyeffect", "sky", "dolby", "jymaster");
    private final NeteaseMusicApi api;
    public NeteaseMusicProvider(NeteaseMusicApi api) { this.api = api; }
    @Override public String id() { return "netease"; }
    @Override public CatalogResult search(String keyword, MusicSearchType type, int limit, int offset) throws MusicError {
        return api.search(keyword, type, limit, offset);
    }
    @Override public SearchResult collectionTracks(MusicCollection collection, int limit, int offset, String cookie) throws MusicError {
        return api.collectionTracks(collection, limit, offset, cookie);
    }
    @Override public SearchResult collectionTracks(MusicCollection collection, int limit, int offset, String cookie,
            MusicPreparation.Cancellation cancellation) throws MusicError {
        return api.collectionTracks(collection, limit, offset, cookie, cancellation);
    }
    @Override public List<String> qualities() { return QUALITIES; }
    @Override public String defaultQuality() { return "exhigh"; }
    @Override public SearchResult search(String keyword, int limit, int offset) throws MusicError {
        var result = api.search(keyword, limit, offset);
        return new SearchResult(result.tracks(), result.total(), result.offset());
    }
    @Override public MusicTrack track(String id) throws MusicError {
        return track(id, new MusicPreparation.Cancellation());
    }
    @Override public MusicTrack track(String id, MusicPreparation.Cancellation cancellation) throws MusicError {
        try {
            long numeric = Long.parseLong(id);
            if (numeric <= 0) throw new NumberFormatException();
            return api.details(List.of(numeric), cancellation).stream().findFirst().orElseThrow(() -> new MusicError("music.error.metadata"));
        } catch (NumberFormatException invalid) { throw new MusicError("music.error.metadata"); }
    }
    @Override public AudioSource audio(MusicTrack track, String quality, String cookie, boolean download) throws MusicError {
        return audio(track, quality, cookie, download, new MusicPreparation.Cancellation());
    }
    @Override public AudioSource audio(MusicTrack track, String quality, String cookie, boolean download,
            MusicPreparation.Cancellation cancellation) throws MusicError {
        var source = api.audio(track.id(), qualities().contains(quality) ? quality : defaultQuality(), cookie, cancellation);
        return new AudioSource(source.uri(), source.extension(), source.fee(), source.previewMillis());
    }
    @Override public Lyrics lyrics(MusicTrack track) throws MusicError {
        var result = api.lyrics(track.id());
        return new Lyrics(result.original(), result.translated());
    }
    @Override public boolean cloudLikes() { return true; }
    @Override public List<MusicTrack> likes(String userId, String cookie) throws MusicError {
        long revision = api.configuration().snapshot().revision();
        return api.details(api.likes(userId, cookie), revision);
    }
    @Override public void like(MusicTrack track, boolean liked, String cookie) throws MusicError { api.like(track.id(), liked, cookie); }
}

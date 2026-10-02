package cn.pupperclient.management.music;

import java.util.List;

/** Account credentials are used only by the NetEase adapter. */
public final class NeteaseMusicProvider implements MusicProvider {
    public static final List<String> QUALITIES = List.of("standard", "higher", "exhigh", "lossless", "hires",
        "jyeffect", "sky", "dolby", "jymaster");
    private final NeteaseMusicApi api;
    public NeteaseMusicProvider(NeteaseMusicApi api) { this.api = api; }
    @Override public String id() { return "netease"; }
    @Override public List<String> qualities() { return QUALITIES; }
    @Override public String defaultQuality() { return "exhigh"; }
    @Override public SearchResult search(String keyword, int limit, int offset) throws MusicError {
        var result = api.search(keyword, limit, offset);
        return new SearchResult(result.tracks(), result.total(), result.offset());
    }
    @Override public MusicTrack track(String id) throws MusicError {
        try {
            long numeric = Long.parseLong(id);
            if (numeric <= 0) throw new NumberFormatException();
            return api.details(List.of(numeric)).stream().findFirst().orElseThrow(() -> new MusicError("music.error.metadata"));
        } catch (NumberFormatException invalid) { throw new MusicError("music.error.metadata"); }
    }
    @Override public AudioSource audio(MusicTrack track, String quality, String cookie, boolean download) throws MusicError {
        var source = api.audio(track.id(), qualities().contains(quality) ? quality : defaultQuality(), cookie);
        return new AudioSource(source.uri(), source.extension());
    }
    @Override public Lyrics lyrics(MusicTrack track) throws MusicError {
        var result = api.lyrics(track.id());
        return new Lyrics(result.original(), result.translated());
    }
    @Override public boolean cloudLikes() { return true; }
    @Override public List<MusicTrack> likes(String userId, String cookie) throws MusicError { return api.details(api.likes(userId, cookie)); }
    @Override public void like(MusicTrack track, boolean liked, String cookie) throws MusicError { api.like(track.id(), liked, cookie); }
}

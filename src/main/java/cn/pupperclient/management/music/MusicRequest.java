package cn.pupperclient.management.music;

/** Provider and quality resolution for requests from commands or other music clients. */
public final class MusicRequest {
    public enum Action { PLAY, DOWNLOAD }
    public record Target(MusicProvider provider, String trackId, String quality) { }
    public record Result(Action action, Music music) { }
    private MusicRequest() { }

    public static Target target(MusicProviders providers, String reference, String requestedQuality) throws MusicError {
        if (reference == null || !reference.matches("(?:[a-zA-Z]+:)?[a-zA-Z0-9_-]{1,80}"))
            throw new MusicError("music.error.metadata");
        MusicProvider provider = providers.forReference(reference);
        String quality = requestedQuality == null ? provider.defaultQuality() : null;
        if (requestedQuality != null) for (String choice : provider.qualities()) {
            if (choice.equalsIgnoreCase(requestedQuality) || MusicText.get("music.quality." + choice).equals(requestedQuality)) {
                quality = choice; break;
            }
        }
        if (quality == null) throw new MusicError("music.error.quality");
        int colon = reference.indexOf(':');
        return new Target(provider, colon < 0 ? reference : reference.substring(colon + 1), quality);
    }
    /** Keep the existing .music quick policy in the music layer, alongside provider routing. */
    public static Action quickAction(MusicTrack track) {
        return track.provider().equals("netease") ? Action.DOWNLOAD : Action.PLAY;
    }
}

package cn.pupperclient.management.music;

import java.util.ArrayList;
import java.util.List;

/** Main-thread search/browse state. Request tokens reject replies after a type, source or page change. */
public final class MusicSearchState {
    public record Request(int generation, String provider, MusicSearchType type, String query, MusicCollection collection, int offset) { }
    private String provider, query = "";
    private MusicSearchType type = MusicSearchType.SONGS;
    private MusicProvider.CatalogResult catalogue = empty(type);
    private MusicProvider.SearchResult detail = new MusicProvider.SearchResult(List.of(), 0, 0);
    private MusicCollection collection;
    private Request pending;
    private int generation;
    private boolean searched;
    public MusicSearchState(String provider) { this.provider = provider; }
    public String query() { return query; }
    public MusicSearchType type() { return type; }
    public MusicCollection collection() { return collection; }
    public boolean loading() { return pending != null; }
    public boolean searched() { return searched; }
    public List<MusicTrack> tracks() { return collection == null ? catalogue.tracks() : detail.tracks(); }
    public List<MusicCollection> collections() { return catalogue.collections(); }
    public int total() { return collection == null ? catalogue.total() : detail.total(); }
    public int nextOffset() { return collection == null ? catalogue.nextOffset() : detail.nextOffset(); }
    public boolean hasMore() { return (!tracks().isEmpty() || collection == null && !collections().isEmpty())
        && nextOffset() > 0 && (total() < 0 || nextOffset() < total()); }
    public boolean reset(String provider, MusicSearchType type, String query) {
        query = query.strip();
        if (this.provider.equals(provider) && this.type == type && this.query.equals(query)) return false;
        cancel(); this.provider = provider; this.type = type; this.query = query;
        collection = null; catalogue = empty(type); searched = false; return true;
    }
    public void open(MusicCollection collection) {
        if (!collection.provider().equals(provider)) throw new IllegalArgumentException("Collection from another source");
        cancel(); this.collection = collection; detail = new MusicProvider.SearchResult(List.of(), 0, 0);
    }
    public void back() { cancel(); collection = null; }
    public void cancel() {
        if (pending != null && pending.collection() == null && pending.offset() == 0) searched = false;
        generation++; pending = null;
    }
    public Request begin(boolean more) {
        if (more && (loading() || !hasMore()) || collection == null && query.isBlank()) return null;
        int offset = more ? nextOffset() : 0;
        cancel();
        if (!more) {
            if (collection == null) catalogue = empty(type);
            else detail = new MusicProvider.SearchResult(List.of(), 0, 0);
        }
        if (collection == null) searched = true;
        return pending = new Request(++generation, provider, type, query, collection, offset);
    }
    public boolean accept(Request request, MusicProvider.CatalogResult result) {
        if (!current(request) || request.collection() != null || result.type() != request.type() || result.offset() != request.offset()) return false;
        var tracks = new ArrayList<>(catalogue.tracks()); tracks.addAll(result.tracks());
        var collections = new ArrayList<>(catalogue.collections()); collections.addAll(result.collections());
        int total = result.tracks().isEmpty() && result.collections().isEmpty() || result.nextOffset() <= request.offset()
            ? result.nextOffset() : result.total();
        catalogue = new MusicProvider.CatalogResult(type, tracks, collections, total, result.offset(), result.nextOffset());
        pending = null; return true;
    }
    public boolean accept(Request request, MusicProvider.SearchResult result) {
        if (!current(request) || request.collection() == null || result.offset() != request.offset()) return false;
        var tracks = new ArrayList<>(detail.tracks()); tracks.addAll(result.tracks());
        detail = new MusicProvider.SearchResult(List.copyOf(tracks), result.tracks().isEmpty() || result.nextOffset() <= request.offset()
            ? result.nextOffset() : result.total(), result.offset(), result.nextOffset());
        pending = null; return true;
    }
    public boolean fail(Request request) { if (!current(request)) return false; pending = null; return true; }
    private boolean current(Request request) { return request != null && request.equals(pending) && request.generation() == generation; }
    private static MusicProvider.CatalogResult empty(MusicSearchType type) {
        return new MusicProvider.CatalogResult(type, List.of(), List.of(), 0, 0, 0);
    }
}

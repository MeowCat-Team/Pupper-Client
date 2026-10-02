package cn.pupperclient.management.music;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Selection affects new searches; every existing track continues to route to its own provider. */
public final class MusicProviders {
    private final Map<String, MusicProvider> providers = new LinkedHashMap<>();
    private final MusicLibraryStore library;
    private volatile MusicProvider selected;
    public MusicProviders(MusicLibraryStore library, MusicProvider... entries) {
        this.library = library;
        for (MusicProvider entry : entries) {
            if (providers.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Duplicate music provider");
        }
        if (providers.isEmpty()) throw new IllegalArgumentException("No music providers");
        selected = providers.getOrDefault(library.provider(), providers.values().iterator().next());
    }
    public List<MusicProvider> all() { return List.copyOf(providers.values()); }
    public MusicProvider selected() { return selected; }
    public MusicProvider forReference(String reference) throws MusicError {
        int separator = reference.indexOf(':');
        return separator < 0 ? selected : get(reference.substring(0, separator));
    }
    public MusicProvider get(String id) throws MusicError {
        MusicProvider provider = providers.get(id.toLowerCase(Locale.ROOT));
        if (provider == null) throw new MusicError("music.error.provider");
        return provider;
    }
    public void select(String id) throws MusicError, IOException {
        MusicProvider next = get(id);
        library.provider(next.id());
        selected = next;
    }
}

package org.example.sospot.ai;

import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class AliasesLoader {

    private static final String RESOURCE = "aliases.json";

    private final ObjectMapper objectMapper;
    private final Map<String, AliasCatalog.RegionEntry> regionIndex = new HashMap<>();
    private final Map<String, AliasCatalog.CategoryEntry> categoryIndex = new HashMap<>();
    private AliasCatalog catalog;

    public AliasesLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            catalog = objectMapper.readValue(in, AliasCatalog.class);
        }
        for (AliasCatalog.RegionEntry region : catalog.regions()) {
            for (String alias : region.aliases()) {
                regionIndex.put(normalize(alias), region);
            }
        }
        for (AliasCatalog.CategoryEntry category : catalog.categories()) {
            for (String alias : category.aliases()) {
                categoryIndex.put(normalize(alias) + "|" + category.catLevel(), category);
            }
        }
    }

    public Optional<AliasCatalog.RegionEntry> lookupRegion(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(regionIndex.get(normalize(text)));
    }

    public Optional<AliasCatalog.CategoryEntry> lookupCategory(String text, String catLevel) {
        if (text == null || catLevel == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(categoryIndex.get(normalize(text) + "|" + catLevel));
    }

    public AliasCatalog catalog() {
        return catalog;
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}

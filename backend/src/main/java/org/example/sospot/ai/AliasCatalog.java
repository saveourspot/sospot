package org.example.sospot.ai;

public record AliasCatalog(
    java.util.List<RegionEntry> regions,
    java.util.List<CategoryEntry> categories
) {
    public record RegionEntry(
        String dongCode,
        String sigungu,
        String canonical,
        java.util.List<String> aliases
    ) {}

    public record CategoryEntry(
        String catCode,
        String catLevel,
        String parentCode,
        String canonical,
        java.util.List<String> aliases
    ) {}
}

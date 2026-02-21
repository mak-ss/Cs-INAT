// ... diğer kodlar aynı kalacak ...

data class DizillaEpisode( // İsim değiştirildi
    @JsonProperty("season_no") val seasonNo: Int?,
    @JsonProperty("episode_no") val episodeNo: Int?,
    @JsonProperty("episode_text") val epText: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
)

data class Season(
    @JsonProperty("season_no") val seasonNo: Int?,
    @JsonProperty("season_text") val seText: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
    @JsonProperty("episodes") val episodes: List<DizillaEpisode>?, // Burası da güncellendi
)

// ... geri kalanlar aynı ...

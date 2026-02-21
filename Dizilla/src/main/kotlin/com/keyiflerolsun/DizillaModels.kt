package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchResult(
    @JsonProperty("response") val response: String?
)

data class SearchData(
    @JsonProperty("result") val result: List<SearchItem>? = arrayListOf()
)

data class SearchItem(
    @JsonProperty("used_slug") val slug: String? = null,
    @JsonProperty("object_name") val title: String? = null,
    @JsonProperty("object_poster_url") val poster: String? = null,
    @JsonProperty("used_type") val type: String? = null
)

data class Root(
    @JsonProperty("contentItem") val contentItem: ContentItem,
    @JsonProperty("RelatedResults") val relatedResults: RelatedResults
)

data class ContentItem(
    @JsonProperty("original_title") val originalTitle: String?,
    @JsonProperty("culture_title") val cultureTitle: String?,
    @JsonProperty("release_year") val releaseYear: Int?,
    @JsonProperty("total_minutes") val totalMinutes: Int?,
    @JsonProperty("poster_url") val posterUrl: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("categories") val categories: String?,
    @JsonProperty("used_slug") val usedSlug: String?,
    @JsonProperty("imdb_point") val imdbPoint: String?
)

data class RelatedResults(
    @JsonProperty("getSerieSeasonAndEpisodes") val getSerieSeasonAndEpisodes: Seasons? = null,
    @JsonProperty("getMovieCastsById") val getMovieCastsById: Casts? = null,
    @JsonProperty("getContentTrailers") val getContentTrailers: Trailers? = null,
    @JsonProperty("getEpisodeSources") val getEpisodeSources: EpisodeSources? = null,
    @JsonProperty("getMoviePartsById") val getMoviePartsById: MovieParts? = null
)

data class Seasons(@JsonProperty("result") val seasons: List<Season>?)
data class Season(
    @JsonProperty("season_no") val seasonNo: Int?,
    @JsonProperty("episodes") val episodes: List<Episode>?
)
data class Episode(
    @JsonProperty("episode_no") val episodeNo: Int?,
    @JsonProperty("episode_text") val epText: String?,
    @JsonProperty("used_slug") val usedSlug: String?
)
data class Casts(@JsonProperty("result") val result: List<CastResult>?)
data class CastResult(
    @JsonProperty("name") val name: String?,
    @JsonProperty("cast_image") val castImage: String?
)
data class Trailers(@JsonProperty("result") val result: List<TrailerResult>?)
data class TrailerResult(@JsonProperty("raw_url") val rawUrl: String?)
data class EpisodeSources(@JsonProperty("result") val result: List<SourceResult>?)
data class MovieParts(@JsonProperty("result") val result: List<MoviePartResult>?)
data class MoviePartResult(@JsonProperty("id") val id: Int?)
data class SourceResult(
    @JsonProperty("source_content") val sourceContent: String?,
    @JsonProperty("quality_name") val qualityName: String?
)
data class SourceItem(val sourceContent: String, val quality: String)
data class ListItems(@JsonProperty("result") val result: List<ContentItem>)

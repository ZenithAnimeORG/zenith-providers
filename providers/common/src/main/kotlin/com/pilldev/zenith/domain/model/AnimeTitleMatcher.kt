package com.pilldev.zenith.domain.model

import com.pilldev.zenith.provider.matcher.AnimeTitleMatcher as SdkAnimeTitleMatcher

/**
 * Intelligent fuzzy title matcher and similarity scorer.
 * Delegates to provider-sdk SdkAnimeTitleMatcher for cross-module consistency.
 */
public object AnimeTitleMatcher {
    public fun extractSeasonNumber(title: String): Int? = SdkAnimeTitleMatcher.extractSeasonNumber(title)

    public fun cleanForSearch(title: String): String = SdkAnimeTitleMatcher.cleanForSearch(title)

    public fun normalize(value: String): String = SdkAnimeTitleMatcher.normalize(value)

    public fun score(
        query: String,
        candidateTitle: String
    ): Int = SdkAnimeTitleMatcher.score(query, candidateTitle)

    public fun scoreWithSynonyms(
        queries: List<String>,
        candidateTitles: List<String>
    ): Int = SdkAnimeTitleMatcher.scoreWithSynonyms(queries, candidateTitles)

    public fun <T> sortBySimilarity(
        queries: List<String>,
        items: List<T>,
        titleSelector: (T) -> List<String>
    ): List<T> = SdkAnimeTitleMatcher.sortBySimilarity(queries, items, titleSelector)

    public fun <T> findBestMatch(
        queries: List<String>,
        items: List<T>,
        minScoreThreshold: Int = 50_000,
        titleSelector: (T) -> List<String>
    ): T? = SdkAnimeTitleMatcher.findBestMatch(queries, items, minScoreThreshold, titleSelector)

    public fun levenshteinDistance(
        s1: String,
        s2: String
    ): Int = SdkAnimeTitleMatcher.levenshteinDistance(s1, s2)
}

package com.lynq.analytics.similarity;

public record SimilarMatch<T>(T item, double score, int sharedSkills) {
}

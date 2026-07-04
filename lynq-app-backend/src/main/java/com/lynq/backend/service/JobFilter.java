package com.lynq.backend.service;

/**
 * Single free-text criterion for listing available jobs. The value is matched (case-insensitive,
 * contains) against every filterable column: title, description, company name, work type and skill.
 * Blank text is normalised to {@code null} so an empty query parameter behaves as "no filter".
 */
public record JobFilter(String filterValue) {

  public JobFilter {
    filterValue = blankToNull(filterValue);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
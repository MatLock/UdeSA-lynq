package com.lynq.backend.service;

public record JobFilter(String filterValue) {

  public JobFilter {
    filterValue = blankToNull(filterValue);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
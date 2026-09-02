package com.lynq.bff.service;

import java.util.List;
import java.util.Map;

final class ParsedResume {

  private static final int MAX_PROSE_CHARS = 2000;

  private static final String SEPARATOR = "\n";
  private static final List<String> PROSE_SECTIONS =
      List.of("work_experience", "education", "projects");

  private ParsedResume() {
  }

  static String fullName(Object resume) {
    Object fullName = asMap(asMap(resume).get("personal_info")).get("full_name");
    return fullName instanceof String name && !name.isBlank() ? name.trim() : null;
  }

  static String prose(Object resume) {
    Map<String, Object> root = asMap(resume);
    StringBuilder text = new StringBuilder();

    append(text, root.get("summary"));
    append(text, asMap(root.get("personal_info")).get("headline"));
    for (String section : PROSE_SECTIONS) {
      for (Object entry : asList(root.get(section))) {
        append(text, asMap(entry).get("description"));
      }
    }

    return text.toString().trim();
  }

  private static void append(StringBuilder text, Object value) {
    if (text.length() >= MAX_PROSE_CHARS || !(value instanceof String prose) || prose.isBlank()) {
      return;
    }
    text.append(prose.trim()).append(SEPARATOR);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value) {
    return value instanceof List<?> list ? (List<Object>) list : List.of();
  }
}

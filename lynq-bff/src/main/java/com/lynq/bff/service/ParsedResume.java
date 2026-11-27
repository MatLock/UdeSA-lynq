package com.lynq.bff.service;

import com.lynq.bff.client.response.SkillExtractionResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class ParsedResume {

  private static final int MAX_PROSE_CHARS = 2000;
  private static final String SKILLS = "skills";

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

  /**
   * The resume with lynq-llm's extracted skills merged into its three buckets.
   *
   * <p>Mirrors the wizard (ResumeEmploymentStep + cleanList): what the resume
   * already lists stays first and in order, extracted names are appended, and a
   * name already present in any letter case is not added again. The parsed
   * resume is never modified; a new map is returned. With nothing to add, or a
   * resume that is not a JSON object, the same instance comes back.
   */
  static Object withExtractedSkills(Object resume, SkillExtractionResponse extracted) {
    if (extracted == null || !(resume instanceof Map<?, ?>)) {
      return resume;
    }
    Map<String, List<String>> additions = new LinkedHashMap<>();
    additions.put("technical", extracted.getSkills());
    additions.put("tools", extracted.getTools());
    additions.put("soft", extracted.getSoft());
    if (additions.values().stream().allMatch(names -> names == null || names.isEmpty())) {
      return resume;
    }

    Map<String, Object> root = new LinkedHashMap<>(asMap(resume));
    Map<String, Object> skills = new LinkedHashMap<>(asMap(root.get(SKILLS)));
    additions.forEach((bucket, names) -> skills.put(bucket, merge(asList(skills.get(bucket)), names)));
    root.put(SKILLS, skills);
    return root;
  }

  private static List<String> merge(List<Object> listed, List<String> extracted) {
    Set<String> seen = new HashSet<>();
    List<String> merged = new ArrayList<>();
    Stream.concat(listed.stream(), extracted == null ? Stream.empty() : extracted.stream())
        .filter(String.class::isInstance)
        .map(name -> ((String) name).trim())
        .filter(name -> !name.isEmpty() && seen.add(name.toLowerCase(Locale.ROOT)))
        .forEach(merged::add);
    return merged;
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

package com.histmersion.api

// ── label display ─────────────────────────────────────────────────────────────────────────────────

/** Display helper for user-facing labels: the ONE place that turns internal `snake_case` into readable
  * `space separated` form. Used by BOTH server Markdown and web — replaces the scattered `_.replace("_", " ")` calls.
  */
object Label:
  /** `"bronze_age_collapse"` → `"bronze age collapse"`. */
  def display(s: String): String = s.replace("_", " ")

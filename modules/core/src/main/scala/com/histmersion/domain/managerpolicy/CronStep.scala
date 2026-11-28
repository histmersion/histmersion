package com.histmersion
package domain.managerpolicy

import com.histmersion.api.given
import java.time.Instant
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum CronStep:
  /** Nothing: no cron, or not due yet. */
  case Idle

  /** Not scheduled yet: record its first firing, fire nothing. */
  case Schedule(next: Option[Instant])

  /** Due: fire now, then next at `next` (None: it never fires again). */
  case Fire(next: Option[Instant])

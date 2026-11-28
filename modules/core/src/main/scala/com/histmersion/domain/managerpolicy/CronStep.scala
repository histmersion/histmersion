package com.histmersion
package domain.managerpolicy

import com.histmersion.api.given
import java.time.Instant
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum CronStep:
  case Idle

  case Schedule(next: Option[Instant])

  case Fire(next: Option[Instant])

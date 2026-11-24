package com.histmersion
package util.jobs

import com.histmersion.lint.{LintRule, tolerated}
/** Why a running background job stops before its end: a person paused it (to be resumed) or cancelled it. */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
enum JobStop derives CanEqual:
  case Pause, Cancel

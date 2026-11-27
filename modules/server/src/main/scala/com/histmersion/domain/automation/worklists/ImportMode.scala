package com.histmersion
package domain.automation.worklists

import com.histmersion.lint.{tolerated, LintRule}

/** What an import does with the list it imports into: empty it first (importing the same file twice is idempotent), or
  * add to it.
  */
enum ImportMode derives CanEqual:
  case Replace, Append

object ImportMode:
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def of(replace: Boolean): ImportMode = if replace then Replace else Append

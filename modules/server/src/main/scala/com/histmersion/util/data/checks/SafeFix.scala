package com.histmersion
package util.data.checks

import com.histmersion.api.checks.Lint.LintFixResult

/** A repair safe to make without a person: when it runs, where it runs among every owner's fixes, and the part of the
  * fix result it reports (its line and its counts; the other counts zero).
  */
final case class SafeFix(when: FixWhen, rank: FixRank, run: CheckContext => LintFixResult)

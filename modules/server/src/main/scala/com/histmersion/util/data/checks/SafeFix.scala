package com.histmersion.util.data.checks

import com.histmersion.api.Lint.LintFixResult

/** A repair safe to make without a person: when it runs, and the part of the fix result it reports (its line and its
  * counts; the other counts zero).
  */
final case class SafeFix(when: FixWhen, run: CheckContext => LintFixResult)

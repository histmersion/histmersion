package com.histmersion
package util.data.checks

import com.histmersion.api.checks.Lint.LintFixResult
import com.histmersion.util.database.WritePermit

/** A repair safe to make without a person: when it runs, where it runs among every owner's fixes, and the part of the
  * fix result it reports (its line and its counts; the other counts zero). It writes, under the permit of the operation
  * that runs the fixes (`lint --fix`, under the maintenance gate).
  */
final case class SafeFix(when: FixWhen, rank: FixRank, run: CheckContext => WritePermit ?=> LintFixResult)

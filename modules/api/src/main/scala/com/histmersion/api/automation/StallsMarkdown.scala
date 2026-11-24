package com.histmersion
package api.automation
import com.histmersion.api.given
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object StallsMarkdown:

  import Stalls.*

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def report(r: StallReport): String =
    val head = s"# Stalled goals: ${r.stalled.size} of ${r.goals} with no progress in ${r.days} day(s)"
    val reasons =
      if r.byReason.isEmpty then "" else "\n\n" + r.byReason.map(x => s"${x.code} ${x.count}").mkString(" · ")
    val rows = if r.stalled.isEmpty then "\n\n_Every unfinished goal progressed._"
    else
      "\n\n" + r.stalled
        .map { s =>
          s"- **${s.slug}** _(${s.status})_ — ${s.idleDays.fold("never progressed")(d => s"idle $d day(s)")}: _${s.code}_ — ${s.why}\n" +
            s"  - unblock: `${s.action}`"
        }
        .mkString("\n")
    (head + reasons + rows)

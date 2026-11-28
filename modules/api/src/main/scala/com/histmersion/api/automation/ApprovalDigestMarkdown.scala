package com.histmersion
package api.automation

import com.histmersion.util.problem.Text.sentences
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object ApprovalDigestMarkdown:
  export Approval.DecidedBy.Wire as DeciderDecidedBy

  import ApprovalDigest.*

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  private def decider(decidedBy: ApprovalDigestMarkdown.DeciderDecidedBy): String = (decidedBy.value: String) match
    case "checked" => ("the autopilot's checks (within the day's cap); a person may decide them first")
    case Waits     => ("nobody now: they wait (see above); a person may still decide one")
    case _         => ("a person")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def digest(d: Digest): String =
    val head = s"# Waiting for a person (${d.at.value.take(16).replace('T', ' ')} UTC)\n\n" +
      s"- ${Plural.count(Plural.CountN(d.total.value.toLong), Plural.CountSingular("open approval"))}" +
      (if d.heldForPerson.value > 0 then
         s"\n- ${Plural.count(Plural.CountN(d.heldForPerson.value.toLong), Plural.CountSingular("manager item"))} held for a person, or with a run waiting for one"
       else "") +
      d.lastWritten.fold("")(t => s"\n- the daily digest was last written to the worklog at $t") +
      d.goalPlansWait.fold("")(w => s"\n- **$w**; a person may still approve one")
    val kinds = d.kinds.map { g =>
      s"## ${g.kind}: ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") + "\n\n" +
        s"- decided by: ${decider(g.decidedBy)}\n" +
        (if g.subjects.isEmpty then ""
         else s"- about: ${g.subjects.map(x => s"${x.subject} (${x.count})").mkString(", ")}\n") +
        s"- ids: ${g.ids.map(i => s"#$i").mkString(", ")}${if (g.count).value > g.ids.size then ", …" else ""}\n" +
        s"- all at once: `histmersion agent decide-all approve --kind ${g.kind}` (or `reject`; `--dry-run` lists them)"
    }
    ((head :: kinds).mkString("\n\n"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bulk(r: BulkResult): String =
    val verb = if r.dryRun.dryRun then s"would ${r.decision}"
    else if r.decision.value == "approve" then "approved"
    else "rejected"
    s"# ${Plural.count(Plural.CountN(r.matched.value.toLong), Plural.CountSingular("open approval"))} matched: $verb ${r.decided.size}" + (if r.dryRun.dryRun
                                                                                                                                           then
                                                                                                                                             " (dry run: nothing decided)"
                                                                                                                                           else
                                                                                                                                             "") +
      (if r.decided.isEmpty then "" else s"\n\n- ${r.decided.map(i => s"#$i").mkString(", ")}") +
      (if r.failed.isEmpty then ""
       else
         "\n\n## Not decided\n\n" + r.failed
           .map(f => s"- #${f.id}: ${f.problems.sentences.mkString("; ")}")
           .mkString("\n")) +
      s"\n\n${Plural.count(Plural.CountN(r.left.value.toLong), Plural.CountSingular("open approval"))} left."

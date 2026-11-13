package com.histmersion
package api.automation

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.Plural
import com.histmersion.api.given
object ApprovalDigestMarkdown:
  export Approval.DecidedBy.Wire as DeciderDecidedBy

  import ApprovalDigest.*

  private def decider(decidedBy: ApprovalDigestMarkdown.DeciderDecidedBy): String = (decidedBy.value: String) match
    case "checked" => ("the autopilot's checks (within the day's cap); a person may decide them first")
    case Waits     => ("nobody now: they wait (see above); a person may still decide one")
    case _         => ("a person")

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

  def bulk(r: BulkResult): String =
    val verb = if r.dryRun.value then s"would ${r.decision}"
    else if r.decision.value == "approve" then "approved"
    else "rejected"
    s"# ${Plural.count(Plural.CountN(r.matched.value.toLong), Plural.CountSingular("open approval"))} matched: $verb ${r.decided.size}" + (if r.dryRun.value
                                                                                                                                           then
                                                                                                                                             " (dry run: nothing decided)"
                                                                                                                                           else
                                                                                                                                             "") +
      (if r.decided.isEmpty then "" else s"\n\n- ${r.decided.map(i => s"#$i").mkString(", ")}") +
      (if r.failed.isEmpty then ""
       else "\n\n## Not decided\n\n" + r.failed.map(f => s"- #${f.id}: ${f.error.sentence}").mkString("\n")) +
      s"\n\n${Plural.count(Plural.CountN(r.left.value.toLong), Plural.CountSingular("open approval"))} left."

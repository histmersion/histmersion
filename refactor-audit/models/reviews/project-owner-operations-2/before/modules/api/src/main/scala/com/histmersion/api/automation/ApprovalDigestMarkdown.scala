package com.histmersion.api.automation

import com.histmersion.api.Plural
import com.histmersion.api.given
object ApprovalDigestMarkdown:
  type DeciderResult = DeciderResult.T
  object DeciderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeciderDecidedBy = DeciderDecidedBy.T
  object DeciderDecidedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DigestResult = DigestResult.T
  object DigestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BulkResult = BulkResult.T
  object BulkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import ApprovalDigest.*

  private def decider(decidedBy: com.histmersion.api.automation.ApprovalDigestMarkdown.DeciderDecidedBy): com.histmersion.api.automation.ApprovalDigestMarkdown.DeciderResult = decidedBy match
    case "checked" => _root_.com.histmersion.api.automation.ApprovalDigestMarkdown.DeciderResult("the autopilot's checks (within the day's cap); a person may decide them first")
    case Waits     => _root_.com.histmersion.api.automation.ApprovalDigestMarkdown.DeciderResult("nobody now: they wait (see above); a person may still decide one")
    case _         => _root_.com.histmersion.api.automation.ApprovalDigestMarkdown.DeciderResult("a person")

  def digest(d: Digest): com.histmersion.api.automation.ApprovalDigestMarkdown.DigestResult =
    val head = s"# Waiting for a person (${d.at.take(16).replace('T', ' ')} UTC)\n\n" +
      s"- ${Plural.count(d.total, "open approval")}" +
      (if d.heldForPerson > 0 then
         s"\n- ${Plural.count(d.heldForPerson, "manager item")} held for a person, or with a run waiting for one"
       else "") +
      d.lastWritten.fold("")(t => s"\n- the daily digest was last written to the worklog at $t") +
      d.goalPlansWait.fold("")(w => s"\n- **$w**; a person may still approve one")
    val kinds = d.kinds.map { g =>
      s"## ${g.kind}: ${g.count}" + g.oldest.fold("")(o => s" (oldest $o)") + "\n\n" +
        s"- decided by: ${decider(g.decidedBy)}\n" +
        (if g.subjects.isEmpty then ""
         else s"- about: ${g.subjects.map(x => s"${x.subject} (${x.count})").mkString(", ")}\n") +
        s"- ids: ${g.ids.map(i => s"#$i").mkString(", ")}${if g.count > g.ids.size then ", …" else ""}\n" +
        s"- all at once: `histmersion agent decide-all approve --kind ${g.kind}` (or `reject`; `--dry-run` lists them)"
    }
    _root_.com.histmersion.api.automation.ApprovalDigestMarkdown.DigestResult((head :: kinds).mkString("\n\n"))

  def bulk(r: BulkResult): com.histmersion.api.automation.ApprovalDigestMarkdown.BulkResult =
    val verb = if r.dryRun then s"would ${r.decision}" else if r.decision == "approve" then "approved" else "rejected"
    s"# ${Plural.count(r.matched, _root_.com.histmersion.api.Plural.CountSingular("open approval"))} matched: $verb ${r.decided.size}" + (if r.dryRun then
                                                                                           " (dry run: nothing decided)"
                                                                                         else "") +
      (if r.decided.isEmpty then "" else s"\n\n- ${r.decided.map(i => s"#$i").mkString(", ")}") +
      (if r.failed.isEmpty then ""
       else "\n\n## Not decided\n\n" + r.failed.map(f => s"- #${f.id}: ${f.error}").mkString("\n")) +
      s"\n\n${Plural.count(r.left, "open approval")} left."

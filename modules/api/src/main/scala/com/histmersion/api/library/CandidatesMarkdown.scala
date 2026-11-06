package com.histmersion.api.library
import com.histmersion.api.given
object CandidatesMarkdown:

  type CountsResult = CountsResult.T
  object CountsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepResult = SweepResult.T
  object SweepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkedResult = MarkedResult.T
  object MarkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def counts(cs: List[CandidateCount]): com.histmersion.api.library.CandidatesMarkdown.CountsResult =
    if cs.isEmpty then _root_.com.histmersion.api.library.CandidatesMarkdown.CountsResult("none yet") else _root_.com.histmersion.api.library.CandidatesMarkdown.CountsResult(cs.map(c => s"${c.count} ${c.state}").mkString(", "))

  def list(l: CandidateList): com.histmersion.api.library.CandidatesMarkdown.ListResult =
    val head = s"# Candidates${l.goal.fold(" (every goal)")(g => s" for goal `$g`")} — ${l.state}"
    val total = s"Counts: ${counts(l.counts)}."
    val rows = l.candidates.map { c =>
      val who = c.authors.headOption.fold("")(a => s"$a${if c.authors.size > 1 then " et al." else ""}, ")
      s"- **#${c.id}** `${c.doi}` — ${c.title} ($who${c.year.fold("n.d.")(_.toString)}${c.venue.fold("")(v => s", $v")})" +
        (if ((c.isOa) == _root_.com.histmersion.api.library.Source.Candidate.IsOa(true)) then " · open access" else "") + (if l.goal.isEmpty then s" · goal `${c.goal}`" else "") +
        s"\n  found by ${c.foundBy.mkString("; ")} · first seen ${c.firstSeen.take(10)}" +
        c.abstractText.fold("")(a => s"\n  ${a.take(400)}${if a.length > 400 then "…" else ""}")
    }
    val tail = l.next.map(n => s"More: `--offset $n`.").toList
    val body = if rows.isEmpty then List(s"No ${l.state} candidates.") else rows
    _root_.com.histmersion.api.library.CandidatesMarkdown.ListResult((head :: "" :: total :: "" :: body ++ (if tail.isEmpty then Nil else "" :: tail)).mkString("\n"))

  def sweep(s: CandidateSweep): com.histmersion.api.library.CandidatesMarkdown.SweepResult =
    val w = s.watches.map { w =>
      s"- ${w.index} ${w.kind} `${w.subject}`: ${w.lastError.fold(s"${w.lastFound} found")(e => s"not read — $e")}" +
        w.watermark.fold("")(d => s" (reads from $d next)")
    }
    val left = if s.skipped.isEmpty then ""
    else " Left out: " + s.skipped.toList.sortBy(_._1).map((k, n) => s"$n $k").mkString(", ") + "."
    (s"# Candidate sweep of goal `${s.goal}`${if ((s.dryRun) == _root_.com.histmersion.api.library.CandidateSweep.DryRun(true)) then " (dry run: nothing stored)" else ""}" :: "" ::
      s"${s.found} work(s) found by ${s.watches.size} watch(es): ${s.added} new candidate(s), ${s.again} found again.$left " +
      s"${s.unseen} unseen candidate(s) wait for a decision (`histmersion candidates --goal ${s.goal}`)." :: "" ::
      (s.notes.map(n => s"- **$n**") ++ (if s.notes.isEmpty then Nil else List("")) ++ w)).mkString("\n")

  def marked(m: CandidateMarked): com.histmersion.api.library.CandidatesMarkdown.MarkedResult = _root_.com.histmersion.api.library.CandidatesMarkdown.MarkedResult(s"${m.updated} candidate(s) marked ${m.state}.")

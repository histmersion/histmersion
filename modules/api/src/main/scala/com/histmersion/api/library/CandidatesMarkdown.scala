package com.histmersion
package api.library
import com.histmersion.api.given
object CandidatesMarkdown:


  private def counts(cs: List[CandidateCount]): String =
    if cs.isEmpty then ("none yet") else (cs.map(c => s"${c.count} ${c.state}").mkString(", "))

  def list(l: CandidateList): String =
    val head = s"# Candidates${l.goal.fold(" (every goal)")(g => s" for goal `$g`")} — ${l.state}"
    val total = s"Counts: ${counts(l.counts)}."
    val rows = l.candidates.map { c =>
      val who = c.authors.headOption.fold("")(a => s"$a${if c.authors.size > 1 then " et al." else ""}, ")
      s"- **#${c.id}** `${c.doi}` — ${c.title} ($who${c.year.fold("n.d.")(_.toString)}${c.venue.fold("")(v => s", $v")})" +
        (if ((c.isOa) == Source.Candidate.IsOa(true)) then " · open access" else "") + (if l.goal.isEmpty then s" · goal `${c.goal}`" else "") +
        s"\n  found by ${c.foundBy.mkString("; ")} · first seen ${c.firstSeen.value.take(10)}" +
        c.abstractText.fold("")(a => s"\n  ${a.value.take(400)}${if a.value.length > 400 then "…" else ""}")
    }
    val tail = l.next.map(n => s"More: `--offset $n`.").toList
    val body = if rows.isEmpty then List(s"No ${l.state} candidates.") else rows
    ((head :: "" :: total :: "" :: body ++ (if tail.isEmpty then Nil else "" :: tail)).mkString("\n"))

  def sweep(s: CandidateSweep): String =
    val w = s.watches.map { w =>
      s"- ${w.index} ${w.kind} `${w.subject}`: ${w.lastError.fold(s"${w.lastFound} found")(e => s"not read — $e")}" +
        w.watermark.fold("")(d => s" (reads from $d next)")
    }
    val left = if s.skipped.isEmpty then ""
    else " Left out: " + s.skipped.toList.sortBy(_._1).map((k, n) => s"$n $k").mkString(", ") + "."
    ((s"# Candidate sweep of goal `${s.goal}`${if ((s.dryRun) == CandidateSweep.DryRun(true)) then " (dry run: nothing stored)" else ""}" :: "" ::
      s"${s.found} work(s) found by ${s.watches.size} watch(es): ${s.added} new candidate(s), ${s.again} found again.$left " +
      s"${s.unseen} unseen candidate(s) wait for a decision (`histmersion candidates --goal ${s.goal}`)." :: "" ::
      (s.notes.map(n => s"- **$n**") ++ (if s.notes.isEmpty then Nil else List("")) ++ w)).mkString("\n"))

  def marked(m: CandidateMarked): String = (s"${m.updated} candidate(s) marked ${m.state}.")

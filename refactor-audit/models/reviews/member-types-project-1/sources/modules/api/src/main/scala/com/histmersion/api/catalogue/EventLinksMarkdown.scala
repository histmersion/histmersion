package com.histmersion.api.catalogue
import com.histmersion.api.given
object EventLinksMarkdown:

  type KmResult = KmResult.T
  object KmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KmD = KmD.T
  object KmD extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContextResult = ContextResult.T
  object ContextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResultResult = ResultResult.T
  object ResultResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def km(d: com.histmersion.api.catalogue.EventLinksMarkdown.KmD): com.histmersion.api.catalogue.EventLinksMarkdown.KmResult = s"${math.round(d)} km"

  def context(d: com.histmersion.api.catalogue.EventCard): com.histmersion.api.catalogue.EventLinksMarkdown.ContextResult =
    val tier = s"\n\n**Tier:** ${d.tier}" + (if d.tier == EventTier.Catalogue then
                                               " — known from a catalogue only: a research lead, not evidence"
                                             else "")
    val names = if d.names.isEmpty then "" else s"  \n**Also called:** ${d.names.take(12).mkString(", ")}"
    val cat = if d.catalogue.isEmpty then ""
    else
      "\n\n**Catalogues**\n" + d.catalogue
        .map { c =>
          val size = (c.vei.map(v => s"VEI $v") ++ c.magnitude.map(m => s"M $m") ++ c.deaths.map(n => s"$n deaths"))
            .mkString(", ")
          s"- ${c.title} `${c.providerId}`${if size.isEmpty then "" else s" ($size)"} — ${c.url} · licence: ${c.licence} · cite: ${c.citation}"
        }
        .mkString("\n")
    val srcs = if d.sources.isEmpty then ""
    else
      "\n\n**Attested by**\n" + d.sources
        .map(s =>
          s"- [[${s.citekey}]]${s.title.fold("")(t => s" — $t")}" +
            s.quote.fold("")(q => s""" ("$q")""")
        )
        .mkString("\n")
    val arts = if d.articles.isEmpty then ""
    else
      "\n\n**Articles citing them**\n" + d.articles
        .map(a => s"- [[${a.slug}]] — ${a.title} (cites [[${a.citekey}]])")
        .mkString("\n")
    val near = if d.findings.isEmpty then ""
    else
      "\n\n**Findings near it**\n" + d.findings
        .map(f =>
          s"- [[${f.citekey}]] `${f.id}` — ${km(f.km)}, " +
            s"${if f.yearGap == 0 then "overlapping in time" else s"${f.yearGap} years apart"}: ${f.claim.take(140)}"
        )
        .mkString("\n")
    val cands = if d.candidates.isEmpty then ""
    else "\n\n**Possibly discussed in (unconfirmed)**\n" + d.candidates.map(line).mkString("\n")
    val leads = if d.leads.isEmpty then ""
    else
      "\n\n**Leads**\n" + d.leads
        .map(l => s"- ${l.text}\n" + l.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n"))
        .mkString("\n")
    tier + names + cat + srcs + arts + near + cands + leads

  private def line(c: EventLinkCandidate): com.histmersion.api.catalogue.EventLinksMarkdown.LineResult =
    s"- [[${c.citekey}]]${c.title.fold("")(t => s" — $t")} · score ${c.score} · ${c.status}" +
      (if c.reasons.isEmpty then "" else s" — ${c.reasons.mkString("; ")}")

  def list(l: EventLinkList): com.histmersion.api.catalogue.EventLinksMarkdown.ListResult =
    if l.candidates.isEmpty then s"_No source in the corpus is near `${l.eventId}` in time and space or names it._"
    else
      (s"## Sources that may discuss ${l.label} (`${l.eventId}`)" :: "" ::
        "_Proposed from findings near it and passages naming it — unconfirmed until confirmed (an attestation)._" :: "" ::
        l.candidates.map(line)).mkString("\n")

  def result(r: EventLinksResult): com.histmersion.api.catalogue.EventLinksMarkdown.ResultResult =
    List(
      Option.when(r.attested.nonEmpty)(
        "**Attested:** " + r.attested
          .map(a =>
            s"[[${a.citekey}]] #${a.id} (chars ${a.charStart.getOrElse(0)}–${a.charEnd.getOrElse(0)}: \"${a.quote.getOrElse("")}\")"
          )
          .mkString(", ")
      ),
      Option.when(r.refused.nonEmpty)("**Not attested:**\n" + r.refused.map("- " + _).mkString("\n")),
      Option.when(r.rejected.nonEmpty)("**Rejected:** " + r.rejected.mkString(", ")),
      Some(list(r.links))
    ).flatten.mkString("\n\n")

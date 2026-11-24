package com.histmersion
package api.catalogue
import com.histmersion.api.given
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object EventLinksMarkdown:
  private def km(d: Double): String = s"${math.round(d)} km"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def context(d: EventCard): String =
    val tier = s"\n\n**Tier:** ${d.tier}" + (if d.tier == EventGeo.Tier(EventTier.Catalogue) then
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
          s"- [[${f.citekey}]] `${f.id}` — ${km(f.km.value)}, " +
            s"${if f.yearGap.value == 0 then "overlapping in time" else s"${f.yearGap} years apart"}: ${f.claim.value.take(140)}"
        )
        .mkString("\n")
    val cands = if d.candidates.isEmpty then ""
    else "\n\n**Possibly discussed in (unconfirmed)**\n" + d.candidates.map(line).mkString("\n")
    val leads = if d.leads.isEmpty then ""
    else
      "\n\n**Leads**\n" + d.leads
        .map(l => s"- ${l.text}\n" + l.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n"))
        .mkString("\n")
    (tier + names + cat + srcs + arts + near + cands + leads)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def line(c: EventLinkCandidate): String =
    (s"- [[${c.citekey}]]${c.title.fold("")(t => s" — $t")} · score ${c.score} · ${c.status}" +
      (if c.reasons.isEmpty then "" else s" — ${c.reasons.mkString("; ")}"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def list(l: EventLinkList): String =
    if l.candidates.isEmpty then (s"_No source in the corpus is near `${l.eventId}` in time and space or names it._")
    else
      ((s"## Sources that may discuss ${l.label} (`${l.eventId}`)" :: "" ::
        "_Proposed from findings near it and passages naming it — unconfirmed until confirmed (an attestation)._" :: "" ::
        l.candidates.map(line)).mkString("\n")
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def result(r: EventLinksResult): String =
    (List(
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
    ).flatten.mkString("\n\n"))

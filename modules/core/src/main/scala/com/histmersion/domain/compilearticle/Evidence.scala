package com.histmersion
package domain.compilearticle

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.automation.Goal.Plans.ArticleDraft
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.domain.compilearticle.NotUsed
import com.histmersion.RowCount
import com.histmersion.domain.compilearticle.SourceFact
import com.histmersion.domain.compilearticle.EdgeFact
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.compilearticle.Extracted
import com.histmersion.domain.CompileArticle.Description

final case class Evidence(
    filter: ArticleDraft.Filter,
    total: RowCount,
    sources: List[SourceFact],
    claims: Map[Citekey, Evidence.ClaimCount],
    edges: List[EdgeFact],
    disputed: List[Citekey],
    notUsed: List[NotUsed]
) derives JsonCodec:
  def used: List[Citekey] =
    sources.map(_.citekey).filter(ck => claims.getOrElse(ck, Evidence.ClaimCount(0)).hasClaims)

object Evidence:
  type ClaimCount = ClaimCount.T
  object ClaimCount extends Newtype[Int, Pure]:
    extension (n: ClaimCount) def hasClaims: Boolean = n.value > 0

  extension (ev: Evidence)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def evidenceText(extracted: List[Extracted]): Description =
      val bySource = extracted.map(e => e.citekey -> e).toMap
      val sources = ev.sources.filter(s => ev.claims.getOrElse(s.citekey, Evidence.ClaimCount(0)).hasClaims).map { s =>
        val claims = bySource.get(s.citekey).toList.flatMap(_.claims).map { c =>
          val when = c.date.fold("")(d => s" · date: $d")
          val where = c.place.fold("")(p => s" · place: $p")
          s"  - ${c.claim} (confidence ${c.confidence.toString.toLowerCase}$when$where)\n    quote: \"${c.quote}\""
        }
        s"- [[${s.citekey}]] — ${s.title} (${s.year.fold("n.d.")(_.toString)}), status **${s.status.wire}**\n" +
          claims.mkString("\n")
      }
      val edges = if ev.edges.isEmpty then "none recorded among these sources (not evidence of agreement)"
      else ev.edges.map(e => s"[[${e.from}]] ${e.kind} [[${e.to}]]").mkString("; ")
      val weight =
        ev.claims.toList.filter(_._2.hasClaims).sortBy(-_._2.value).map((ck, n) => s"[[$ck]] $n").mkString(", ")
      val notUsed =
        if ev.notUsed.isEmpty then "none"
        else ev.notUsed.map(n => s"[[${n.citekey}]]: ${n.reason}").mkString("\n- ")
      Description(
        s"Filter: ${ev.filter} → ${ev.total}\n\n## Claim table\n\n${sources.mkString("\n")}\n\n" +
          s"## Evidence model\n\n- disagreement edges among the sources: $edges\n" +
          (if ev.edges.isEmpty then ""
           else
             "- the critique checklist: each edge is answered when its two sources are cited in ONE paragraph — the one that " +
               "states the criticised claim (the target of challenges/refutes/updates; the older work of superseded_by), with the " +
               "critique in a sentence from the critic's claims. A paragraph that names an author cites that author's work there.\n") +
          s"- sources with a non-current status: ${
              if ev.disputed.isEmpty then "none" else ev.disputed.map(c => s"[[$c]]").mkString(", ")
            }\n" +
          s"- claims per source (due weight): $weight\n\n## Not used\n\n- $notUsed"
      )

package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.Proposal
import com.histmersion.domain.ReviewArticle.FindingCount
import com.histmersion.domain.research.workflows.Verdict
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Finding
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.workflows.ColdReaderAnswer
import com.histmersion.domain.ReviewArticle.RoundCount
import com.histmersion.domain.ReviewArticle.FixRun
import com.histmersion.domain.research.workflows.Quotation

final case class Merged(
    round: Merged.Round,
    verdict: Verdict,
    findings: List[Finding],
    explainBack: Option[Merged.ExplainBack],
    emptySlots: List[Merged.EmptySlots],
    proposals: List[Proposal],
    deferred: List[Merged.Deferred] = Nil
):
  def majors: List[Finding] = findings.filter(f => f.major && !deferred.contains(f.id))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def escalated: List[Finding] = findings.filter(_.escalated.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def count(r: Reviewer): FindingCount = FindingCount(findings.count(_.reviewer == r))

object Merged:

  type Round = Round.T
  object Round extends Newtype[Int, Pure]
  export ColdReaderAnswer.ExplainBack
  export ReviewArticle.ExplainSlotsValue as EmptySlots
  export Finding.Id as Deferred

  extension (m: Merged)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def done(round: Round, rounds: RoundCount, fix: FixRun): Boolean =
      m.majors.isEmpty || !fix.value || round.value >= rounds.value

  extension (m: Merged)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def findingsText(notes: Map[String, String] = Map.empty): String =
      m.findings
        .map { f =>
          val esc = if f.escalated.value then " [raised again after a rejection]" else ""
          s"- ${f.id} [${f.reviewer.wire}, ${f.severity.wire}${
              if f.kind.value.nonEmpty then s", ${f.kind}" else ""
            }]$esc " +
            s"where: ${f.where}\n  quote: ${f.quote}\n  problem: ${f.problem}\n  fix: ${f.fix}" +
            f.pair.fold("")(p => s"\n  pair: ${p.wire}") +
            f.evidence.map(e => s"\n  evidence: ${e.evidenceLine}").mkString +
            notes.get(f.id.value).fold("")(n => s"\n  note: $n")
        }
        .mkString("\n")

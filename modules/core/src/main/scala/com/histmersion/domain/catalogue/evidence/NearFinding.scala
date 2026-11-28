package com.histmersion
package domain.catalogue.evidence

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.catalogue.events.Placement
import com.histmersion.api.catalogue.EventTaxonomy
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.Plural
import com.histmersion.api.catalogue.EventLinkCandidate
import com.histmersion.api.catalogue.EventPassage
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.library.Title
import com.histmersion.domain.catalogue.AttestationTarget
import com.histmersion.api.catalogue.Attestation

final case class NearFinding(id: NearFinding.Id, citekey: Citekey, km: NearFinding.Km, yearGap: NearFinding.YearGap)

object NearFinding:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Km = Km.T
  object Km extends Newtype[Double, Pure]
  type YearGap = YearGap.T
  object YearGap extends Newtype[Long, Pure]

  final case class Weights(
      km: Weights.Km,
      years: Weights.Years,
      name: Weights.Name,
      finding: Weights.Finding,
      minScore: Weights.MinScore,
      minNameChars: Weights.MinNameChars,
      maxCandidates: Weights.MaxCandidates
  )

  object Weights:
    export Placement.Tolerance.Km
    export Placement.Tolerance.Years
    type Name = Name.T
    object Name extends Newtype[Double, Pure]
    type Finding = Finding.T
    object Finding extends Newtype[Double, Pure]
    type MinScore = MinScore.T
    object MinScore extends Newtype[Double, Pure]
    type MinNameChars = MinNameChars.T
    object MinNameChars extends Newtype[Int, Pure]
    type MaxCandidates = MaxCandidates.T
    object MaxCandidates extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def searchNames(names: List[String], minChars: Weights.MinNameChars): List[String] =
    val generic =
      (EventTaxonomy.kinds.map(_.label.value) ++ EventTaxonomy.kinds.map(_.kind.value)).map(_.toLowerCase).toSet
    names
      .map(_.trim.replaceAll("\\s+", " "))
      .filter(n => n.length >= minChars.value && !generic(n.toLowerCase))
      .distinctBy(_.toLowerCase)
      .sortBy(n => (-n.length, n))

  type FindingWeight = FindingWeight.T

  object FindingWeight extends Newtype[Double, Pure]

  extension (f: NearFinding)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def findingWeight(w: Weights): FindingWeight =
      val space = if w.km.value <= 0 then 0.0 else (1.0 - f.km.value / w.km.value).max(0.0)
      val time =
        if w.years.value <= 0 then (if f.yearGap.value == 0 then 1.0 else 0.0)
        else (1.0 - f.yearGap.value.toDouble / w.years.value).max(0.0)
      FindingWeight(space * time)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def candidates(
      eventId: Event.Id,
      near: List[NearFinding],
      named: Map[Citekey, List[EventPassage]],
      w: Weights,
      titles: Map[Citekey, Title] = Map.empty
  ): List[EventLinkCandidate] =
    val byCk = near.groupBy(_.citekey)
    (byCk.keySet ++ named.keySet).toList
      .flatMap { ck =>
        val fs = byCk.getOrElse(ck, Nil).sortBy(f => (f.yearGap.value: Long, f.km.value: Double, f.id.value: String))
        val ps = named.getOrElse(ck, Nil)
        val fScore = (fs.map(_.findingWeight(w).value).sum * w.finding.value).min(w.finding.value)
        val nScore = if ps.isEmpty then 0.0 else w.name.value * (1.0 + 0.1 * (ps.size - 1).min(4))
        val score = BigDecimal(fScore + nScore).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
        val reasons =
          Option
            .when(ps.nonEmpty)(
              s"names it ${Plural.count(Plural.CountN(ps.size), Plural.CountSingular("time"))}: " +
                ps.map(p => s"\"${p.quote}\"").distinct.take(3).mkString(", ")
            )
            .toList ++
            Option
              .when(fs.nonEmpty) {
                val nearest = fs.map(_.km.value).min
                val overlap = fs.count(_.yearGap.value == 0)
                s"${Plural.count(Plural.CountN(fs.size), Plural.CountSingular("finding"))} within ${math.round(fs.map(_.km.value).max)} km (nearest ${math.round(nearest)} km)" +
                  (if overlap > 0 then s", $overlap overlapping in time"
                   else s", nearest in time ${fs.map(_.yearGap.value).min} years apart")
              }
              .toList
        Option.when(score >= w.minScore.value)(
          EventLinkCandidate(
            com.histmersion.api.catalogue.Event.Id(eventId.value),
            com.histmersion.api.library.Citekey(ck.value),
            EventLinkCandidate.Score(score),
            EventLinkCandidate.StatusValue(Event.LinkStatus.Proposed),
            titles.get(ck),
            fs.map(f => com.histmersion.api.library.Finding.Id(f.id.value)),
            ps,
            reasons.map(EventLinkCandidate.Reasons(_))
          )
        )
      }
      .sortBy(c => (-c.score.value, c.citekey.value))
      .take(w.maxCandidates.value.max(1))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def attestationPassage(ps: List[EventPassage]): Option[EventPassage] =
    ps.sortBy(p => (-p.name.value.length, p.start.value: Int)).headOption

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def attestation(eventId: Event.Id, citekey: Citekey, p: EventPassage): Attestation.Put =
    Attestation.Put(
      com.histmersion.api.library.Citekey(citekey.value),
      Attestation.TargetType(AttestationTarget.Event),
      Attestation.TargetId(eventId.value),
      quote = Some(p.quote),
      charStart = Some(Attestation.CharStart(p.start.value)),
      charEnd = Some(Attestation.CharEnd(p.end.value)),
      note = Some(Attestation.Note(s"names the event as \"${p.name}\" (confirmed from `events links`)"))
    )

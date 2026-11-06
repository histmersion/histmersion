package com.histmersion.domain.catalogue.evidence

import com.histmersion.domain.catalogue.AttestationTarget

import com.histmersion.api.Plural
import com.histmersion.api.catalogue.{EventLinkCandidate, EventPassage, EventTaxonomy}
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.Attestation

/** The matcher. Pure: the database gives it the findings near an event and the passages naming it. */
object EventMatcher:

  /** How far a finding may be from the event (`km`) and how many years apart (`years`; 0 = their spans overlap), what a
    * name match and a finding weigh, the least score kept, and the least length of a name worth searching for.
    */

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
    type Km = Km.T
    object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Years = Years.T
    object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Finding = Finding.T
    object Finding extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinScore = MinScore.T
    object MinScore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinNameChars = MinNameChars.T
    object MinNameChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxCandidates = MaxCandidates.T
    object MaxCandidates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One finding near the event: its id, source, distance and the years between its period and the event's span (0 when
    * they overlap).
    */
  final case class NearFinding(id: NearFinding.Id, citekey: com.histmersion.domain.library.Citekey, km: NearFinding.Km, yearGap: NearFinding.YearGap)

  object NearFinding:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Km = Km.T
    object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearGap = YearGap.T
    object YearGap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: the names worth searching a body for — distinct ignoring case, at least `minChars` long and more than a bare
    * kind word ("Battle", "Siege"), longest first (a longer name is the stronger match).
    */
  def searchNames(names: List[String], minChars: Weights.MinNameChars): List[String] =
    val generic = (EventTaxonomy.kinds.map(_.label.value) ++ EventTaxonomy.kinds.map(_.kind.value)).map(_.toLowerCase).toSet
    names
      .map(_.trim.replaceAll("\\s+", " "))
      .filter(n => n.length >= minChars.value && !generic(n.toLowerCase))
      .distinctBy(_.toLowerCase)
      .sortBy(n => (-n.length, n))

  type FindingWeight = FindingWeight.T
  object FindingWeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: how much one finding says — 1 at the event's place and time, falling linearly to 0 at `km` away and at
    * `years` apart.
    */
  def findingWeight(f: NearFinding, w: Weights): FindingWeight =
    val space = if w.km.value <= 0 then 0.0 else (1.0 - f.km.value / w.km.value).max(0.0)
    val time =
      if w.years.value <= 0 then (if f.yearGap.value == 0 then 1.0 else 0.0)
      else (1.0 - f.yearGap.value.toDouble / w.years.value).max(0.0)
    FindingWeight(space * time)

  /** Pure: the candidates for one event — every source with a finding near it or a passage naming it, scored: `name`
    * for a source whose text names the event (a little more for each further passage, up to five), plus `finding` × the
    * summed finding weights (at most `finding`). Sources below `minScore` are dropped; the best `maxCandidates` kept,
    * best first.
    */
  def candidates(
      eventId: com.histmersion.domain.catalogue.Event.Id,
      near: List[NearFinding],
      named: Map[com.histmersion.domain.library.Citekey, List[EventPassage]],
      w: Weights,
      titles: Map[com.histmersion.domain.library.Citekey, com.histmersion.api.library.Title] = Map.empty
  ): List[EventLinkCandidate] =
    val byCk = near.groupBy(_.citekey)
    (byCk.keySet ++ named.keySet).toList
      .flatMap { ck =>
        val fs = byCk.getOrElse(ck, Nil).sortBy(f => (f.yearGap.value: Long, f.km.value: Double, f.id.value: String))
        val ps = named.getOrElse(ck, Nil)
        val fScore = (fs.map(findingWeight(_, w).value).sum * w.finding.value).min(w.finding.value)
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

  /** Pure: the passage an attestation is written from — the longest name matched, the first place it occurs. `None`
    * when no passage names the event: then no attestation may be written.
    */
  def attestationPassage(ps: List[EventPassage]): Option[EventPassage] =
    ps.sortBy(p => (-p.name.value.length, p.start.value: Int)).headOption

  /** Pure: the attestation a confirmed passage becomes — the quote and its offsets (W3C TextQuoteSelector +
    * TextPositionSelector), and a note saying how it was found.
    */
  def attestation(eventId: com.histmersion.domain.catalogue.Event.Id, citekey: com.histmersion.domain.library.Citekey, p: EventPassage): Attestation.Put =
    Attestation.Put(
      com.histmersion.api.library.Citekey(citekey.value),
      Attestation.TargetType(AttestationTarget.Event),
      Attestation.TargetId(eventId.value),
      quote = Some(Attestation.Quote(p.quote.value)),
      charStart = Some(Attestation.CharStart(p.start.value)),
      charEnd = Some(Attestation.CharEnd(p.end.value)),
      note = Some(Attestation.Note(s"names the event as \"${p.name}\" (confirmed from `events links`)"))
    )

package com.histmersion.domain

import com.histmersion.api.{AttestationPut, AttestationTarget, EventLinkCandidate, EventLinkStatus, EventPassage, EventTaxonomy, Plural}

/** The matcher. Pure: the database gives it the findings near an event and the passages naming it. */
object EventMatcher:

  /** How far a finding may be from the event (`km`) and how many years apart (`years`; 0 = their spans
    * overlap), what a name match and a finding weigh, the least score kept, and the least length of a
    * name worth searching for. */
  final case class Weights(km: Double, years: Long, name: Double, finding: Double, minScore: Double,
      minNameChars: Int, maxCandidates: Int)

  /** One finding near the event: its id, source, distance and the years between its period and the
    * event's span (0 when they overlap). */
  final case class NearFinding(id: String, citekey: String, km: Double, yearGap: Long)

  /** Pure: the names worth searching a body for — distinct ignoring case, at least `minChars` long and
    * more than a bare kind word ("Battle", "Siege"), longest first (a longer name is the stronger match). */
  def searchNames(names: List[String], minChars: Int): List[String] =
    val generic = (EventTaxonomy.kinds.map(_.label) ++ EventTaxonomy.kinds.map(_.kind)).map(_.toLowerCase).toSet
    names.map(_.trim.replaceAll("\\s+", " ")).filter(n => n.length >= minChars && !generic(n.toLowerCase))
      .distinctBy(_.toLowerCase).sortBy(n => (-n.length, n))

  /** Pure: how much one finding says — 1 at the event's place and time, falling linearly to 0 at `km`
    * away and at `years` apart. */
  def findingWeight(f: NearFinding, w: Weights): Double =
    val space = if w.km <= 0 then 0.0 else (1.0 - f.km / w.km).max(0.0)
    val time  = if w.years <= 0 then (if f.yearGap == 0 then 1.0 else 0.0) else (1.0 - f.yearGap.toDouble / w.years).max(0.0)
    space * time

  /** Pure: the candidates for one event — every source with a finding near it or a passage naming it,
    * scored: `name` for a source whose text names the event (a little more for each further passage,
    * up to five), plus `finding` × the summed finding weights (at most `finding`). Sources below
    * `minScore` are dropped; the best `maxCandidates` kept, best first. */
  def candidates(eventId: String, near: List[NearFinding], named: Map[String, List[EventPassage]], w: Weights,
      titles: Map[String, String] = Map.empty): List[EventLinkCandidate] =
    val byCk = near.groupBy(_.citekey)
    (byCk.keySet ++ named.keySet).toList.flatMap { ck =>
      val fs = byCk.getOrElse(ck, Nil).sortBy(f => (f.yearGap, f.km, f.id))
      val ps = named.getOrElse(ck, Nil)
      val fScore = (fs.map(findingWeight(_, w)).sum * w.finding).min(w.finding)
      val nScore = if ps.isEmpty then 0.0 else w.name * (1.0 + 0.1 * (ps.size - 1).min(4))
      val score = BigDecimal(fScore + nScore).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
      val reasons =
        Option.when(ps.nonEmpty)(s"names it ${Plural.count(ps.size, "time")}: " +
          ps.map(p => s"\"${p.quote}\"").distinct.take(3).mkString(", ")).toList ++
        Option.when(fs.nonEmpty) {
          val nearest = fs.map(_.km).min
          val overlap = fs.count(_.yearGap == 0)
          s"${Plural.count(fs.size, "finding")} within ${math.round(fs.map(_.km).max)} km (nearest ${math.round(nearest)} km)" +
            (if overlap > 0 then s", $overlap overlapping in time" else s", nearest in time ${fs.map(_.yearGap).min} years apart")
        }.toList
      Option.when(score >= w.minScore)(EventLinkCandidate(eventId, ck, score, EventLinkStatus.Proposed,
        titles.get(ck), fs.map(_.id), ps, reasons))
    }.sortBy(c => (-c.score, c.citekey)).take(w.maxCandidates.max(1))

  /** Pure: the passage an attestation is written from — the longest name matched, the first place it
    * occurs. `None` when no passage names the event: then no attestation may be written. */
  def attestationPassage(ps: List[EventPassage]): Option[EventPassage] =
    ps.sortBy(p => (-p.name.length, p.start)).headOption

  /** Pure: the attestation a confirmed passage becomes — the quote and its offsets (W3C
    * TextQuoteSelector + TextPositionSelector), and a note saying how it was found. */
  def attestation(eventId: String, citekey: String, p: EventPassage): AttestationPut =
    AttestationPut(citekey, AttestationTarget.Event, eventId, quote = Some(p.quote),
      charStart = Some(p.start), charEnd = Some(p.end),
      note = Some(s"names the event as \"${p.name}\" (confirmed from `events links`)"))

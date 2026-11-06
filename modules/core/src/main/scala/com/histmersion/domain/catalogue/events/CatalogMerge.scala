package com.histmersion.domain.catalogue.events

import com.histmersion.api.{TimeSpan, Years}
import com.histmersion.api.catalogue.{CatalogRecord, EventGeo, EventTaxonomy, Geocode}

/** The pure half of events from catalogues: the providers' identities and terms, the mapping of their datings onto the
  * four-point time-span, and how records from several catalogues and the events already stored are merged into ONE
  * event.
  */

object CatalogMerge:

  /** How close two records of the same kind must be to be one event: their outer spans overlap once each is widened by
    * `years`, and their points are within `km`.
    */

  final case class Tolerance(km: Tolerance.Km, years: Tolerance.Years)

  object Tolerance:
    type Km = Km.T
    object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Years = Years.T
    object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Where one record goes: into a stored event (`existing`), or into the NEW event `id` (several records of one thing
    * share it). `why` says what matched.
    */
  enum Placement:
    case Existing(eventId: com.histmersion.domain.catalogue.Event.Id, why: Placement.Why)
    case New(eventId: com.histmersion.domain.catalogue.Event.Id, why: Placement.Why)
    def eventId: com.histmersion.domain.catalogue.Event.Id

  object Placement:
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def outer(ts: TimeSpan): Option[(com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year)] = ts.outer

  private def point(e: StoredEvent): (Option[com.histmersion.api.catalogue.Latitude], Option[com.histmersion.api.catalogue.Longitude]) =
    (e.lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)), e.lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)))

  private def outerYears(ts: TimeSpan): Option[(Long, Long)] = outer(ts).map((a, b) => (a.value, b.value))

  /** Pure: do two spans overlap once widened by `years`? An undated one overlaps nothing. */
  def overlaps(a: TimeSpan, b: TimeSpan, years: Tolerance.Years): Boolean =
    (outer(a), outer(b)) match
      case (Some((a0, a1)), Some((b0, b1))) =>
        val back = Years.Shift(-years.value)
        val forth = Years.Shift(years.value)
        Years.shift(a0, back).value <= Years.shift(b1, forth).value &&
        Years.shift(b0, back).value <= Years.shift(a1, forth).value
      case _ => false

  /** Pure: are two points within `km`? An unplaced one is near nothing. */
  def near(a: (Option[com.histmersion.api.catalogue.Latitude], Option[com.histmersion.api.catalogue.Longitude]), b: (Option[com.histmersion.api.catalogue.Latitude], Option[com.histmersion.api.catalogue.Longitude]), km: Tolerance.Km): Boolean =
    (a, b) match
      case ((Some(la), Some(lo)), (Some(lb), Some(ob))) => Geocode.distanceKm(lo, la, ob, lb).value <= km.value
      case _                                            => false

  /** Two kinds are the same thing when their canonical kinds are equal (a siege is no battle here: Wikidata lists both
    * for one fight only when it says so through the item).
    */
  private def canonical(kind: String): com.histmersion.api.catalogue.EventSummary.Kind =
    EventTaxonomy.canonical(com.histmersion.api.catalogue.EventSummary.Kind(kind))

  private def sameKind(a: String, b: String): Boolean = canonical(a) == canonical(b)

  /** Pure: how well a candidate's span and point fit a record's — the TIE-BREAK when several qualify, compared in this
    * order: the larger `score` (the overlap of the two outer spans over their union, 0…1: the one whose dating agrees
    * best), then the smaller `years` (between the spans' midpoints), then the smaller `km`, then the smaller id.
    * Undated or unplaced sides rank last on that criterion (∞), never first.
    */
  final case class Fit(score: Fit.Score, years: Fit.Years, km: Fit.Km, id: Fit.Id)
  object Fit:
    type Score = Score.T
    object Score extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Years = Years.T
    object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Km = Km.T
    object Km extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = com.histmersion.domain.catalogue.Event.Id
    val Id: _root_.com.histmersion.domain.catalogue.Event.Id.type = _root_.com.histmersion.domain.catalogue.Event.Id

    given Ordering[Fit] = Ordering.by((f: Fit) => (-f.score.value, f.years.value: Double, f.km.value: Double, f.id.value: String))

  /** Pure: the [[Fit]] of a candidate (`ts`, `lat`/`lon`, `id`) to a record. */
  def fit(r: CatalogRecord, ts: TimeSpan, lat: Option[com.histmersion.api.catalogue.Latitude], lon: Option[com.histmersion.api.catalogue.Longitude], id: Fit.Id): Fit =
    val (score, years) = (outerYears(r.timeSpan), outerYears(ts)) match
      case (Some((a0, a1)), Some((b0, b1))) =>
        val inter = (a1.min(b1) - a0.max(b0) + 1).max(0).toDouble
        val union = (a1.max(b1) - a0.min(b0) + 1).toDouble
        (inter / union, math.abs((a0 + a1) / 2.0 - (b0 + b1) / 2.0))
      case _ => (0.0, Double.PositiveInfinity)
    val km = (r.lat, r.lon, lat, lon) match
      case (Some(la), Some(lo), Some(lb), Some(ob)) => Geocode.distanceKm(lo, la, ob, lb).value
      case _                                        => Double.PositiveInfinity
    Fit(Fit.Score(score), Fit.Years(years), Fit.Km(km), id)

  /** Pure: where each record goes. A record joins, in this order of rules:
    *   1. the stored event that already holds its catalogue key;
    *   2. the stored event that is the same Wikidata item;
    *   3. a stored event of the same kind, overlapping in time and near in space, that no record of the SAME catalogue
    *      made (one catalogue's two records are two events: two eruptions of one volcano);
    *   4. a record placed before it in this run by the same key or item, or — from ANOTHER catalogue — the same `group`
    *      (+ overlapping time) or kind+time+space;
    * else it makes a new event, under [[CatalogRecord.eventId]].
    *
    * DETERMINISTIC: the records are placed in key order (provider, then id), whatever order they came in, and where a
    * rule finds several candidates the best [[Fit]] wins (overlap, then |Δt|, then distance, then id) — never the first
    * in the list. The answer keeps the order of `records`.
    */
  def place(records: List[CatalogRecord], stored: List[StoredEvent], tol: Tolerance): List[(CatalogRecord, Placement)] =
    val byKey = stored.flatMap(e => e.keys.map(_ -> e.id)).groupMap(_._1)(_._2).view.mapValues(_.minBy(_.value)).toMap
    val byWd = stored.flatMap(e => e.wikidata.map(_ -> e)).groupMap(_._1)(_._2)
    def best[A](cands: List[A])(f: A => Fit): Option[A] = cands.minByOption(f)
    def ofStored(r: CatalogRecord)(e: StoredEvent): Fit =
      fit(r, e.timeSpan, point(e)._1, point(e)._2, e.id)
    val order = records.zipWithIndex.sortBy((r, i) => (r.provider.value, r.providerId.value, i))
    // `var`-free fold: the records placed so far, with where they went
    val placed = order.foldLeft(Vector.empty[(Int, CatalogRecord, Placement)]) { case (acc, (r, i)) =>
      def storedMatch: Option[Placement] =
        byKey
          .get(r.key)
          .map(Placement.Existing(_, Placement.Why(s"${r.provider} ${r.providerId} already recorded")))
          .orElse(
            r.wikidata
              .flatMap(q => byWd.get(q))
              .flatMap(es => best(es)(ofStored(r)))
              .map(e => Placement.Existing(e.id, Placement.Why(s"same Wikidata item ${r.wikidata.get}")))
          )
          .orElse(
            best(
              stored.filter(e =>
                !e.keys.exists(_._1 == r.provider) && sameKind(e.kind.value, r.kind.value) &&
                  overlaps(e.timeSpan, r.timeSpan, tol.years) &&
                  near(point(e), (r.lat, r.lon), tol.km)
              )
            )(ofStored(r))
              .map(e =>
                Placement
                  .Existing(e.id, Placement.Why(s"same ${canonical(r.kind.value)} at the same time and place as '${e.id}'"))
              )
          )
      def runMatch: Option[Placement] =
        // the rules in order; within one rule, the best fit
        val prior = acc.toList.map((_, o, p) => (o, p))
        def pick(rule: (CatalogRecord, Placement) => Boolean): Option[Placement] =
          best(prior.filter(rule.tupled))((o, p) => fit(r, o.timeSpan, o.lat, o.lon, p.eventId)).map(_._2)
        pick((o, _) => o.key == r.key)
          .orElse(pick((o, _) => o.wikidata.isDefined && o.wikidata == r.wikidata))
          .orElse(
            pick((o, _) =>
              o.provider != r.provider && o.group.isDefined && o.group == r.group &&
                sameKind(o.kind.value, r.kind.value) && overlaps(o.timeSpan, r.timeSpan, tol.years)
            )
          )
          .orElse(
            pick((o, _) =>
              o.provider != r.provider && sameKind(o.kind.value, r.kind.value) &&
                overlaps(o.timeSpan, r.timeSpan, tol.years) && near((o.lat, o.lon), (r.lat, r.lon), tol.km)
            )
          )
          .map {
            case Placement.Existing(id, _) => Placement.Existing(id, Placement.Why("merged with another record of this run"))
            case Placement.New(id, _)      => Placement.New(id, Placement.Why("merged with another catalogue's record of it"))
          }
      val p = storedMatch.orElse(runMatch).getOrElse(Placement.New(com.histmersion.domain.catalogue.Event.Id.assume(CatalogRecord.eventId(r).value), Placement.Why("new")))
      acc :+ ((i, r, p))
    }
    placed.sortBy(_._1).map((_, r, p) => r -> p).toList

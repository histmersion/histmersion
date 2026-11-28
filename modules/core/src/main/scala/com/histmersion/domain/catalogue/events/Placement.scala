package com.histmersion
package domain.catalogue.events

import com.histmersion.domain.catalogue.Event
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.catalogue.EventTaxonomy
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.CatalogRecord

enum Placement:
  case Existing(eventId: Event.Id, why: Placement.Rationale)
  case New(eventId: Event.Id, why: Placement.Rationale)
  def eventId: Event.Id

object Placement:
  type Rationale = Rationale.T

  object Rationale extends Newtype[String, Pure]

  final case class Tolerance(km: Tolerance.Km, years: Tolerance.Years)

  object Tolerance:
    type Km = Km.T
    object Km extends Newtype[Double, Pure]
    type Years = Years.T
    object Years extends Newtype[Long, Pure]

  private def outer(ts: TimeSpan): Option[(Year, Year)] = ts.outer

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def point(e: StoredEvent): (Option[Latitude], Option[Longitude]) =
    (e.lat.map(l => Latitude(l.value)), e.lon.map(l => Longitude(l.value)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def outerYears(ts: TimeSpan): Option[(Long, Long)] = outer(ts).map((a, b) => (a.value, b.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def overlaps(a: TimeSpan, b: TimeSpan, years: Tolerance.Years): Boolean =
    (outer(a), outer(b)) match
      case (Some((a0, a1)), Some((b0, b1))) =>
        val back = Year.Shift(-years.value)
        val forth = Year.Shift(years.value)
        a0.shift(back) <= b1.shift(forth) &&
        b0.shift(back) <= a1.shift(forth)
      case _ => false

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def near(
      a: (Option[Latitude], Option[Longitude]),
      b: (Option[Latitude], Option[Longitude]),
      km: Tolerance.Km
  ): Boolean =
    (a, b) match
      case ((Some(la), Some(lo)), (Some(lb), Some(ob))) => Geocode.distanceKm(lo, la, ob, lb).value <= km.value
      case _                                            => false

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def canonical(kind: String): EventSummary.Kind =
    EventTaxonomy.canonical(EventSummary.Kind(kind))

  private def sameKind(a: String, b: String): Boolean = canonical(a) == canonical(b)

  final case class Fit(score: Fit.Score, years: Fit.Years, km: Fit.Km, id: Fit.Id)

  object Fit:
    type Score = Score.T
    object Score extends Newtype[Double, Pure]
    type Years = Years.T
    object Years extends Newtype[Double, Pure]
    type Km = Km.T
    object Km extends Newtype[Double, Pure]
    export Event.Id

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    given Ordering[Fit] =
      Ordering.by((f: Fit) => (-f.score.value, f.years.value: Double, f.km.value: Double, f.id.value: String))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fit(r: CatalogRecord, ts: TimeSpan, lat: Option[Latitude], lon: Option[Longitude], id: Fit.Id): Fit =
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def place(records: List[CatalogRecord], stored: List[StoredEvent], tol: Tolerance): List[(CatalogRecord, Placement)] =
    val byKey = stored.flatMap(e => e.keys.map(_ -> e.id)).groupMap(_._1)(_._2).view.mapValues(_.min).toMap
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
          .map(
            Placement.Existing(_, Rationale(s"${r.provider} ${r.providerId} already recorded"))
          )
          .orElse(
            r.wikidata
              .flatMap(q => byWd.get(q))
              .flatMap(es => best(es)(ofStored(r)))
              .map(e => Placement.Existing(e.id, Rationale(s"same Wikidata item ${r.wikidata.get}")))
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
                  .Existing(
                    e.id,
                    Rationale(s"same ${canonical(r.kind.value)} at the same time and place as '${e.id}'")
                  )
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
            case Placement.Existing(id, _) =>
              Placement.Existing(id, Rationale("merged with another record of this run"))
            case Placement.New(id, _) =>
              Placement.New(id, Rationale("merged with another catalogue's record of it"))
          }
      val p = storedMatch
        .orElse(runMatch)
        .getOrElse(
          Placement.New(Event.Id.assume(CatalogRecord.eventId(r).value), Rationale("new"))
        )
      acc :+ ((i, r, p))
    }
    placed.sortBy(_._1).map((_, r, p) => r -> p).toList

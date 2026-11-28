package com.histmersion
package domain.catalogue.places.placereview

import com.histmersion.domain.*
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.placereview.LinkedItem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.Geocode
import com.histmersion.domain.catalogue.places.PlaceReview.periodText
import com.histmersion.domain.catalogue.places.placereview.Proposal
import com.histmersion.domain.catalogue.places.PlaceReview.WikidataClasses
import com.histmersion.Period
import com.histmersion.domain.catalogue.places.PlaceReview.ExtantYear
import com.histmersion.domain.catalogue.places.PlaceReview.PolityTypes
import com.histmersion.domain.catalogue.places.PlaceReview.Rationale
import com.histmersion.domain.catalogue.places.placereview.Candidate
import com.histmersion.domain.catalogue.places.PlaceReview.PeopleTypes
import com.histmersion.domain.catalogue.places.PlaceReview.SiteTypes
import com.histmersion.domain.catalogue.places.PlaceReview.WikidataPolity

final case class Evidence(
    id: Evidence.Id,
    label: Evidence.Label,
    kind: Place.Kind.Wire,
    featureTypes: List[Evidence.FeatureTypes],
    timePeriods: List[Evidence.TimePeriods],
    start: Option[DeepTime.SignedYear],
    end: Option[DeepTime.SignedYear],
    items: List[LinkedItem] = Nil,
    descriptions: List[Evidence.Descriptions] = Nil
)

object Evidence:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type FeatureTypes = FeatureTypes.T
  object FeatureTypes extends Newtype[String, Pure]
  type TimePeriods = TimePeriods.T
  object TimePeriods extends Newtype[String, Pure]
  type Descriptions = Descriptions.T
  object Descriptions extends Newtype[String, Pure]

  private val Withdrawn = "(?i)^withdrawn\\b.*".r

  private val RegionAside = "(?i).*\\([^)]*\\bregion\\b[^)]*\\).*".r

  private val WithdrawnDuplicate = "(?i)^withdrawn\\s*\\(?duplicate\\)?\\s*:\\s*(.+)$".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def duplicateOf(label: Evidence.Label): Option[Evidence.Label] = label.value.trim match
    case WithdrawnDuplicate(x) => Some(x.trim).filter(_.nonEmpty).map(Evidence.Label(_))
    case _                     => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def duplicateKeys(name: Evidence.Label): List[Geocode.Key] =
    (Geocode.key(name.value) :: name.value.split("/").toList.map(Geocode.key)).filter(_.value.nonEmpty).distinct

  extension (e: Evidence)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def evidenceOf: List[Proposal.EvidenceLine] =
      val types = Option.when(e.featureTypes.nonEmpty)(s"Pleiades types: ${e.featureTypes.mkString(", ")}")
      val tps = Option.when(e.timePeriods.nonEmpty)(s"Pleiades periods: ${e.timePeriods.mkString(", ")}")
      val items = e.items.flatMap(i =>
        i.itemClass.map(c =>
          s"${i.ref}: ${WikidataClasses.get(c).fold(c.value)(_.value)}" + i.period.fold("")(p => s" ${periodText(p)}")
        )
      )
      val desc = e.descriptions.headOption.map(d => s"described as \"$d\"")
      (types.toList ++ tps.toList ++ items ++ desc.toList).map(Proposal.EvidenceLine(_))

  extension (e: Evidence)
    def polityPeriod: Option[Period] =
      val own = for s <- e.start; t <- e.end if t < ExtantYear && s <= t; p <- DeepTime.span(s, t) yield p
      own.orElse(e.items.flatMap(_.period).headOption)

  extension (e: Evidence)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def propose(candidates: List[Candidate] = Nil): Option[Proposal] =
      val types = e.featureTypes.map(t => Evidence.FeatureTypes(t.value.trim.toLowerCase(java.util.Locale.ROOT))).toSet
      val ev = e.evidenceOf
      def to(kind: Place.Kind.Wire, reason: Rationale, period: Option[Period] = None) =
        Option.when(kind != e.kind || period.isDefined)(
          Proposal.Reclassify(e.id, e.label, e.kind, kind, period, reason, ev)
        )
      duplicateOf(e.label) match
        case Some(x) =>
          candidates.filter(_.id != e.id) match
            case List(c) =>
              Some(
                Proposal.Merge(
                  e.id,
                  e.label,
                  c.id,
                  c.label,
                  Rationale(s"withdrawn by Pleiades as a duplicate of '$x'"),
                  ev
                )
              )
            case Nil =>
              Some(
                Proposal.Review(
                  e.id,
                  e.label,
                  Rationale(s"withdrawn by Pleiades as a duplicate of '$x'; no seed place has that name"),
                  ev
                )
              )
            case cs =>
              Some(
                Proposal.Review(
                  e.id,
                  e.label,
                  Rationale(
                    s"withdrawn by Pleiades as a duplicate of '$x'; ${cs.size} seed places have that name (${cs.map(_.id).mkString(", ")})"
                  ),
                  ev
                )
              )
        case None if Withdrawn.matches(e.label.value.trim) =>
          Some(
            Proposal.Review(
              e.id,
              e.label,
              Rationale("withdrawn by Pleiades, with no replacement named"),
              ev
            )
          )
        case None if e.kind.value == Place.Kind.Feature => None
        case None                                       =>
          val settled = types.intersect(SiteTypes)
          // a SITE that is also a settlement stays one (a provincial capital); otherwise an
          // administrative or a people type wins over the kind the seed gave it
          val keepSite = e.kind.value == Place.Kind.Site && settled.nonEmpty
          val admin = types.intersect(PolityTypes).filter(_ => !keepSite)
          if admin.nonEmpty then
            val period = e.polityPeriod
            val stated = (e.start, e.end)
            val note = period match
              case Some(p) if !(stated._1.contains(p.start) && stated._2.contains(p.end)) =>
                "an administrative unit (a polity); the period from the linked Wikidata item"
              case Some(_) => "an administrative unit (a polity)"
              case None    => "an administrative unit (a polity); no source states its period — add one by hand"
            // a polity whose own period is already the stated one needs no period write
            val write = period.filterNot(p => stated._1.contains(p.start) && stated._2.contains(p.end))
            if e.kind.value == Place.Kind.Polity && write.isEmpty then None
            else
              Some(
                Proposal
                  .Reclassify(
                    e.id,
                    e.label,
                    e.kind,
                    Place.Kind.Wire(Place.Kind.Polity),
                    write,
                    Rationale(note),
                    ev
                  )
              )
          else if types.intersect(PeopleTypes).nonEmpty && !keepSite then
            to(
              Place.Kind.Wire(Place.Kind.People),
              Rationale("a people or civic tribe — a group of persons, not a territory")
            )
          else if settled.nonEmpty && !RegionAside.matches(e.label.value) then
            to(
              Place.Kind.Wire(Place.Kind.Site),
              Rationale(s"a settlement (${settled.toList.sorted.mkString(", ")}), not a region")
            )
          else if e.kind.value != Place.Kind.Polity && e.items.exists(_.itemClass.contains(WikidataPolity)) then
            // Wikidata files Brittany and Messenia as historical countries too: evidence, not a verdict
            Some(
              Proposal.Review(
                e.id,
                e.label,
                Rationale("the linked Wikidata item is a historical country — a polity, or a region named after one"),
                ev
              )
            )
          else None

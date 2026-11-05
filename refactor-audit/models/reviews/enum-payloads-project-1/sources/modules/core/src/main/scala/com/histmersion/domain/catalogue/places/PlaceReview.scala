package com.histmersion.domain.catalogue.places

import com.histmersion.domain.*

import com.histmersion.api.YearFmt
import com.histmersion.api.catalogue.Geocode
import com.histmersion.Period
import com.histmersion.domain.catalogue.Place

/** What a point-only seed region IS, from what its sources say (`lint --rule place-kind`, 2026-10-09). Pleiades files a
  * people, a civic tribe, a province, a deme and a district alike as a "region" with one representative point; this
  * reads the evidence — Pleiades' feature types, the class and dates of the Wikidata item a place is linked to, a
  * "Withdrawn (duplicate)" title — and proposes what the place should be. Pure: `db.PlaceReviewRepo` fetches the
  * evidence and applies a fix.
  *
  * It reads every seed point whose kind is an area (region, polity, people), every seed "site" whose only Pleiades
  * types are administrative or a people (Pleiades' `province-2` has no settlement kind, so the seed filed "Thessalia
  * (late Roman province)" as a site), and every place Pleiades withdrew. The rules, first match wins:
  *   1. a title "Withdrawn (duplicate): X" (or "Withdrawn duplicate: X") → MERGE into the one seed place called X (by
  *      [[Geocode.key]], so "Creta et Cyrene (Roman province)" is X); none or several → listed for review;
  *   2. another "Withdrawn…" title → review (Pleiades retired it and names no replacement);
  *   3. an administrative type (province, satrapy, Augustan regio, state, diocese) → `polity` (a site that is also a
  *      settlement stays a site: a provincial capital), with a period: the place's own when both ends are stated and
  *      the end is not Pleiades' "still extant" 2100, else the linked Wikidata item's, else none (said so);
  *   4. the type `people` → `people`: a people or civic tribe is a group of persons, not a territory;
  *   5. a settlement type (settlement, deme) → `site` (Peiraieus is a port town and deme), unless the label itself says
  *      region ("Salmydessos (coastal region)");
  *   6. a linked Wikidata item classed as a historical country → listed for review: Wikidata files Brittany and
  *      Messenia as historical countries too.
  * Anything else is left as it is.
  */
object PlaceReview:

  /** Pleiades types that make a place an administrative unit. */
  type PolityTypes = PolityTypes.T
  object PolityTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeopleTypes = PeopleTypes.T
  object PeopleTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteTypes = SiteTypes.T
  object SiteTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtantYear = ExtantYear.T
  object ExtantYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataClassesPart1 = WikidataClassesPart1.T
  object WikidataClassesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataClassesPart2 = WikidataClassesPart2.T
  object WikidataClassesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataPolity = WikidataPolity.T
  object WikidataPolity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicateOfResult = DuplicateOfResult.T
  object DuplicateOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicateOfLabel = DuplicateOfLabel.T
  object DuplicateOfLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicateKeysResult = DuplicateKeysResult.T
  object DuplicateKeysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DuplicateKeysName = DuplicateKeysName.T
  object DuplicateKeysName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvidenceOfResult = EvidenceOfResult.T
  object EvidenceOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val PolityTypes: Set[com.histmersion.domain.catalogue.places.PlaceReview.PolityTypes] = Set("province", "province-2", "satrapy", "regio-augusti", "state", "diocese")

  /** Pleiades types that make a place a people. */
  val PeopleTypes: Set[com.histmersion.domain.catalogue.places.PlaceReview.PeopleTypes] = Set("people")

  /** Pleiades types that make a "region" a settlement. */
  val SiteTypes: Set[com.histmersion.domain.catalogue.places.PlaceReview.SiteTypes] = Set("settlement", "settlement-modern", "deme-attic")

  /** Pleiades' "still extant" upper bound (`maxDate = 2100`). */
  val ExtantYear: com.histmersion.domain.catalogue.places.PlaceReview.ExtantYear = 2100L

  /** The Wikidata classes the extract selects places by (`db/gazetteer`), in words. */
  val WikidataClasses: Map[com.histmersion.domain.catalogue.places.PlaceReview.WikidataClassesPart1, com.histmersion.domain.catalogue.places.PlaceReview.WikidataClassesPart2] = Map(
    "Q3024240" -> "historical country",
    "Q1620908" -> "historical region",
    "Q486972" -> "human settlement",
    "Q15661340" -> "ancient city",
    "Q839954" -> "archaeological site"
  )
  val WikidataPolity: com.histmersion.domain.catalogue.places.PlaceReview.WikidataPolity = "Q3024240"

  private val Withdrawn = "(?i)^withdrawn\\b.*".r

  /** A label that says the place is a region ("Salmydessos (coastal region)"). */
  private val RegionAside = "(?i).*\\([^)]*\\bregion\\b[^)]*\\).*".r
  private val WithdrawnDuplicate = "(?i)^withdrawn\\s*\\(?duplicate\\)?\\s*:\\s*(.+)$".r

  /** A Wikidata (or other) record linked to the place: its id, its class, and its dates. */
  final case class LinkedItem(ref: LinkedItem.Ref, itemClass: Option[LinkedItem.ItemClass], period: Option[Period])

  object LinkedItem:
    type Ref = Ref.T
    object Ref extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemClass = ItemClass.T
    object ItemClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What is known about one point-only seed place. `featureTypes`/`timePeriods` are Pleiades'; `start`/`end` the
    * stored period (`None` = open on that side).
    */
  final case class Evidence(
      id: Evidence.Id,
      label: Evidence.Label,
      kind: Evidence.Kind,
      featureTypes: List[Evidence.FeatureTypes],
      timePeriods: List[Evidence.TimePeriods],
      start: Option[Evidence.Start],
      end: Option[Evidence.End],
      items: List[LinkedItem] = Nil,
      descriptions: List[Evidence.Descriptions] = Nil
  )

  object Evidence:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FeatureTypes = FeatureTypes.T
    object FeatureTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TimePeriods = TimePeriods.T
    object TimePeriods extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Descriptions = Descriptions.T
    object Descriptions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A seed place a withdrawn duplicate could be merged into. */
  final case class Candidate(id: Candidate.Id, label: Candidate.Label, kind: Candidate.Kind)

  object Candidate:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What to do with one place. */
  enum Proposal:
    /** Change the kind (and, for a polity, the period). */
    case Reclassify(
        id: Reclassify.Id,
        label: Reclassify.Label,
        from: Reclassify.From,
        to: Reclassify.To,
        period: Option[Period],
        reason: Reclassify.Reason,
        evidence: List[Reclassify.Evidence]
    )

    /** Move the place's findings, links and children to `into`, delete it, keep its id as an alias. */
    case Merge(id: Merge.Id, label: Merge.Label, into: Merge.Into, intoLabel: Merge.IntoLabel, reason: Merge.Reason, evidence: List[Merge.Evidence])

    /** Nothing automatic: a person decides. */
    case Review(id: Review.Id, label: Review.Label, reason: Review.Reason, evidence: List[Review.Evidence])

    def id: com.histmersion.domain.catalogue.places.PlaceReview.Proposal.IdResult

    /** One line: id, label, current → proposed kind, why, and the evidence. */
    def describe: com.histmersion.domain.catalogue.places.PlaceReview.Proposal.DescribeResult = s"$id $detail"

    /** The line without the id — a lint item, which carries the id beside it. */
    def detail: com.histmersion.domain.catalogue.places.PlaceReview.Proposal.DetailResult =
      def ev(e: List[String]) = if e.isEmpty then "" else e.mkString(" [", "; ", "]")
      this match
        case Reclassify(_, label, from, to, period, reason, evidence) =>
          val per =
            period.fold("")(p => s", period ${YearFmt.period(Some(p.start.value.toLong), Some(p.end.value.toLong))}")
          s"$label: $from → $to$per — $reason${ev(evidence)}"
        case Merge(_, label, into, intoLabel, reason, evidence) =>
          s"$label: merge into $into $intoLabel — $reason${ev(evidence)}"
        case Review(_, label, reason, evidence) =>
          s"$label: review — $reason${ev(evidence)}"

    /** Does `--fix` act on it? */
    def fixable: com.histmersion.domain.catalogue.places.PlaceReview.Proposal.FixableResult = this match
      case _: Review => false
      case _         => true

  object Proposal:
    object Reclassify:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Label = Label.T
      object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type From = From.T
      object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type To = To.T
      object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Evidence = Evidence.T
      object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Merge:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Label = Label.T
      object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Into = Into.T
      object Into extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type IntoLabel = IntoLabel.T
      object IntoLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Evidence = Evidence.T
      object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Review:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Label = Label.T
      object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Evidence = Evidence.T
      object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type IdResult = IdResult.T
    object IdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DetailResult = DetailResult.T
    object DetailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FixableResult = FixableResult.T
    object FixableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The duplicate's name, when the title says "Withdrawn (duplicate): X". */
  def duplicateOf(label: com.histmersion.domain.catalogue.places.PlaceReview.DuplicateOfLabel): Option[com.histmersion.domain.catalogue.places.PlaceReview.DuplicateOfResult] = label.trim match
    case WithdrawnDuplicate(x) => Some(x.trim).filter(_.nonEmpty)
    case _                     => None

  /** The name keys a withdrawn duplicate's replacement may carry: the whole name's, and each of the alternatives a
    * Pleiades title joins with '/' ("Peiros/Piros (river)" → "Peiros (river)").
    */
  def duplicateKeys(name: com.histmersion.domain.catalogue.places.PlaceReview.DuplicateKeysName): List[com.histmersion.domain.catalogue.places.PlaceReview.DuplicateKeysResult] =
    (Geocode.key(name) :: name.split("/").toList.map(Geocode.key)).filter(_.nonEmpty).distinct

  /** The evidence lines every proposal carries. */
  def evidenceOf(e: Evidence): List[com.histmersion.domain.catalogue.places.PlaceReview.EvidenceOfResult] =
    val types = Option.when(e.featureTypes.nonEmpty)(s"Pleiades types: ${e.featureTypes.mkString(", ")}")
    val tps = Option.when(e.timePeriods.nonEmpty)(s"Pleiades periods: ${e.timePeriods.mkString(", ")}")
    val items = e.items.flatMap(i =>
      i.itemClass.map(c =>
        s"${i.ref}: ${WikidataClasses.getOrElse(c, c)}" + i.period.fold("")(p =>
          s" ${YearFmt.period(Some(p.start.value.toLong), Some(p.end.value.toLong))}"
        )
      )
    )
    val desc = e.descriptions.headOption.map(d => s"described as \"$d\"")
    types.toList ++ tps.toList ++ items ++ desc.toList

  /** The period a polity gets: its own when both ends are stated and it is not "still extant", else a linked item's,
    * else none.
    */
  def polityPeriod(e: Evidence): Option[Period] =
    val own = for s <- e.start; t <- e.end if t < ExtantYear && s <= t; p <- DeepTime.span(s, t) yield p
    own.orElse(e.items.flatMap(_.period).headOption)

  /** The proposal for one place (`None` = leave it). `candidates` are the seed places a withdrawn duplicate's name
    * resolves to (empty otherwise). Pure.
    */
  def propose(e: Evidence, candidates: List[Candidate] = Nil): Option[Proposal] =
    val types = e.featureTypes.map(_.trim.toLowerCase(java.util.Locale.ROOT)).toSet
    val ev = evidenceOf(e)
    def to(kind: String, reason: String, period: Option[Period] = None) =
      Option.when(kind != e.kind || period.isDefined)(
        Proposal.Reclassify(e.id, e.label, e.kind, kind, period, reason, ev)
      )
    duplicateOf(e.label) match
      case Some(x) =>
        candidates.filter(_.id != e.id) match
          case List(c) =>
            Some(Proposal.Merge(e.id, e.label, c.id, c.label, s"withdrawn by Pleiades as a duplicate of '$x'", ev))
          case Nil =>
            Some(
              Proposal.Review(
                e.id,
                e.label,
                s"withdrawn by Pleiades as a duplicate of '$x'; no seed place has that name",
                ev
              )
            )
          case cs =>
            Some(
              Proposal.Review(
                e.id,
                e.label,
                s"withdrawn by Pleiades as a duplicate of '$x'; ${cs.size} seed places have that name (${cs.map(_.id).mkString(", ")})",
                ev
              )
            )
      case None if Withdrawn.matches(e.label.trim) =>
        Some(Proposal.Review(e.id, e.label, "withdrawn by Pleiades, with no replacement named", ev))
      case None if e.kind == Place.Kind.Feature => None
      case None                                 =>
        val settled = types.intersect(SiteTypes)
        // a SITE that is also a settlement stays one (a provincial capital); otherwise an
        // administrative or a people type wins over the kind the seed gave it
        val keepSite = e.kind == Place.Kind.Site && settled.nonEmpty
        val admin = types.intersect(PolityTypes).filter(_ => !keepSite)
        if admin.nonEmpty then
          val period = polityPeriod(e)
          val stated = (e.start, e.end)
          val note = period match
            case Some(p) if !(stated._1.contains(p.start) && stated._2.contains(p.end)) =>
              "an administrative unit (a polity); the period from the linked Wikidata item"
            case Some(_) => "an administrative unit (a polity)"
            case None    => "an administrative unit (a polity); no source states its period — add one by hand"
          // a polity whose own period is already the stated one needs no period write
          val write = period.filterNot(p => stated._1.contains(p.start) && stated._2.contains(p.end))
          if e.kind == Place.Kind.Polity && write.isEmpty then None
          else Some(Proposal.Reclassify(e.id, e.label, e.kind, Place.Kind.Polity, write, note, ev))
        else if types.intersect(PeopleTypes).nonEmpty && !keepSite then
          to(Place.Kind.People, "a people or civic tribe — a group of persons, not a territory")
        else if settled.nonEmpty && !RegionAside.matches(e.label) then
          to(Place.Kind.Site, s"a settlement (${settled.toList.sorted.mkString(", ")}), not a region")
        else if e.kind != Place.Kind.Polity && e.items.exists(_.itemClass.contains(WikidataPolity)) then
          // Wikidata files Brittany and Messenia as historical countries too: evidence, not a verdict
          Some(
            Proposal.Review(
              e.id,
              e.label,
              "the linked Wikidata item is a historical country — a polity, or a region named after one",
              ev
            )
          )
        else None

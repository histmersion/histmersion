package com.histmersion
package domain.catalogue.events
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.domain.DeepTime.SignedYear

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{CatalogRecord, EventTaxonomy}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.lint.{tolerated, LintRule}

/** The rules the host's event import runs every catalogue's records through (#1449) — the thresholds, the parts read
  * under them — and Wikidata's `wbgetentities`, which the host asks for the labels and aliases of Wikidata's events and
  * the classes of the sites on the map. The catalogues themselves are plugins. Pure.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object EventProviders:
  type WikidataProperty = WikidataProperty.T
  object WikidataProperty extends Newtype[String, Pure]

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  object WikidataEvents:

    /** Pure: the kind an item read in two classes keeps — the one whose class comes first in `order` (the configured
      * classes, the most specific first: a naval battle is also a battle, and stays a naval battle whichever class is
      * read last); a kind not in `order` yields to one that is.
      */

    def moreSpecific(order: List[EventSummary.Kind], a: EventSummary.Kind, b: EventSummary.Kind): EventSummary.Kind =
      def rank(k: EventSummary.Kind) = order.indexOf(EventTaxonomy.canonical(k)) match
        case -1 => Int.MaxValue
        case i  => i
      if rank(b) < rank(a) then b else a

    /** Pure: the `wbgetentities` URL for the labels and aliases of up to 50 items in `langs`. */
    def namesUrl(api: String, ids: List[String], langs: List[String]): String =
      s"$api?action=wbgetentities&format=json&formatversion=2&props=labels%7Caliases" +
        s"&languages=${enc(langs.mkString("|"))}&ids=${enc(ids.mkString("|"))}"

    /** Pure: each item's labels and aliases in the asked languages, distinct; None when Wikidata answered with an error
      * object instead (no caller reads its words: a batch that fails adds no names).
      */
    def names(json: Json): Option[Map[String, List[String]]] =
      val c = json.cursor
      c.downField("error").focus match
        case Some(_) => None
        case None    =>
          val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
          Some(ents.map { (id, e) =>
            val labels = e.cursor
              .downField("labels")
              .focus
              .flatMap(_.asObject)
              .toList
              .flatMap(_.values)
              .flatMap(_.cursor.get[String]("value").toOption)
            val aliases = e.cursor
              .downField("aliases")
              .focus
              .flatMap(_.asObject)
              .toList
              .flatMap(_.values)
              .flatMap(_.asArray.toList.flatten)
              .flatMap(_.cursor.get[String]("value").toOption)
            id -> (labels ++ aliases).map(_.trim).filter(_.nonEmpty).distinct
          }.toMap)

    /** Pure: the P31 classes of each item of a `wbgetentities` (props=claims) answer — what a site's type is refined
      * from ([[SiteTypes.wikidata]]); P279 likewise for a class.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def claims(json: Json, property: WikidataProperty): Option[Map[String, List[String]]] =
      val c = json.cursor
      c.downField("error").focus match
        case Some(_) => None
        case None    =>
          val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
          Some(ents.map { (id, e) =>
            val sts = e.cursor.downField("claims").downField(property.value).focus.flatMap(_.asArray).toList.flatten
            id -> sts
              .filterNot(_.cursor.get[String]("rank").toOption.contains("deprecated"))
              .flatMap(st =>
                st.cursor.downField("mainsnak").downField("datavalue").downField("value").get[String]("id").toOption
              )
          }.toMap)

  /** Pure: does a record pass the configured thresholds? Inside `region` (the world by default) everything (at the
    * region's own minimum); outside a narrower one an eruption needs `outsideVei`, an earthquake `outsideMagnitude`. A
    * record with no position never passes (it cannot be drawn or matched in space). `Left` is why not.
    */
  final case class Thresholds(
      region: Option[BBox],
      regionVei: Thresholds.RegionVei,
      outsideVei: Thresholds.OutsideVei,
      regionMagnitude: Thresholds.RegionMagnitude,
      outsideMagnitude: Thresholds.OutsideMagnitude,
      area: Option[BBox] = None
  )

  object Thresholds:
    type RegionVei = RegionVei.T
    object RegionVei extends Newtype[Int, Pure]
    type OutsideVei = OutsideVei.T
    object OutsideVei extends Newtype[Int, Pure]
    type RegionMagnitude = RegionMagnitude.T
    object RegionMagnitude extends Newtype[Double, Pure]
    type OutsideMagnitude = OutsideMagnitude.T
    object OutsideMagnitude extends Newtype[Double, Pure]

  /** #6561: what a read part was kept under. A part is recorded as read together with the thresholds that judged its
    * records, so widening the region (or lowering a VEI or magnitude floor) makes every part due again instead of
    * skipping it as read — its records were judged by the old rule.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def scope(t: Thresholds): String =
    def box(b: BBox) = s"${b.minLon},${b.minLat},${b.maxLon},${b.maxLat}"
    s"region=${t.region.fold("world")(box)};vei=${t.regionVei}/${t.outsideVei};" +
      s"mag=${t.regionMagnitude}/${t.outsideMagnitude};area=${t.area.fold("all")(box)}"

  /** The key a part is recorded under in `scope`. */
  def scopedPart(part: String, scope: String): String = s"$part @ $scope"

  /** The parts recorded as read in `scope` (keys written under another scope, or before scopes, are not). */
  def partsIn(recorded: Set[String], scope: String): Set[String] =
    val suffix = s" @ $scope"
    recorded.collect { case k if k.endsWith(suffix) => k.dropRight(suffix.length) }

  /** Why a record is left out of an import: the account of the judgement, not a failure. */
  enum Rationale:
    /** It lies outside the `--bbox` asked for. */
    case OutsideArea

    /** An eruption below the region's VEI floor `min`. */
    case VeiBelowRegion(min: Thresholds.RegionVei)

    /** An eruption outside the region, below the VEI floor `min` there. */
    case VeiBelowOutside(min: Thresholds.OutsideVei)

    /** An earthquake below the region's magnitude floor `min`. */
    case MagnitudeBelowRegion(min: Thresholds.RegionMagnitude)

    /** An earthquake outside the region, below the magnitude floor `min` there. */
    case MagnitudeBelowOutside(min: Thresholds.OutsideMagnitude)

    /** It has no position: it cannot be drawn or matched in space. */
    case NoPosition

    /** It starts after `to`, the last year asked for. */
    case After(to: SignedYear)

    /** It ends before `from`, the first year asked for. */
    case Before(from: SignedYear)

    /** The reason as the import's report counts it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case OutsideArea                => "outside --bbox"
      case VeiBelowRegion(min)        => s"VEI below $min"
      case VeiBelowOutside(min)       => s"VEI below $min outside the region"
      case MagnitudeBelowRegion(min)  => s"magnitude below $min"
      case MagnitudeBelowOutside(min) => s"magnitude below $min outside the region"
      case NoPosition                 => "no position"
      case After(to)                  => s"after $to"
      case Before(from)               => s"before $from"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def keep(r: CatalogRecord, t: Thresholds): Either[Rationale, Unit] =
    (r.lat, r.lon) match
      case (Some(la), Some(lo)) =>
        def in(b: BBox) = b.contains(lo, la)
        val inRegion = t.region.forall(in)
        if t.area.exists(b => !in(b)) then Left(Rationale.OutsideArea)
        else
          r.kind.value match
            case EventTaxonomy.Eruption =>
              val min = if inRegion then t.regionVei.value else t.outsideVei.value
              if min > 0 && r.vei.forall(_.value < min) then
                Left(
                  if inRegion then Rationale.VeiBelowRegion(t.regionVei) else Rationale.VeiBelowOutside(t.outsideVei)
                )
              else Right(())
            case EventTaxonomy.Earthquake =>
              val min = if inRegion then t.regionMagnitude.value else t.outsideMagnitude.value
              if min > 0 && r.magnitude.forall(_.value < min) then
                Left(
                  if inRegion then Rationale.MagnitudeBelowRegion(t.regionMagnitude)
                  else Rationale.MagnitudeBelowOutside(t.outsideMagnitude)
                )
              else Right(())
            case _ => Right(())
      case _ => Left(Rationale.NoPosition)

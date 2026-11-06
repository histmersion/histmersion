package com.histmersion.domain.catalogue.events

import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{CatalogRecord, EventTaxonomy}

import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** The rules the host's event import runs every catalogue's records through (#1449) — the thresholds, the parts read
  * under them — and Wikidata's `wbgetentities`, which the host asks for the labels and aliases of Wikidata's events and
  * the classes of the sites on the map. The catalogues themselves are plugins. Pure.
  */
object EventProviders:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikidataProperty = WikidataProperty.T
  object WikidataProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  object WikidataEvents:
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    /** Pure: the kind an item read in two classes keeps — the one whose class comes first in `order` (the configured
      * classes, the most specific first: a naval battle is also a battle, and stays a naval battle whichever class is
      * read last); a kind not in `order` yields to one that is.
      */


    def moreSpecific(order: List[String], a: String, b: String): String =
      def rank(k: String) = order.indexOf(EventTaxonomy.canonical(k)) match
        case -1 => Int.MaxValue
        case i  => i
      if rank(b) < rank(a) then b else a

    /** Pure: the `wbgetentities` URL for the labels and aliases of up to 50 items in `langs`. */
    def namesUrl(api: String, ids: List[String], langs: List[String]): String =
      s"$api?action=wbgetentities&format=json&formatversion=2&props=labels%7Caliases" +
        s"&languages=${enc(langs.mkString("|"))}&ids=${enc(ids.mkString("|"))}"

    /** Pure: each item's labels and aliases in the asked languages, distinct. */
    def names(json: Json): Either[com.histmersion.domain.catalogue.events.EventProviders.WikidataEvents.Problem, Map[String, List[String]]] =
      val c = json.hcursor
      c.downField("error").focus match
        case Some(err) => Left(err.hcursor.get[String]("info").getOrElse("error"))
        case None      =>
          val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
          Right(ents.map { (id, e) =>
            val labels = e.hcursor
              .downField("labels")
              .focus
              .flatMap(_.asObject)
              .toList
              .flatMap(_.values)
              .flatMap(_.hcursor.get[String]("value").toOption)
            val aliases = e.hcursor
              .downField("aliases")
              .focus
              .flatMap(_.asObject)
              .toList
              .flatMap(_.values)
              .flatMap(_.asArray.toList.flatten)
              .flatMap(_.hcursor.get[String]("value").toOption)
            id -> (labels ++ aliases).map(_.trim).filter(_.nonEmpty).distinct
          }.toMap)

    /** Pure: the P31 classes of each item of a `wbgetentities` (props=claims) answer — what a site's type is refined
      * from ([[SiteTypes.wikidata]]); P279 likewise for a class.
      */
    def claims(json: Json, property: com.histmersion.domain.catalogue.events.EventProviders.WikidataProperty): Either[com.histmersion.domain.catalogue.events.EventProviders.WikidataEvents.Problem, Map[String, List[String]]] =
      val c = json.hcursor
      c.downField("error").focus match
        case Some(err) => Left(err.hcursor.get[String]("info").getOrElse("error"))
        case None      =>
          val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
          Right(ents.map { (id, e) =>
            val sts = e.hcursor.downField("claims").downField(property).focus.flatMap(_.asArray).toList.flatten
            id -> sts
              .filterNot(_.hcursor.get[String]("rank").toOption.contains("deprecated"))
              .flatMap(st =>
                st.hcursor.downField("mainsnak").downField("datavalue").downField("value").get[String]("id").toOption
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
    object RegionVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutsideVei = OutsideVei.T
    object OutsideVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RegionMagnitude = RegionMagnitude.T
    object RegionMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OutsideMagnitude = OutsideMagnitude.T
    object OutsideMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** #6561: what a read part was kept under. A part is recorded as read together with the thresholds that judged its
    * records, so widening the region (or lowering a VEI or magnitude floor) makes every part due again instead of
    * skipping it as read — its records were judged by the old rule.
    */
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

  def keep(r: CatalogRecord, t: Thresholds): Either[com.histmersion.domain.catalogue.events.EventProviders.Problem, Unit] =
    (r.lat, r.lon) match
      case (Some(la), Some(lo)) =>
        def in(b: BBox) =
          la >= b.minLat && la <= b.maxLat &&
            (if b.crossesAntimeridian then lo >= b.minLon || lo <= b.maxLon else lo >= b.minLon && lo <= b.maxLon)
        val inRegion = t.region.forall(in)
        if t.area.exists(b => !in(b)) then Left(com.histmersion.domain.catalogue.events.EventProviders.Problem("outside --bbox"))
        else
          r.kind match
            case EventTaxonomy.Eruption =>
              val min = if inRegion then t.regionVei else t.outsideVei
              if min > 0 && r.vei.forall(_ < min) then
                Left(s"VEI below $min${if inRegion then "" else " outside the region"}")
              else Right(())
            case EventTaxonomy.Earthquake =>
              val min = if inRegion then t.regionMagnitude else t.outsideMagnitude
              if min > 0 && r.magnitude.forall(_ < min) then
                Left(s"magnitude below $min${if inRegion then "" else " outside the region"}")
              else Right(())
            case _ => Right(())
      case _ => Left(com.histmersion.domain.catalogue.events.EventProviders.Problem("no position"))

package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.basemap.Basemap

/** What a person downloads of the base map from the About window, as data: the regions to pick from, the fetch a choice
  * asks for, and the dry run's sizes zoom by zoom.
  */
object MapRegions:

  /** What a region's download holds. */
  enum Content(val label: String):
    /** Every layer of the map for the box: it takes the place of the region downloaded now. */
    case Map extends Content("the map, every layer (in place of the region downloaded now)")

    /** The relief from the extract's depth to the on-demand depth, kept beside the map until removed. */
    case Relief extends Content("the detailed relief, kept beside the map")

  object Content:
    def parse(s: String): Content = values.find(_.toString == s).getOrElse(Map)

  /** One entry of the region list: what it is called and its box (`None`: the box typed in). */
  final case class RegionChoice(id: String, label: String, bbox: Option[String])

  val OwnBox = "own"

  /** The configured region first — the whole world by default, then listed once, as itself. */
  def choices(s: Basemap.Status): List[RegionChoice] =
    val world = RegionChoice("world", "the whole world", Some("world"))
    val configured =
      if s.bbox.trim.equalsIgnoreCase("world") then
        world.copy(id = "configured", label = "the configured region (the whole world)")
      else RegionChoice("configured", s"the configured region (${s.bbox})", Some(s.bbox))
    List(configured) ++ s.regions.map(r => RegionChoice(r.name, s"${r.name} (${r.bbox})", Some(r.bbox))) ++
      Option.unless(configured.bbox == world.bbox)(world).toList ++ List(RegionChoice(OwnBox, "a box of my own", None))

  /** The fetch for a box, what it holds, the named region it is (if it is one) and the depth typed (blank: the
    * configured one). A relief download is a focus region: the named one, or one named after its box.
    */
  def request(
      box: String,
      content: Content,
      named: Option[String],
      maxZoom: String,
      dryRun: Boolean
  ): Either[String, Basemap.FetchQuery] =
    for
      b <- Basemap.validBBox(box.trim)
      z <- maxZoom.trim match
        case "" => Right(None)
        case t  =>
          t.toIntOption
            .filter(z => z >= 0 && z <= Basemap.MaxZoomCap)
            .map(Some(_))
            .toRight(s"the deepest zoom is a number from 0 to ${Basemap.MaxZoomCap}")
    yield content match
      case Content.Map    => Basemap.FetchQuery(bbox = Some(box.trim), maxZoom = z, dryRun = dryRun)
      case Content.Relief =>
        Basemap.FetchQuery(
          layer = Basemap.Layer.Terrain.wire,
          bbox = Some(box.trim),
          terrainMaxZoom = z,
          dryRun = dryRun,
          focus = Some(named.getOrElse(Basemap.focusNameOf(b)))
        )

  /** One layer of a dry run's answer: its line, and what each zoom would download. */
  final case class LayerSizes(line: String, zooms: List[String])

  /** A fetch's answer, layer by layer (`; ` between them; a layer's sizes by zoom follow it as
    * `by zoom — zoom 0: … · zoom 1: …`).
    */
  def sizes(answer: String): List[LayerSizes] =
    answer.split("; ").toList.map(_.trim).filter(_.nonEmpty).foldLeft(List.empty[LayerSizes]) { (acc, part) =>
      if part.startsWith("by zoom — ") && acc.nonEmpty then
        acc.init :+ acc.last.copy(zooms = part.stripPrefix("by zoom — ").split(" · ").toList.map(_.trim))
      else acc :+ LayerSizes(part, Nil)
    }

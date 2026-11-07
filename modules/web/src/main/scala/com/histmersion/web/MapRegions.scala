package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.basemap.Basemap

/** What a person downloads of the base map from the About window, as data: the regions to pick from, the fetch a choice
  * asks for, and the dry run's sizes zoom by zoom.
  */
object MapRegions:
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizesText = SizesText.T
  object SizesText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomText = ZoomText.T
  object ZoomText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What a region's download holds. */

  enum Content(val label: Content.Label):
    /** Every layer of the map for the box: it takes the place of the region downloaded now. */
    case Map extends Content(com.histmersion.web.MapRegions.Content.Label("the map, every layer (in place of the region downloaded now)"))

    /** The relief from the extract's depth to the on-demand depth, kept beside the map until removed. */
    case Relief extends Content(com.histmersion.web.MapRegions.Content.Label("the detailed relief, kept beside the map"))

  object Content:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.web.MapRegions.Content.Name): Content = values.find(_.toString == s).getOrElse(Map)

  /** One entry of the region list: what it is called and its box (`None`: the box typed in). */
  final case class RegionChoice(id: RegionChoice.Id, label: RegionChoice.Label, bbox: Option[RegionChoice.Bbox])

  object RegionChoice:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Bbox = Bbox.T
    object Bbox extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val OwnBox = "own"

  /** The configured region first — the whole world by default, then listed once, as itself. */
  def choices(s: Basemap.Status): List[RegionChoice] =
    val world = RegionChoice(com.histmersion.web.MapRegions.RegionChoice.Id("world"), com.histmersion.web.MapRegions.RegionChoice.Label("the whole world"), Some(com.histmersion.web.MapRegions.RegionChoice.Bbox("world")))
    val configured =
      if s.bbox.trim.equalsIgnoreCase("world") then
        world.modify(_.id).setTo("configured").modify(_.label).setTo("the configured region (the whole world)")
      else RegionChoice(com.histmersion.web.MapRegions.RegionChoice.Id("configured"), s"the configured region (${s.bbox})", Some(s.bbox))
    List(configured) ++ s.regions.map(r => RegionChoice(r.name, s"${r.name} (${r.bbox})", Some(r.bbox))) ++
      Option.unless(configured.bbox == world.bbox)(world).toList ++ List(RegionChoice(OwnBox, com.histmersion.web.MapRegions.RegionChoice.Label("a box of my own"), None))

  /** The fetch for a box, what it holds, the named region it is (if it is one) and the depth typed (blank: the
    * configured one). A relief download is a focus region: the named one, or one named after its box.
    */
  def request(
      box: com.histmersion.api.catalogue.MapArea.Bbox,
      content: Content,
      named: Option[com.histmersion.web.MapRegions.Name],
      maxZoom: com.histmersion.web.MapRegions.ZoomText,
      dryRun: com.histmersion.web.MapRegions.DryRun
  ): Either[com.histmersion.web.MapRegions.Name, Basemap.FetchQuery] =
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
  final case class LayerSizes(line: LayerSizes.Line, zooms: List[LayerSizes.Zooms])

  object LayerSizes:
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Zooms = Zooms.T
    object Zooms extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A fetch's answer, layer by layer (`; ` between them; a layer's sizes by zoom follow it as
    * `by zoom — zoom 0: … · zoom 1: …`).
    */
  def sizes(answer: com.histmersion.web.MapRegions.SizesText): List[LayerSizes] =
    answer.split("; ").toList.map(_.trim).filter(_.nonEmpty).foldLeft(List.empty[LayerSizes]) { (acc, part) =>
      if part.startsWith("by zoom — ") && acc.nonEmpty then
        acc.init :+ acc.last.copy(zooms = part.stripPrefix("by zoom — ").split(" · ").toList.map(_.trim))
      else acc :+ LayerSizes(part, Nil)
    }

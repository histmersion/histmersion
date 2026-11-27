package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.basemap.Basemap
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.MapArea
import com.histmersion.lint.{tolerated, LintRule}

/** What a person downloads of the base map from the About window, as data: the regions to pick from, the fetch a choice
  * asks for, and the dry run's sizes zoom by zoom.
  */
object MapRegions:
  type SizesText = SizesText.T
  object SizesText extends Newtype[String, Pure]
  type ZoomText = ZoomText.T
  object ZoomText extends Newtype[String, Pure]

  /** What a region's download holds. */

  enum Content(val label: Content.Label):
    /** Every layer of the map for the box: it takes the place of the region downloaded now. */
    case Map extends Content(Content.Label("the map, every layer (in place of the region downloaded now)"))

    /** The relief from the extract's depth to the on-demand depth, kept beside the map until removed. */
    case Relief extends Content(Content.Label("the detailed relief, kept beside the map"))

  object Content:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Name): Content = values.find(_.toString == s.value).getOrElse(Map)

  /** One entry of the region list: what it is called and its box (`None`: the box typed in). */
  final case class RegionChoice(id: RegionChoice.Id, label: RegionChoice.Label, bbox: Option[BBox.Text])

  object RegionChoice:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)

  val OwnBox = "own"

  /** The configured region first — the whole world by default, then listed once, as itself. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def choices(s: Basemap.Status): List[RegionChoice] =
    val world = RegionChoice(RegionChoice.Id("world"), RegionChoice.Label("the whole world"), Some(BBox.Text("world")))
    val configured =
      if s.bbox.value.trim.equalsIgnoreCase("world") then
        world
          .modify(_.id)
          .setTo(RegionChoice.Id("configured"))
          .modify(_.label)
          .setTo(RegionChoice.Label("the configured region (the whole world)"))
      else
        RegionChoice(
          RegionChoice.Id("configured"),
          RegionChoice.Label(shown"the configured region (${s.bbox})"),
          Some(s.bbox)
        )
    List(configured) ++ s.regions.map(r =>
      RegionChoice(RegionChoice.Id(r.name.value), RegionChoice.Label(shown"${r.name} (${r.bbox})"), Some(r.bbox))
    ) ++
      Option.unless(configured.bbox == world.bbox)(world).toList ++ List(
        RegionChoice(RegionChoice.Id(OwnBox), RegionChoice.Label("a box of my own"), None)
      )

  /** The fetch for a box, what it holds, the named region it is (if it is one) and the depth typed (blank: the
    * configured one). A relief download is a focus region: the named one, or one named after its box.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def request(
      box: MapArea.Bbox,
      content: Content,
      named: Option[Basemap.Focus.Name],
      maxZoom: ZoomText,
      dryRun: Basemap.FetchQuery.DryRun
  ): Either[Problem, Basemap.FetchQuery] =
    val typed = box.value.trim
    for
      b <- Basemap.validBBox(Basemap.Focus.Bbox(typed))
      z <- maxZoom.value.trim match
        case "" => Right(None)
        case t  =>
          t.toIntOption
            .filter(z => z >= 0 && z <= Basemap.MaxZoomCap)
            .map(Some(_))
            .toRight(Problem.Unclassified(Legacy.Text(s"the deepest zoom is a number from 0 to ${Basemap.MaxZoomCap}")))
    yield content match
      case Content.Map =>
        Basemap.FetchQuery(
          bbox = Some(Basemap.FetchQuery.Bbox(typed)),
          maxZoom = z.map(Basemap.FetchQuery.MaxZoom(_)),
          dryRun = dryRun
        )
      case Content.Relief =>
        Basemap.FetchQuery(
          layer = Basemap.Layer.Terrain.wire,
          bbox = Some(Basemap.FetchQuery.Bbox(typed)),
          terrainMaxZoom = z.map(Basemap.FetchQuery.TerrainMaxZoom(_)),
          dryRun = dryRun,
          focus = Some(named.getOrElse(Basemap.focusNameOf(b)))
        )

  /** One layer of a dry run's answer: its line, and what each zoom would download. */
  final case class LayerSizes(line: LayerSizes.Line, zooms: List[LayerSizes.Zooms])

  object LayerSizes:
    type Line = Line.T
    object Line extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)
    type Zooms = Zooms.T
    object Zooms extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)

  /** A fetch's answer, layer by layer (`; ` between them; a layer's sizes by zoom follow it as
    * `by zoom — zoom 0: … · zoom 1: …`).
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sizes(answer: SizesText): List[LayerSizes] =
    answer.value.split("; ").toList.map(_.trim).filter(_.nonEmpty).foldLeft(List.empty[LayerSizes]) { (acc, part) =>
      if part.startsWith("by zoom — ") && acc.nonEmpty then
        acc.init :+ acc.last
          .copy(zooms = part.stripPrefix("by zoom — ").split(" · ").toList.map(z => LayerSizes.Zooms(z.trim)))
      else acc :+ LayerSizes(LayerSizes.Line(part), Nil)
    }

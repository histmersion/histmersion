package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.personal.{ItemKey, MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** What a bookmark or a note points at, from the command line: `<kind> [<id>]` and the flags of a point on the map, a
  * view or a period.
  */
@atBoundary(BoundaryKind.CliArgs, "parses the `<kind> [<id>]` target flags of bookmarks and notes")
object TargetFlags:
  type Views = Views.T
  object Views extends Newtype[Boolean, Pure]

  final case class Raw(
      kind: String,
      id: Option[String],
      lat: Option[Double],
      lon: Option[Double],
      zoom: Option[Double],
      from: Option[Int],
      to: Option[Int],
      view: Option[String]
  )

  /** The target the flags name, or why they name none. Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def target(r: Raw): Either[Cli.Error, Target] =
    def usage(why: String) = Cli.Error(Problem.Unclassified(Legacy.Text(why)))
    for
      kind <- ItemKind
        .parse(ItemKind.Wire(r.kind))
        .toRight(usage(s"kind '${r.kind}': one of ${ItemKind.values.map(_.wire).mkString(", ")}"))
      lat = r.lat.map(Latitude(_))
      lon = r.lon.map(Longitude(_))
      zoom = r.zoom.map(MapZoom(_))
    yield
      val base = if kind.isItem then Target.item(kind, ItemKey(r.id.getOrElse(""))) else Target(kind)
      base
        .modify(_.lat)
        .setTo(lat)
        .modify(_.lon)
        .setTo(lon)
        .modify(_.zoom)
        .setTo(zoom)
        .modify(_.from)
        .setTo(r.from.map(Year(_)))
        .modify(_.to)
        .setTo(r.to.map(Year(_)))
        .modify(_.view)
        .setTo(r.view.map(ViewState(_)))

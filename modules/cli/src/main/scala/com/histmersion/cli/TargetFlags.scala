package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.personal.{ItemKey, MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a bookmark or a note points at, from the command line: `<kind> [<id>]` and the flags of a point on the map, a
  * view or a period.
  */
object TargetFlags:
  type Views = Views.T
  object Views extends RefinedType[Boolean, Pure]



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

  /** `views`: the flags of a saved view (`--zoom`, `--view`) too. */
  def opts(kinds: Set[ItemKind], views: Views): Opts[Raw] =
    (
      Opts.argument[String](s"kind (${ItemKind.values.filter(kinds).map(_.wire).mkString(" | ")})"),
      Opts.argument[String]("id").orNone,
      Opts.option[Double]("lat", "A point's latitude, degrees north").orNone,
      Opts.option[Double]("lon", "A point's longitude, degrees east").orNone,
      if views.value then Opts.option[Double]("zoom", "A view's map zoom (2 a continent, 5 a country, 9 a city)").orNone
      else Opts(None),
      Opts.option[Int]("from", "The first year (BCE negative)").orNone,
      Opts.option[Int]("to", "The last year (BCE negative)").orNone,
      if views.value then Opts.option[String]("view", "A view's filters: what follows # in Explore's address").orNone
      else Opts(None)
    )
      .mapN(Raw.apply)

  /** The target the flags name, or why they name none. Pure. */
  def target(r: Raw): Either[Cli.Error, Target] =
    def usage(why: String) = Cli.Error(Cli.Error.Message(why), exitCode = ExitCode.Usage)
    for
      kind <- ItemKind
        .parse(ItemKind.Wire(r.kind))
        .toRight(usage(s"kind '${r.kind}': one of ${ItemKind.values.map(_.wire).mkString(", ")}"))
      lat = r.lat.map(Latitude(_))
      lon = r.lon.map(Longitude(_))
      zoom = r.zoom.map(MapZoom(_))
    yield
      val base = if kind.isItem then Target.item(kind, ItemKey(r.id.getOrElse(""))) else Target(kind)
      base.modify(
        _.lat).setTo(lat).modify(_.lon).setTo(lon).modify(_.zoom).setTo(zoom).modify(_.from).setTo(r.from.map(Year(_))).modify(_.to).setTo(r.to.map(Year(_))).modify(_.view).setTo(r.view.map(ViewState(_))
      )

package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.personal.{MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}

/** What a bookmark or a note points at, from the command line: `<kind> [<id>]` and the flags of a point on the map, a
  * view or a period.
  */
object TargetFlags:

  final case class Raw(
      kind: Raw.Kind,
      id: Option[Raw.Id],
      lat: Option[Raw.Lat],
      lon: Option[Raw.Lon],
      zoom: Option[Raw.Zoom],
      from: Option[Raw.From],
      to: Option[Raw.To],
      view: Option[Raw.View]
  )

  object Raw:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lat = Lat.T
    object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lon = Lon.T
    object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Zoom = Zoom.T
    object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type View = View.T
    object View extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `views`: the flags of a saved view (`--zoom`, `--view`) too. */
  def opts(kinds: Set[ItemKind], views: Boolean): Opts[Raw] =
    (
      Opts.argument[String](s"kind (${ItemKind.values.filter(kinds).map(_.wire).mkString(" | ")})"),
      Opts.argument[String]("id").orNone,
      Opts.option[Double]("lat", "A point's latitude, degrees north").orNone,
      Opts.option[Double]("lon", "A point's longitude, degrees east").orNone,
      if views then Opts.option[Double]("zoom", "A view's map zoom (2 a continent, 5 a country, 9 a city)").orNone
      else Opts(None),
      Opts.option[Int]("from", "The first year (BCE negative)").orNone,
      Opts.option[Int]("to", "The last year (BCE negative)").orNone,
      if views then Opts.option[String]("view", "A view's filters: what follows # in Explore's address").orNone
      else Opts(None)
    )
      .mapN(Raw.apply)

  /** The target the flags name, or why they name none. Pure. */
  def target(r: Raw): Either[Cli.Error, Target] =
    def usage(why: String) = Cli.Error(why, exitCode = ExitCode.Usage)
    for
      kind <- ItemKind
        .parse(r.kind)
        .toRight(usage(s"kind '${r.kind}': one of ${ItemKind.values.map(_.wire).mkString(", ")}"))
      lat <- r.lat.traverse(v => Latitude.either(v).left.map(_ => usage(s"--lat $v: between -90 and 90")))
      lon <- r.lon.traverse(v => Longitude.either(v).left.map(_ => usage(s"--lon $v: between -180 and 180")))
      zoom = r.zoom.map(MapZoom(_))
    yield
      val base = if kind.isItem then Target.item(kind, r.id.getOrElse("")) else Target(kind)
      base.modify(
        _.lat).setTo(lat).modify(_.lon).setTo(lon).modify(_.zoom).setTo(zoom).modify(_.from).setTo(r.from.map(Year(_))).modify(_.to).setTo(r.to.map(Year(_))).modify(_.view).setTo(r.view.map(ViewState(_))
      )

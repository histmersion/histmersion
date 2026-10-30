package com.histmersion.api.personal

import com.histmersion.api.ValueCodecs.given
import com.histmersion.api.YearFmt
import com.histmersion.values.{Latitude, Longitude, Year}
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a bookmark or a note points at, and where it opens back to: an item of the collection (`kind` and `id`), a
  * point on the map (`lat`, `lon`, optionally the years `from`..`to`), a saved view of Explore (`view` with the map's
  * `lat`, `lon` and `zoom`, and the span `from`..`to` it shows), or a period (`from`..`to`).
  */
final case class Target(
    kind: ItemKind,
    id: Option[ItemKey] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    zoom: Option[MapZoom] = None,
    from: Option[Year] = None,
    to: Option[Year] = None,
    view: Option[ViewState] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The item's key, for an item of the collection. */
  def itemKey: Option[String] = Option.when(kind.isItem)(id.map(_.value)).flatten

  /** One string per target that is the same item: `source:anthony2007_horse`; a point, a view or a period has none
    * (each saved one is its own). Pure.
    */
  def identity: Option[String] = itemKey.map(k => s"${kind.wire}:$k")

object Target:
  given Schema[Target] = summon[KindlingsSchema[Target]].schema

  def item(kind: ItemKind, key: String): Target = Target(kind, Some(ItemKey(key.trim)))

  /** `t` as it is kept: the fields its kind uses, checked, and the others dropped; refused when `kind` may not be
    * `what`'s target or a field it needs is missing. Pure.
    */
  def check(t: Target, allowed: Set[ItemKind], what: String): Either[String, Target] =
    val years = (t.from, t.to) match
      case (Some(a), Some(b)) if a.value > b.value =>
        Left(s"the period ends (${YearFmt(b.value.toLong)}) before it starts (${YearFmt(a.value.toLong)})")
      case _ => Right(())
    if !allowed.contains(t.kind) then
      Left(
        s"a $what cannot be left on a ${t.kind.label.toLowerCase}; it can be on: ${ItemKind.values.filter(allowed).map(_.wire).mkString(", ")}"
      )
    else
      years.flatMap { _ =>
        t.kind match
          case k if k.isItem =>
            t.id.map(_.value.trim).filter(_.nonEmpty) match
              case None      => Left(s"a $what on a ${k.label.toLowerCase} names it (its id)")
              case Some(key) => Right(Target(k, Some(ItemKey(key))))
          case ItemKind.Point =>
            if t.lat.isEmpty || t.lon.isEmpty then Left(s"a $what on the map names the point (lat and lon)")
            else Right(Target(ItemKind.Point, lat = t.lat, lon = t.lon, zoom = t.zoom, from = t.from, to = t.to))
          case ItemKind.View =>
            val view = t.view.map(_.value.trim.stripPrefix("#"))
            if view.exists(_.length > ViewState.MaxChars) then
              Left(s"a saved view is at most ${ViewState.MaxChars} characters")
            else if view.isEmpty && t.lat.isEmpty then
              Left("a saved view holds the filters (view) or the map's position (lat, lon)")
            else
              Right(
                Target(
                  ItemKind.View,
                  lat = t.lat,
                  lon = t.lon,
                  zoom = t.zoom,
                  from = t.from,
                  to = t.to,
                  view = view.map(ViewState(_))
                )
              )
          case _ => // a period
            if t.from.isEmpty || t.to.isEmpty then Left(s"a $what on a period names its years (from and to)")
            else Right(Target(ItemKind.Period, from = t.from, to = t.to))
      }

  /** Where a target is, in words, beside its name: a point's position and years, a view's map and span, a period's
    * years. Empty for an item. Pure.
    */
  def where(t: Target): String =
    val span = YearFmt.period(t.from.map(_.value.toLong), t.to.map(_.value.toLong))
    val at = t.lat.zip(t.lon).fold("")((la, lo) => s"${degrees(la.value, "N", "S")}, ${degrees(lo.value, "E", "W")}")
    t.kind match
      case ItemKind.Point => List(at, span).filter(_.nonEmpty).mkString(" · ")
      case ItemKind.View  =>
        List(at, t.zoom.fold("")(z => s"zoom ${round1(z.value)}"), span).filter(_.nonEmpty).mkString(" · ")
      case ItemKind.Period => span
      case _               => ""

  private def round1(v: Double): String =
    BigDecimal(v).setScale(1, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  private def degrees(v: Double, plus: String, minus: String): String =
    val a = BigDecimal(math.abs(v)).setScale(2, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString
    s"$a° ${if v >= 0 then plus else minus}"

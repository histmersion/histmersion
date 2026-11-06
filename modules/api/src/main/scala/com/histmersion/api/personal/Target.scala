package com.histmersion.api.personal

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.YearFmt
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind

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
      KindlingsSchema, com.histmersion.api.json.Schema:
  def itemKey: Option[com.histmersion.api.personal.ItemKey] = Option.when(((kind.isItem) == (true)))(id.map(_.value)).flatten

  def identity: Option[com.histmersion.api.personal.Target.Identity] = itemKey.map(k => _root_.com.histmersion.api.personal.Target.Identity(s"${kind.wire}:$k"))

object Target:
  type Identity = Identity.T
  object Identity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Where = Where.T
  object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  def item(kind: ItemKind, key: com.histmersion.api.personal.ItemKey): Target = Target(kind, Some(ItemKey((key).value.trim)))

  def check(t: Target, allowed: Set[ItemKind], what: String): Either[com.histmersion.api.personal.Target.Problem, Target] =
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
          case _ =>
            if t.from.isEmpty || t.to.isEmpty then Left(s"a $what on a period names its years (from and to)")
            else Right(Target(ItemKind.Period, from = t.from, to = t.to))
      }

  def where(t: Target): com.histmersion.api.personal.Target.Where =
    val span = YearFmt.period(t.from.map(_root_.com.histmersion.api.catalogue.Year(_.value.toLong)), t.to.map(_root_.com.histmersion.api.catalogue.Year(_.value.toLong)))
    val at = t.lat.zip(t.lon).fold("")((la, lo) => s"${degrees(la.value, ("N"), ("S"))}, ${degrees(lo.value, ("E"), ("W"))}")
    t.kind match
      case ItemKind.Point => _root_.com.histmersion.api.personal.Target.Where(List(at, span).filter(_.nonEmpty).mkString(" · "))
      case ItemKind.View  =>
        List(at, t.zoom.fold("")(z => s"zoom ${round1(z.value)}"), span).filter(_.nonEmpty).mkString(" · ")
      case ItemKind.Period => span
      case _               => _root_.com.histmersion.api.personal.Target.Where("")

  private def round1(v: Double): String =
    (BigDecimal((v)).setScale(1, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString)

  private def degrees(v: Double, plus: String, minus: String): String =
    val a = BigDecimal(math.abs((v))).setScale(2, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString
    s"$a° ${if v >= 0 then plus else minus}"

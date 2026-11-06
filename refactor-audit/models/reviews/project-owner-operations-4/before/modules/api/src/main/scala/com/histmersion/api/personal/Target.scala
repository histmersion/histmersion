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
  def itemKey: Option[com.histmersion.api.personal.Target.ItemKeyResult] = Option.when(kind.isItem)(id.map(_.value)).flatten

  def identity: Option[com.histmersion.api.personal.Target.IdentityResult] = itemKey.map(k => _root_.com.histmersion.api.personal.Target.IdentityResult(s"${kind.wire}:$k"))

object Target:


  type ItemKeyResult = ItemKeyResult.T
  object ItemKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentityResult = IdentityResult.T
  object IdentityResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemKey = ItemKey.T
  object ItemKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckWhat = CheckWhat.T
  object CheckWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WhereResult = WhereResult.T
  object WhereResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Round1Result = Round1Result.T
  object Round1Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Round1V = Round1V.T
  object Round1V extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DegreesResult = DegreesResult.T
  object DegreesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DegreesV = DegreesV.T
  object DegreesV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DegreesPlus = DegreesPlus.T
  object DegreesPlus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DegreesMinus = DegreesMinus.T
  object DegreesMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def item(kind: ItemKind, key: com.histmersion.api.personal.Target.ItemKey): Target = Target(kind, Some(ItemKey((key).value.trim)))

  def check(t: Target, allowed: Set[ItemKind], what: com.histmersion.api.personal.Target.CheckWhat): Either[com.histmersion.api.personal.Target.CheckResultPart1, Target] =
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

  def where(t: Target): com.histmersion.api.personal.Target.WhereResult =
    val span = YearFmt.period(t.from.map(_root_.com.histmersion.api.YearFmt.PeriodStart(_.value.toLong)), t.to.map(_root_.com.histmersion.api.YearFmt.PeriodEnd(_.value.toLong)))
    val at = t.lat.zip(t.lon).fold("")((la, lo) => s"${degrees(la.value, _root_.com.histmersion.api.personal.Target.DegreesPlus("N"), _root_.com.histmersion.api.personal.Target.DegreesMinus("S"))}, ${degrees(lo.value, _root_.com.histmersion.api.personal.Target.DegreesPlus("E"), _root_.com.histmersion.api.personal.Target.DegreesMinus("W"))}")
    t.kind match
      case ItemKind.Point => _root_.com.histmersion.api.personal.Target.WhereResult(List(at, span).filter(_.nonEmpty).mkString(" · "))
      case ItemKind.View  =>
        List(at, t.zoom.fold("")(z => s"zoom ${round1(z.value)}"), span).filter(_.nonEmpty).mkString(" · ")
      case ItemKind.Period => span
      case _               => _root_.com.histmersion.api.personal.Target.WhereResult("")

  private def round1(v: com.histmersion.api.personal.Target.Round1V): com.histmersion.api.personal.Target.Round1Result =
    BigDecimal((v).value).setScale(1, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  private def degrees(v: com.histmersion.api.personal.Target.DegreesV, plus: com.histmersion.api.personal.Target.DegreesPlus, minus: com.histmersion.api.personal.Target.DegreesMinus): com.histmersion.api.personal.Target.DegreesResult =
    val a = BigDecimal(math.abs((v).value)).setScale(2, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString
    s"$a° ${if v >= 0 then plus else minus}"

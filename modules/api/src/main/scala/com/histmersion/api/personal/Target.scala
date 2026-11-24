package com.histmersion
package api.personal
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.api.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.YearFmt
import com.histmersion.api.catalogue.{Latitude, Longitude, Year}
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class Target(
    kind: ItemKind,
    id: Option[ItemKey] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    zoom: Option[MapZoom] = None,
    from: Option[Year] = None,
    to: Option[Year] = None,
    view: Option[ViewState] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def itemKey: Option[ItemKey] = Option.when(kind.isItem)(id).flatten

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def identity: Option[Target.Identity] = itemKey.map(k => Target.Identity(s"${kind.wire}:$k"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Target:
  given JsonValueCodec[ItemKey] = ItemKey.given_JsonValueCodec_ItemKey

  type Identity = Identity.T
  object Identity extends Newtype[String, Pure]
  type Where = Where.T
  object Where extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  export com.histmersion.api.personal.ItemKey
  type CheckWhat = String
  object CheckWhat:
    inline def apply(v: String): String = v
  type DegreesPlus = String
  object DegreesPlus:
    inline def apply(v: String): String = v
  type DegreesMinus = String
  object DegreesMinus:
    inline def apply(v: String): String = v

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def item(kind: ItemKind, key: com.histmersion.api.personal.ItemKey): Target =
    Target(kind, Some(ItemKey((key).value.trim)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def check(t: Target, allowed: Set[ItemKind], what: Field): Either[Problem, Target] =
    val years = (t.from, t.to) match
      case (Some(a), Some(b)) if a > b =>
        Left(Invalid.Inconsistent.here(NonEmptyVector.of(Field("from"), Field("to")), Invalid.Exclusion.Ordered))
      case _ => Right(())
    if !allowed.contains(t.kind) then
      Left(
        Invalid.Unknown(
          TypeName.named[ItemKind],
          Raw(s"${t.kind.wire}"),
          ItemKind.values.toVector.filter(allowed).map(k => Raw(s"${k.wire}")),
          Vector(what)
        )
      )
    else
      years.flatMap { _ =>
        t.kind match
          case k if k.isItem =>
            t.id.map(_.value.trim).filter(_.nonEmpty) match
              case None =>
                Left(Invalid.Missing(Field("id"), Vector(what)))
              case Some(key) => Right(Target(k, Some(ItemKey(key))))
          case ItemKind.Point =>
            if t.lat.isEmpty || t.lon.isEmpty then
              Left(Invalid.Missing(Field(if t.lat.isEmpty then "lat" else "lon"), Vector(what)))
            else Right(Target(ItemKind.Point, lat = t.lat, lon = t.lon, zoom = t.zoom, from = t.from, to = t.to))
          case ItemKind.View =>
            val view = t.view.map(_.value.trim.stripPrefix("#"))
            if view.exists(_.length > ViewState.MaxChars) then
              Left(
                Invalid.OutOfRange(
                  TypeName.of(ViewState),
                  Raw.of(view.fold(0L)(_.length.toLong)),
                  Invalid.Bounds(None, Some(Raw.of(ViewState.MaxChars.toLong))),
                  Vector(what)
                )
              )
            else if view.isEmpty && t.lat.isEmpty then
              Left(
                Invalid.Inconsistent(
                  NonEmptyVector.of(Field("view"), Field("lat")),
                  Invalid.Exclusion.AtLeastOne,
                  Vector(what)
                )
              )
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
            if t.from.isEmpty || t.to.isEmpty then
              Left(Invalid.Missing(Field(if t.from.isEmpty then "from" else "to"), Vector(what)))
            else Right(Target(ItemKind.Period, from = t.from, to = t.to))
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def where(t: Target): Where =
    val span = YearFmt.period(t.from, t.to)
    val at =
      t.lat.zip(t.lon).fold("")((la, lo) => s"${degrees(la.value, ("N"), ("S"))}, ${degrees(lo.value, ("E"), ("W"))}")
    t.kind match
      case ItemKind.Point => Where(List(at, span).filter(_.nonEmpty).mkString(" · "))
      case ItemKind.View  =>
        Where(List(at, t.zoom.fold("")(z => s"zoom ${round1(z.value)}"), span).filter(_.nonEmpty).mkString(" · "))
      case ItemKind.Period => Where(span)
      case _               => Where("")

  private def round1(v: Double): String =
    (BigDecimal((v)).setScale(1, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString)

  private def degrees(v: Double, plus: String, minus: String): String =
    val a = BigDecimal(math.abs((v))).setScale(2, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString
    s"$a° ${if v >= 0 then plus else minus}"

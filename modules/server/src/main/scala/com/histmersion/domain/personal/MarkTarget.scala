package com.histmersion.domain.personal

import io.scalaland.chimney.dsl.*

import com.histmersion.api.personal.{ItemKey as WireKey, MapZoom, Target, ViewState}
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.Described
import com.histmersion.domain.ItemKey
import com.histmersion.domain.ItemKind
import com.histmersion.domain.ItemRef
import com.histmersion.domain.ItemUid
import com.histmersion.{Latitude, Longitude, Year}
import com.histmersion.domain.library.Title

/** What a bookmark or a note is left on, or what a read mark is about: a record of some domain (personal knows only its
  * reference), a point on the map, a saved view of Explore, or a span of years.
  */
enum MarkTarget:
  case Item(ref: ItemRef)
  case Point(lat: Latitude, lon: Longitude, zoom: Option[MapZoom], from: Option[Year], to: Option[Year])
  case View(
      state: Option[ViewState],
      lat: Option[Latitude],
      lon: Option[Longitude],
      zoom: Option[MapZoom],
      from: Option[Year],
      to: Option[Year]
  )
  case Span(from: Year, to: Year)

  def kind: MarkKind = this match
    case Item(ref) => MarkTarget.markKind(ref.kind).getOrElse(throw IllegalStateException(s"kind ${ref.kind.value}"))
    case _: Point  => MarkKind.Point
    case _: View   => MarkKind.View
    case _: Span   => MarkKind.Period

  def itemRef: Option[ItemRef] = this match
    case Item(r) => Some(r)
    case _       => None

  /** The same target, its item's uid as resolved when it was marked. */
  def resolved(uid: Option[ItemUid]): MarkTarget = this match
    case Item(r) => Item(r.copy(uid = uid))
    case other   => other

  /** As a note keeps it: a note on a point does not keep the map's zoom. */
  def withoutZoom: MarkTarget = this match
    case p: Point => p.copy(zoom = None)
    case other    => other

  /** The target as it shows now, and what it is called: the item's key and title as its owner describes them, else the
    * name it had when it was marked, else its key or what its kind is.
    */
  def shown(described: Map[ItemRef, Described], stored: Option[TargetLabel]): (MarkTarget, Title) = this match
    case Item(r) =>
      val d = described.get(r)
      val now = d.fold(r)(x => r.copy(key = x.key))
      val live = d.flatMap(_.title).flatMap(t => TargetLabel.tidy(t.value))
      (Item(now), Title(live.orElse(stored).fold(now.key.value)(_.value)))
    case other => (other, Title(stored.fold(other.kind.label)(_.value)))

  def wire: Target = this match
    case Item(r)                         => Target(kind, Some(WireKey(r.key.value)))
    case Point(lat, lon, zoom, from, to) =>
      Target(
        MarkKind.Point,
        lat = Some(lat.transformInto[com.histmersion.api.catalogue.Latitude.T]),
        lon = Some(lon.transformInto[com.histmersion.api.catalogue.Longitude.T]),
        zoom = zoom,
        from = from.transformInto[Option[com.histmersion.api.catalogue.Year]],
        to = to.transformInto[Option[com.histmersion.api.catalogue.Year]]
      )
    case View(state, lat, lon, zoom, from, to) =>
      Target(
        MarkKind.View,
        lat = lat.transformInto[Option[com.histmersion.api.catalogue.Latitude]],
        lon = lon.transformInto[Option[com.histmersion.api.catalogue.Longitude]],
        zoom = zoom,
        from = from.transformInto[Option[com.histmersion.api.catalogue.Year]],
        to = to.transformInto[Option[com.histmersion.api.catalogue.Year]],
        view = state
      )
    case Span(from, to) =>
      Target(
        MarkKind.Period,
        from = Some(from.transformInto[com.histmersion.api.catalogue.Year.T]),
        to = Some(to.transformInto[com.histmersion.api.catalogue.Year.T])
      )

object MarkTarget:

  /** The deepest zoom the map has; a target's zoom is between 0 and this. */
  val MaxZoom = 24.0

  /** The target, when its zoom (if any) is one the map has. */
  def zoomChecked(t: Target): Either[String, Target] =
    t.zoom.map(_.value).filterNot(z => z >= 0.0 && z <= MaxZoom) match
      case Some(z) => Left(s"zoom $z: between 0 and ${MaxZoom.toInt}")
      case None    => Right(t)

  /** The kind of record an item kind of the wire names. */
  def itemKind(k: MarkKind): ItemKind = ItemKind.applyUnsafe(k.wire)

  def markKind(k: ItemKind): Option[MarkKind] = MarkKind.values.find(m => m.isItem && m.wire == k.value)

  /** A target the wire sent, once [[Target.check]] passed it: an item's uid is not known yet. */
  def fromWire(t: Target): Option[MarkTarget] =
        for
          lat <- t.lat.transformIntoPartial[Option[Latitude]].asOption
          lon <- t.lon.transformIntoPartial[Option[Longitude]].asOption
          result <- t.kind match
            case k if k.isItem => t.id.map(key => Item(ItemRef(itemKind(k), ItemKey(key.value), None)))
            case MarkKind.Point => lat.zip(lon).map((la, lo) => Point(la, lo, t.zoom, t.from.transformInto[Option[Year]], t.to.transformInto[Option[Year]]))
            case MarkKind.View => Some(View(t.view, lat, lon, t.zoom, t.from.transformInto[Option[Year]], t.to.transformInto[Option[Year]]))
            case _ => t.from.transformInto[Option[Year]].zip(t.to.transformInto[Option[Year]]).map(Span(_, _))
        yield result

  /** A stored row's target; none when the row does not make one (a kind this version does not know). */
  def ofColumns(
      kind: String,
      key: Option[ItemKey],
      uid: Option[ItemUid],
      lat: Option[Latitude],
      lon: Option[Longitude],
      zoom: Option[MapZoom],
      from: Option[Year],
      to: Option[Year],
      view: Option[ViewState]
  ): Option[MarkTarget] =
    MarkKind.values.find(_.wire == kind).flatMap {
      case k if k.isItem  => key.map(kk => Item(ItemRef(itemKind(k), kk, uid)))
      case MarkKind.Point => lat.zip(lon).map((la, lo) => Point(la, lo, zoom, from, to))
      case MarkKind.View  => Some(View(view, lat, lon, zoom, from, to))
      case _              => from.zip(to).map(Span(_, _))
    }

package com.histmersion.domain.catalogue.places

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.Place

import java.util.Locale

/** A place as the in-memory catalogue holds it: the row an import or a person wrote, the dataset it came in with, its
  * names (a name a person added is `manual`, which no import deletes), the edges to its parents and its links.
  */
final case class HeldPlace(
    place: Place,
    dataset: Dataset,
    names: Vector[HeldPlace.Name] = Vector.empty,
    parents: Vector[Place.Relation] = Vector.empty,
    links: Vector[Place.Link] = Vector.empty
):
  def id: com.histmersion.domain.catalogue.Place.Id = place.id.value

  /** Is this place valid at `asOf` (a timeless place always is)? */
  def validAt(asOf: Option[com.histmersion.domain.catalogue.places.HeldPlace.AsOf]): Boolean = HeldPlace.covers(place.period, asOf)

  /** Its names valid at `asOf`, distinct and by name, comma-joined — the `names` column of a summary. */
  def namesAt(asOf: Option[com.histmersion.domain.catalogue.places.HeldPlace.AsOf]): com.histmersion.domain.catalogue.places.HeldPlace.NamesAtResult =
    names.filter(n => HeldPlace.covers(n.period, asOf)).map(_.name).distinct.sorted.mkString(", ")

  def contested: Boolean =
    place.properties.exists(p =>
      io.circe.parser.parse(p).toOption.flatMap(_.hcursor.downField("contested").as[Boolean].toOption).contains(true)
    )

  def summary: Place.Summary =
    Place.Summary(
      com.histmersion.api.catalogue.Place.Id(id),
      place.label,
      place.kind,
      place.period.map(_.start),
      place.period.map(_.end),
      place.source,
      namesAt(None),
      contested,
      Place.Dating.Stated,
      None
    )

object HeldPlace:
  type AsOf = AsOf.T
  object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One name: `manual` when a person added it. */
  type NamesAtResult = NamesAtResult.T
  object NamesAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerResult = LowerResult.T
  object LowerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowerS = LowerS.T
  object LowerS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Name(
      name: Name.Value,
      lang: Name.Lang,
      period: Option[com.histmersion.api.catalogue.Period],
      manual: Name.Manual
  )

  object Name:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manual = Manual.T
    object Manual extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(n: Place.Name): Name = Name(n.name, n.lang, n.period, manual = false)

  /** Does `period` (none: always) contain the year `asOf` (none: any time)? */
  def covers(period: Option[com.histmersion.api.catalogue.Period], asOf: Option[com.histmersion.domain.catalogue.places.HeldPlace.AsOf]): Boolean =
    asOf.forall(y => period.forall(p => p.start <= y && y <= p.end))

  /** The case-insensitive form a search compares. */
  def lower(s: com.histmersion.domain.catalogue.places.HeldPlace.LowerS): com.histmersion.domain.catalogue.places.HeldPlace.LowerResult = s.toLowerCase(Locale.ROOT)

package com.histmersion.domain.catalogue.places

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.DeepTime

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
  def id: Place.Id = place.id

  /** Is this place valid at `asOf` (a timeless place always is)? */
  def validAt(asOf: Option[DeepTime.SignedYear]): Boolean = HeldPlace.covers(place.period, asOf)

  /** Its names valid at `asOf`, distinct and by name, comma-joined — the `names` column of a summary. */
  def namesAt(asOf: Option[DeepTime.SignedYear]): Place.Summary.Names =
    Place.Summary.Names(names.filter(n => HeldPlace.covers(n.period, asOf)).map(_.name.value).distinct.sorted.mkString(", "))

  def contested: Boolean =
    place.properties.exists(p =>
      io.circe.parser.parse(p.value).toOption.flatMap(_.hcursor.downField("contested").as[Boolean].toOption).contains(true)
    )

  def summary: Place.Summary =
    Place.Summary(
      id,
      place.label,
      Place.Summary.Kind(place.kind.value),
      place.period.map(_.start),
      place.period.map(_.end),
      place.source,
      namesAt(None),
      Place.Summary.Contested(contested),
      Place.Dating.Stated,
      None
    )

object HeldPlace:
  /** One name: `manual` when a person added it. */
  final case class Name(
      name: Place.Name.Value,
      lang: Place.Name.Lang,
      period: Option[com.histmersion.api.catalogue.Period],
      manual: Name.Manual
  )

  object Name:
    type Manual = Manual.T
    object Manual extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(n: Place.Name): Name = Name(n.name, n.lang, n.period, manual = Name.Manual(false))

  /** Does `period` (none: always) contain the year `asOf` (none: any time)? */
  def covers(period: Option[com.histmersion.api.catalogue.Period], asOf: Option[DeepTime.SignedYear]): Boolean =
    asOf.forall(y => period.forall(p => p.start.value <= y.value && y.value <= p.end.value))

  /** The case-insensitive form a search compares. */
  def lower(s: String): String = s.toLowerCase(Locale.ROOT)

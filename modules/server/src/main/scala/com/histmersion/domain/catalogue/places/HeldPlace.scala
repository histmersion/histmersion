package com.histmersion
package domain.catalogue.places
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.DeepTime

import java.util.Locale
import com.histmersion.api.catalogue.Period
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** A place as the in-memory catalogue holds it: the row an import or a person wrote, the dataset it came in with, its
  * names (a name a person added is `manual`, which no import deletes), the edges to its parents and its links.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def namesAt(asOf: Option[DeepTime.SignedYear]): Place.Summary.Names =
    Place.Summary.Names(
      names.filter(n => HeldPlace.covers(n.period, asOf)).map(_.name.value).distinct.sorted.mkString(", ")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contested: Boolean =
    place.properties.exists(p =>
      RawJson
        .parse(p.value)
        .toOption
        .flatMap(_.cursor.downField("contested").as[Boolean].toOption)
        .contains(true)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def summary: Place.Summary =
    Place.Summary(
      id,
      place.label,
      Place.Summary.Kind(place.kind.value),
      place.period.map(_.start),
      place.period.map(_.end),
      place.source,
      namesAt(None),
      (if contested then Place.Summary.Contested.Contested else Place.Summary.Contested.Settled),
      Place.Dating.Stated,
      None
    )

object HeldPlace:
  /** One name: `manual` when a person added it. */
  final case class Name(
      name: Place.Name.Value,
      lang: Place.Name.Lang,
      period: Option[Period],
      manual: Name.Manual
  )

  object Name:
    enum Manual derives CanEqual:
      case Manual, Imported
    object Manual extends TwoCases[Manual]:
      extension (manual: Manual) def manual: Boolean = manual == Manual

    def of(n: Place.Name): Name = Name(n.name, n.lang, n.period, manual = Manual.Imported)

  /** Does `period` (none: always) contain the year `asOf` (none: any time)? */
  def covers(period: Option[Period], asOf: Option[DeepTime.SignedYear]): Boolean =
    asOf.forall(y => period.forall(p => p.start <= y && y <= p.end))

  /** The case-insensitive form a search compares. */
  def lower(s: String): String = s.toLowerCase(Locale.ROOT)

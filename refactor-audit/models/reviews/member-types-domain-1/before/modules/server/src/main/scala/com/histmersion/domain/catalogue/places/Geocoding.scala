package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.util.database.Transactor

/** A place NAME → its gazetteer point (#981): the one geocoder, for a finding about a place of kind `hint` dated `at`
  * (`None` = no gazetteer place by that name; nothing is ever guessed). A mechanism over PostGIS — a point on a shape's
  * surface, a shape cut to a compass part — with no in-memory twin.
  */
trait Geocoding:
  def resolve(name: String, hint: Geocode.KindHint, at: Option[Period], placeholderMin: Int): Option[Geocode.Resolution]

object Geocoding:
  def apply(db: Transactor): Geocoding = Implementation(db)

  final private class Implementation(db: Transactor) extends Geocoding:
    def resolve(
        name: String,
        hint: Geocode.KindHint,
        at: Option[Period],
        placeholderMin: Int
    ): Option[Geocode.Resolution] =
      db.read(Geocoder.resolve(name, hint, at, placeholderMin))

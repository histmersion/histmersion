package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.geocode.Attempt
import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.api.catalogue.geocode.Resolution

import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.util.database.Transactor
import com.histmersion.lint.{tolerated, LintRule}

/** A place NAME → its gazetteer point (#981): the one geocoder, for a finding about a place of kind `hint` dated `at`
  * (`None` = no gazetteer place by that name; nothing is ever guessed). A mechanism over PostGIS — a point on a shape's
  * surface, a shape cut to a compass part — with no in-memory twin.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait Geocoding:
  def resolve(
      name: Attempt.Name,
      hint: KindHint,
      at: Option[Period],
      placeholderMin: GeocodeSettings.PlaceholderMin
  ): Option[Resolution]

object Geocoding:
  def apply(db: Transactor): Geocoding = Implementation(db)

  final private class Implementation(db: Transactor) extends Geocoding:
    def resolve(
        name: Attempt.Name,
        hint: KindHint,
        at: Option[Period],
        placeholderMin: GeocodeSettings.PlaceholderMin
    ): Option[Resolution] =
      db.read(Geocoder.resolve(name, hint, at, placeholderMin))

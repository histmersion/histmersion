package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Place
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class PlaceChoice(
    claim: PlaceChoice.Claim,
    asked: PlaceChoice.Asked,
    placeId: Option[Place.Id],
    label: Option[PlaceChoice.Label],
    approximate: Option[PlaceChoice.Approximate]
) derives JsonCodec

object PlaceChoice:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Asked = Asked.T
  object Asked extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Approximate = Approximate.T
  object Approximate extends Newtype[String, Pure]

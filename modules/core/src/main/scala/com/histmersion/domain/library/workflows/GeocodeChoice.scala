package com.histmersion
package domain.library.workflows

import com.histmersion.domain.catalogue
import io.github.iltotore.iron.constraint.any.Pure

final case class GeocodeChoice(
    claim: GeocodeChoice.Claim,
    asked: GeocodeChoice.Asked,
    placeId: Option[catalogue.Place.Id],
    label: Option[GeocodeChoice.Label],
    approximate: Option[GeocodeChoice.Approximate]
)

object GeocodeChoice:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Asked = Asked.T
  object Asked extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Approximate = Approximate.T
  object Approximate extends Newtype[String, Pure]

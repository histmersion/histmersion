package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ManualPlaceName(
    placeId: Place.Id,
    label: ManualPlaceName.Label,
    name: ManualPlaceName.Name,
    lang: ManualPlaceName.Lang
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManualPlaceName:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Lang = Lang.T
  object Lang extends Newtype[String, Pure]

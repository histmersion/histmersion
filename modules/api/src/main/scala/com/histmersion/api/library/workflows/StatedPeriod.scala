package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure

final case class StatedPeriod(start: StatedYear, end: StatedYear, name: Option[StatedPeriod.Name] = None) derives JsonCodec, KindlingsSchema, Schema

object StatedPeriod:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

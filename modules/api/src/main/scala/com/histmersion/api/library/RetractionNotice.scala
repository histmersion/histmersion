package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class RetractionNotice(nature: Option[RetractionNotice.Nature], reason: Option[RetractionNotice.Reason], date: Option[RetractionNotice.Date])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object RetractionNotice:
  type Nature = Nature.T
  object Nature extends Newtype[String, Pure]
  type Reason = Reason.T
  object Reason extends Newtype[String, Pure]
  type Date = Date.T
  object Date extends Newtype[String, Pure]

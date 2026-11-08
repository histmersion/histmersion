package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A Crossref `update-to` entry. */
final case class UpdateNotice(kind: Option[UpdateNotice.Kind], label: Option[UpdateNotice.Label], doi: Option[Doi])
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UpdateNotice:
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]

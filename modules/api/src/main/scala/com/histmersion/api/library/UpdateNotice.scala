package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A Crossref `update-to` entry. */
final case class UpdateNotice(kind: Option[UpdateNotice.Kind], label: Option[UpdateNotice.Label], doi: Option[Doi])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object UpdateNotice:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

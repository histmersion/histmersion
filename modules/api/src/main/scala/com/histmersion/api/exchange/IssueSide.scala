package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class IssueSide(label: IssueSide.Label, uid: UUID, lines: List[IssueSide.Lines] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object IssueSide:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Lines = Lines.T
  object Lines extends Newtype[String, Pure]

package com.histmersion
package api.aliases
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class InstanceInfo(id: UUID, label: Option[InstanceInfo.Label], createdAt: InstanceInfo.CreatedAt)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object InstanceInfo:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]

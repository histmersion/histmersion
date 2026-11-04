package com.histmersion.api.aliases
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class InstanceInfo(id: UUID, label: Option[String], createdAt: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

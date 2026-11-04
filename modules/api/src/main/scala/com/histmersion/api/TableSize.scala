package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TableSize(name: String, bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

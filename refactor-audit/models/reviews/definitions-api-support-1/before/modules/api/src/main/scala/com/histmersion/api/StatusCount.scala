package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source

final case class StatusCount(status: Source.Status, count: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

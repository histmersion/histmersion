package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class InstanceLabel(label: Option[String]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

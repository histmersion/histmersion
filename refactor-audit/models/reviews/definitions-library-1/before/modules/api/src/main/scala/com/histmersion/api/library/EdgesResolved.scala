package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EdgesResolved(resolved: Int) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CoverageEdge(from: String, kind: String, to: String) derives KindlingsCodecAsObject, KindlingsSchema
object CoverageEdge:
  given Schema[CoverageEdge] = summon[KindlingsSchema[CoverageEdge]].schema

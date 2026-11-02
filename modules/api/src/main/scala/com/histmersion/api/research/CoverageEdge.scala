package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CoverageEdge(from: String, kind: String, to: String) derives KindlingsCodecAsObject, KindlingsSchema
object CoverageEdge:
  given Schema[CoverageEdge] = summon[KindlingsSchema[CoverageEdge]].schema

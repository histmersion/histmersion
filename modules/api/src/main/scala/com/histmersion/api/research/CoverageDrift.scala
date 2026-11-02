package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CoverageDrift(kind: DriftKind, citekeys: List[String]) derives KindlingsCodecAsObject, KindlingsSchema
object CoverageDrift:
  given Schema[CoverageDrift] = summon[KindlingsSchema[CoverageDrift]].schema

package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class SeedResult(
    imported: Int,
    failed: List[String],
    edgesResolved: Int,
    skipped: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SeedResult:
  given Schema[SeedResult] = summon[KindlingsSchema[SeedResult]].schema

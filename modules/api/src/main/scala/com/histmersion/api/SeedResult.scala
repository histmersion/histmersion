package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `skipped` = citekeys whose stored row is NEWER than the file on disk (agent enrichment that a reseed would have
  * wiped); re-run with `force` to overwrite them anyway.
  */
final case class SeedResult(
    imported: Int,
    failed: List[String],
    edgesResolved: Int,
    skipped: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SeedResult:
  given Schema[SeedResult] = summon[KindlingsSchema[SeedResult]].schema

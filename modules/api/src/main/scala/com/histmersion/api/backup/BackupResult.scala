package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BackupResult(
    file: String,
    bytes: Long,
    sha256: String,
    durationMs: Long,
    pruned: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupResult:
  given Schema[BackupResult] = summon[KindlingsSchema[BackupResult]].schema

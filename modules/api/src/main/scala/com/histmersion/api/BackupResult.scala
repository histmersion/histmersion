package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── wire DTOs (declared last: Kindlings derivation is order-sensitive at the file level) ────────

/** `POST /api/backup` — one freshly written archive. */
final case class BackupResult(
    file: String,
    bytes: Long,
    sha256: String,
    durationMs: Long,
    /** Older archives deleted afterwards to keep `backups.keep`. */
    pruned: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupResult:
  given Schema[BackupResult] = summon[KindlingsSchema[BackupResult]].schema

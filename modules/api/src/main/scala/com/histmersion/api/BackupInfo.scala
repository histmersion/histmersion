package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One row of `GET /api/backups`, newest first. No checksum here on purpose — hashing every archive on every listing
  * would read gigabytes; `POST /api/backup` reports the checksum of what it wrote. `createdAt` is when the dump was
  * taken ([[Backup.takenAt]]).
  */
final case class BackupInfo(file: String, bytes: Long, createdAt: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupInfo:
  given Schema[BackupInfo] = summon[KindlingsSchema[BackupInfo]].schema

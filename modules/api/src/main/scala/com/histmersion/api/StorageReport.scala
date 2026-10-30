package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** @param measuring
  *   a measurement is under way; the rest is the previous one (or empty, on the first)
  * @param measuredAt
  *   when the entries were measured (ISO-8601)
  * @param tookMillis
  *   how long that took
  * @param ageSeconds
  *   seconds between `measuredAt` and the server's clock when it answered
  * @param lowFreeBytes
  *   below this much free space a volume gets a [[hints]] line (`storage.low-free-gb`)
  * @param hints
  *   what to do about low free space — [[Storage.hints]] of the rest
  * @param problems
  *   what could not be measured — said, never hidden
  */
final case class StorageReport(
    measuring: Boolean = false,
    measuredAt: Option[String] = None,
    tookMillis: Option[Long] = None,
    ageSeconds: Option[Long] = None,
    entries: List[StorageEntry] = Nil,
    tables: List[TableSize] = Nil,
    volumes: List[StorageVolume] = Nil,
    backups: Option[BackupsUsage] = None,
    lowFreeBytes: Long = Storage.DefaultLowFreeBytes,
    hints: List[String] = Nil,
    problems: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object StorageReport:
  given Schema[StorageReport] = summon[KindlingsSchema[StorageReport]].schema

package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TableSize
import com.histmersion.api.backup.BackupsUsage
import com.histmersion.api.given

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

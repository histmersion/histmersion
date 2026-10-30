package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class About(
    name: String,
    build: BuildStamp,
    health: Health,
    jobs: JobCounts,
    counts: Option[CorpusCounts],
    lastBackup: Option[BackupInfo],
    lastBackupAgeSeconds: Option[Long],
    problems: List[String] = Nil,
    configFile: ConfigFileInfo = ConfigFileInfo(),
    agentLease: Option[AgentLeaseInfo] = None,
    plugins: PluginsReport = PluginsReport(),
    /** Empty from an older server. */
    storage: StorageReport = StorageReport(),
    /** Empty from an older server. */
    upstreams: List[UpstreamQuota] = Nil,
    /** Absent from an older server. */
    acquireRecheck: Option[AcquireChecks.RecheckStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object About:
  given Schema[About] = summon[KindlingsSchema[About]].schema

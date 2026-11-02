package com.histmersion.api.instance
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.automation.AgentLeaseInfo
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.library.UpstreamQuota
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.given
import com.histmersion.api.backup.Backup
import com.histmersion.api.library.Acquire
import com.histmersion.api.maintenance.Storage

final case class About(
    name: String,
    build: BuildStamp,
    health: Health,
    jobs: JobCounts,
    counts: Option[CorpusCounts],
    lastBackup: Option[Backup.Info],
    lastBackupAgeSeconds: Option[Long],
    problems: List[String] = Nil,
    configFile: ConfigFileInfo = ConfigFileInfo(),
    agentLease: Option[AgentLeaseInfo] = None,
    plugins: PluginsReport = PluginsReport(),
    /** Empty from an older server. */
    storage: Storage.Report = Storage.Report(),
    /** Empty from an older server. */
    upstreams: List[UpstreamQuota] = Nil,
    /** Absent from an older server. */
    acquireRecheck: Option[Acquire.Checks.RecheckStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object About:
  given Schema[About] = summon[KindlingsSchema[About]].schema

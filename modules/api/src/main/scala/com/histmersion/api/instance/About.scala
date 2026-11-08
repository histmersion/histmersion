package com.histmersion
package api.instance
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
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.backup.Backup
import com.histmersion.api.library.Acquire
import com.histmersion.api.maintenance.Storage
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class About(
    name: About.Name,
    build: BuildStamp,
    health: Health,
    jobs: JobCounts,
    counts: Option[CorpusCounts],
    lastBackup: Option[Backup.Info],
    lastBackupAgeSeconds: Option[About.LastBackupAgeSeconds],
    problems: List[About.Problems] = Nil,
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
      KindlingsSchema, com.histmersion.api.json.Schema

object About:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type LastBackupAgeSeconds = LastBackupAgeSeconds.T
  object LastBackupAgeSeconds extends RefinedType[Long, Pure]
  type Problems = Problems.T
  object Problems extends RefinedType[String, Pure]

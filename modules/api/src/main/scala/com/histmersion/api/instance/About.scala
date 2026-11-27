package com.histmersion
package api.instance
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object About:
  type Name = Name.T
  object Name extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type LastBackupAgeSeconds = LastBackupAgeSeconds.T
  object LastBackupAgeSeconds extends Newtype[Long, Pure]
  type Problems = Problems.T
  object Problems extends Newtype[String, Pure]

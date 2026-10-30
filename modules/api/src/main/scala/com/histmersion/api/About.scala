package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/about` — who the running server is and how it is doing, in ONE read: the web UI's About panel and
  * `histmersion about`. Read-only; every number is a count the store already keeps.
  */

final case class About(
    name: String,
    build: BuildStamp,
    /** The same report `/api/health` gives — database, schema version, converter. */
    health: Health,
    jobs: JobCounts,
    /** `None` when the store could not be counted (the database is down). */
    counts: Option[CorpusCounts],
    /** The newest archive in `backups.dir`, if there is one. */
    lastBackup: Option[BackupInfo],
    /** Seconds between that archive and the server's clock when it answered. */
    lastBackupAgeSeconds: Option[Long],
    /** What could not be read (backups dir unreadable, a count failed) — said, never hidden. */
    problems: List[String] = Nil,
    /** The user config file this server started with. */
    configFile: ConfigFileInfo = ConfigFileInfo(),
    /** The agent lease's holder (None: no server has claimed it — agents off everywhere). */
    agentLease: Option[AgentLeaseInfo] = None,
    /** The plugins in this binary and what became of each (secrets as references, never values). */
    plugins: PluginsReport = PluginsReport(),
    /** Where the data is on disk and how big each part is (measured in the background, see [[StorageReport]]); empty
      * from an older server.
      */
    storage: StorageReport = StorageReport(),
    /** The metered upstreams' budgets today (CORE); empty before one was first asked, or from an older server.
      */
    upstreams: List[UpstreamQuota] = Nil,
    /** The acquisition list's legal re-check; absent when it could not be read, or from an older server. */
    acquireRecheck: Option[AcquireChecks.RecheckStatus] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object About:
  given Schema[About] = summon[KindlingsSchema[About]].schema

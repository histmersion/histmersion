package com.histmersion
package pluginhost

import com.histmersion.plugin.*
import org.ekrich.config.Config

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What the host lends every plugin of a generation: the HTTP client (built on first use), the log, the environment
  * read at startup, and the budgets of the metered upstreams (by name; an upstream with none is unlimited).
  */
final case class HostServices(
    http: () => Plugin.Http,
    env: Config,
    log: (HostServices.LogArg1, HostServices.LogArg2, HostServices.LogArg3) => Unit, // (plugin id, is a warning, message)
    quota: HostServices.QuotaArg1 => UpstreamQuota = _ => UpstreamQuota.Unlimited,
    files: LocalFiles = LocalFiles.Unavailable,
    processes: Processes = Processes.Unavailable
)

object HostServices:
  /** The budgets of the metered upstreams the host lends plugins, by upstream name. */
  type LogArg1 = LogArg1.T
  object LogArg1 extends RefinedType[String, Pure]
  type LogArg2 = LogArg2.T
  object LogArg2 extends RefinedType[Boolean, Pure]
  type LogArg3 = LogArg3.T
  object LogArg3 extends RefinedType[String, Pure]
  type QuotaArg1 = QuotaArg1.T
  object QuotaArg1 extends RefinedType[String, Pure]

  type Quotas = String => UpstreamQuota

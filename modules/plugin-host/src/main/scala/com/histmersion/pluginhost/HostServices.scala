package com.histmersion
package pluginhost

import com.histmersion.plugin.*
import org.ekrich.config.Config

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** What the host lends every plugin of a generation: the HTTP client (built on first use), the log, the environment
  * read at startup, and the budgets of the metered upstreams (by name; an upstream with none is unlimited).
  */
final case class HostServices(
    http: () => Plugin.Http,
    env: Config,
    log: (
        HostServices.PluginId,
        Boolean,
        HostServices.Line
    ) => Unit, // (plugin id, is a warning, message)
    quota: HostServices.Upstream => UpstreamQuota = _ => UpstreamQuota.Unlimited,
    files: LocalFiles = LocalFiles.Unavailable,
    processes: Processes = Processes.Unavailable
)

object HostServices:
  /** The budgets of the metered upstreams the host lends plugins, by upstream name. */
  type PluginId = PluginId.T
  object PluginId extends Newtype[String, Pure]
  type Line = Line.T
  object Line extends Newtype[String, Pure]
  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]

  type Quotas = String => UpstreamQuota

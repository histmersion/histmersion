package com.histmersion.pluginhost

import com.histmersion.plugin.*
import org.ekrich.config.Config

import scala.jdk.CollectionConverters.*

/** What the host lends every plugin of a generation: the HTTP client (built on first use), the log, the environment
  * read at startup, and the budgets of the metered upstreams (by name; an upstream with none is unlimited).
  */
final case class HostServices(
    http: () => Plugin.Http,
    env: Config,
    log: (String, Boolean, String) => Unit, // (plugin id, is a warning, message)
    quota: String => UpstreamQuota = _ => UpstreamQuota.Unlimited
)

object HostServices:
  /** The budgets of the metered upstreams the host lends plugins, by upstream name. */
  type Quotas = String => UpstreamQuota

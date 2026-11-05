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
    log: (HostServices.LogArg1, HostServices.LogArg2, HostServices.LogArg3) => Unit, // (plugin id, is a warning, message)
    quota: HostServices.QuotaArg1 => UpstreamQuota = _ => UpstreamQuota.Unlimited,
    files: LocalFiles = LocalFiles.Unavailable,
    processes: Processes = Processes.Unavailable
)

object HostServices:
  /** The budgets of the metered upstreams the host lends plugins, by upstream name. */
  type LogArg1 = LogArg1.T
  object LogArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogArg2 = LogArg2.T
  object LogArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogArg3 = LogArg3.T
  object LogArg3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotaArg1 = QuotaArg1.T
  object QuotaArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Quotas = String => UpstreamQuota

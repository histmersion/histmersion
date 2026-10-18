package com.histmersion.server.plugins

import com.histmersion.core.{AppConfig, LoadedConfig}
import com.histmersion.plugin.PluginHttp
import com.histmersion.server.acquire.{OutboundTls, SttpHttp}

/** The composition root's handle on the plugins: discover the providers on the server's class path
  * and build the generation for a loaded config. Opens nothing — no database, no listener, no socket
  * (the HTTP client is built only when a plugin first asks for it) — so `--self-check` can call it. */
object Plugins:

  def load(loaded: LoadedConfig): Generation =
    val (found, errors) = PluginHost.discover(getClass.getClassLoader)
    PluginHost.build(found, loaded.user, loaded.env, services(loaded.config, loaded.env), errors)

  /** The host's services: the one outbound HTTP policy (the acquisition client, `acquire.tls`
    * included) and scribe with the plugin's id on every line. */
  def services(cfg: AppConfig, env: org.ekrich.config.Config): HostServices =
    HostServices(() => http(cfg), env, log)

  private def http(cfg: AppConfig): PluginHttp =
    val tls    = OutboundTls.configure(cfg.acquire.tls)
    val client = SttpHttp(cfg.acquire, maxRetryAfterSeconds = cfg.media.maxRetryAfterSeconds, tls = tls.map(_._1))
    new PluginHttp:
      def get(url: String, headers: Map[String, String]) = client.bytes(url, headers)

  private def log(id: String, warn: Boolean, message: String): Unit =
    val line = if id.isEmpty then s"plugins: $message" else s"plugin $id: $message"
    if warn then scribe.warn(line) else scribe.info(line)

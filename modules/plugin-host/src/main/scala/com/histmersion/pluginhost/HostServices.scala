package com.histmersion.pluginhost

import com.histmersion.files.*
import com.histmersion.plugin.*
import org.ekrich.config.Config

import scala.jdk.CollectionConverters.*

/** What the host lends every plugin of a generation: the HTTP client (built on first use), the log and the environment
  * read at startup.
  */
final case class HostServices(
    http: () => PluginHttp,
    env: Config,
    log: (String, Boolean, String) => Unit
) // (plugin id, is a warning, message)

package com.histmersion
package util.settings

import com.histmersion.config.{AppConfig, LoadedConfig, UserConfigFile}
import ox.{forkDiscard, useInScope}

import scala.concurrent.duration.*

/** The config a hot generation is built from: the decoded settings the server runs on (fitted to this process — the
  * bundled runtime's endpoints) and the load they came from (the file, the user's plugin blocks, the environment read
  * at startup, the merged settings a reload compares).
  */
final case class HotConfig(app: AppConfig, loaded: LoadedConfig)

object HotConfig:
  /** A config with no file behind it (a test's): it can be built from, not reloaded. */
  def fixed(cfg: AppConfig): HotConfig =
    HotConfig(cfg, LoadedConfig(cfg, UserConfigFile(None, loaded = UserConfigFile.Loaded(false))))

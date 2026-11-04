package com.histmersion.cli

import com.monovore.decline.*
import com.histmersion.config.*

/** The configuration as the CLI reads it: reference.conf < the user config file < the environment, the same layers as
  * the server ([[AppConfig.loadWithUserFile]]). Read on first use, so `--help` never reads a file. A file that does not
  * read is said once on stderr and the CLI goes on with the defaults and the environment: it is a client, and
  * `histmersion config path` must still run to show what is wrong.
  */
object Settings:
  lazy val loaded: Either[String, LoadedConfig] = AppConfig.loadWithUserFile()

   lazy val app: AppConfig = loaded match
    case Right(l)  => l.config
    case Left(err) =>
      scribe.warn(s"warning: $err — going on with the defaults and the environment")
      AppConfig.load()

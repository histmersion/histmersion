package com.histmersion
package cli

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.config.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}

/** The configuration as the CLI reads it: reference.conf < the user config file < the environment, the same layers as
  * the server ([[AppConfig.loadWithUserFile]]). Read on first use, so `--help` never reads a file. A file that does not
  * read is said once on stderr and the CLI goes on with the defaults and the environment: it is a client, and
  * `histmersion config path` must still run to show what is wrong.
  */
@atBoundary(BoundaryKind.Config, "reads the CLI's configuration layers: reference.conf, the user file and the environment")
object Settings:
  type Prefix = Prefix.T
  object Prefix extends Newtype[String, Pure]

  lazy val loaded: Either[Problems, LoadedConfig] = AppConfig.loadWithUserFile()

  lazy val app: AppConfig = loaded match
    case Right(l)  => l.config
    case Left(err) =>
      err.sentences.foreach(s => scribe.warn(s"warning: $s — going on with the defaults and the environment"))
      AppConfig.load()

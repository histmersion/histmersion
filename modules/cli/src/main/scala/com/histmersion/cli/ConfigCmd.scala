package com.histmersion.cli

import com.monovore.decline.*
import com.histmersion.core.*

import java.nio.file.Files

/** The configuration as the CLI reads it: reference.conf < the user config file < the environment,
  * the same layers as the server ([[AppConfig.loadWithUserFile]]). Read on first use, so `--help`
  * never reads a file. A file that does not read is said once on stderr and the CLI goes on with the
  * defaults and the environment: it is a client, and `histmersion config path` must still run to show
  * what is wrong. */
object Settings:
  lazy val loaded: Either[String, LoadedConfig] = AppConfig.loadWithUserFile()

  lazy val app: AppConfig = loaded match
    case Right(l) => l.config
    case Left(err) =>
      System.err.println(s"warning: $err — going on with the defaults and the environment")
      AppConfig.load()

/** `histmersion config …` — the user config file. */
object ConfigCmd:

  private val path = Opts.subcommand("path",
    "Where the user config file is on this machine (printed alone on stdout), whether it exists and reads cleanly; the running server's file is in histmersion about") {
    Opts.unit.map(_ => (_: Client) =>
      val located = UserPaths.configFile(AppConfig.lookup(org.ekrich.config.ConfigFactory.systemEnvironment()))
      located match
        case None => Left(CliError(s"no config file location: HOME is unset (set ${UserPaths.ConfigEnv})"))
        case Some(p) =>
          println(p)
          AppConfig.loadWithUserFile(org.ekrich.config.ConfigFactory.systemEnvironment(), Some(p)) match
            case Left(err) => Left(CliError(err))
            case Right(l) =>
              if !Files.exists(p) then System.err.println("(not there yet: the defaults and the environment apply)")
              else
                l.file.warnings.foreach(w => System.err.println(s"warning: $w"))
                if l.file.unknownKeys.isEmpty then System.err.println("(reads cleanly)")
              Right(()))
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("config", "The user config file: settings over the defaults, under the environment") {
      path
    }

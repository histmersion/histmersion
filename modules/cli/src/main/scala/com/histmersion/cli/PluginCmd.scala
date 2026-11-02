package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.plugins.PluginKit

object PluginCmd:

  /** The install on this machine, wherever the CLI is pointed. */
  private[cli] def install: Either[CliError, LocalInstall] =
    val env = (k: String) => Option(java.lang.System.getenv(k))
    val exe =
      scala.util.Try(ProcessHandle.current().info().command().orElse(null)).toOption.flatMap(Option(_)).map(Path.of(_))
    val cfg = Settings.app
    LocalInstall
      .locate(cfg.cli.plugins, cfg.runtime.dataDir, env, java.lang.System.getProperty("os.name", ""), exe)
      .left
      .map(e => CliError(e, exitCode = ExitCode.Usage))

  private[cli] def local(c: Client): Either[CliError, PluginOps] =
    if !PluginKit.isLoopback(c.serverUrl) then
      Left(
        CliError(
          s"histmersion plugin manages the install on THIS machine, but the CLI is pointed at ${c.serverUrl} — " +
            "run the command on the machine the server runs on",
          exitCode = ExitCode.Usage
        )
      )
    else install.map(i => PluginOps(i, Settings.app.cli.plugins))

  /** The running server's plugins, when it answers. */
  private def running(c: Client): (Option[PluginsReport], Boolean) =
    c.about.fold(_ => (None, false), a => (Some(a.plugins), true))

  private val add = Opts.subcommand(
    "add",
    "Install a plugin on this machine: a local path (copied, with the .pom beside it), an https URL (downloaded, SHA-256 shown) or group:artifact:version from Maven (group::artifact:version = a Scala 3 artifact); the dependencies its POM lists are installed with it, except what Histmersion ships; shows its id, version, SPI, access and dependencies first"
  ) {
    (
      Opts.argument[String]("path|url|group:artifact:version"),
      Opts.flag("yes", "Install without asking (needed when there is no terminal)", "y").orFalse,
      Opts.flag("rebuild", "Relink right after installing (histmersion plugin rebuild)").orFalse,
      Opts.flag("verify", "Require a valid GPG signature (<jar>.asc) even when GPG provenance is not set up").orFalse,
      Opts.flag("unsigned", "Install even when the signature is missing or does not verify").orFalse,
      Opts.option[String]("sha256", "The SHA-256 the jar must have (refused otherwise)").orNone,
      Opts
        .options[String](
          "repo",
          "A Maven repository (a directory or a URL) to look in first, for the plugin and its dependencies; repeatable"
        )
        .orEmpty
    ).mapN { (arg, yes, rebuild, verify, unsigned, sha, repoArgs) => (c: Client) =>
      val repos = repoArgs
        .traverse(r => PluginRepository.parse(r, Files.isDirectory(_)).map(_.value))
        .left
        .map(CliError(_, exitCode = ExitCode.Usage))
      repos.flatMap(rs => local(c).map(_ -> rs)).flatMap { (ops, rs) =>
        ops.add(arg, AddOptions(yes, unsigned, verify, sha, rs)).flatMap { _ =>
          if rebuild then ops.rebuild(Nil)
          else Right(println("the running binary does not have it yet: histmersion plugin rebuild (or plugin status)"))
        }
      }
    }
  }

  private val rm =
    Opts.subcommand("rm", "Remove an installed plugin (by id or jar file name) from this machine's plugins directory") {
      (Opts.argument[String]("id|file"), Opts.flag("rebuild", "Relink right after removing").orFalse).mapN {
        (what, rebuild) => (c: Client) =>
          local(c).flatMap(ops =>
            ops
              .rm(what)
              .flatMap(_ =>
                if rebuild then ops.rebuild(Nil)
                else Right(println("the running binary still has it until: histmersion plugin rebuild"))
              )
          )
      }
    }

  private val list = Opts.subcommand(
    "list",
    "The plugin jars in this machine's plugins directory, and whether the installed binary was linked with each"
  ) {
    Opts.unit.map(_ => (c: Client) => local(c).map(ops => println(ops.list(running(c)._1).mkString("\n"))))
  }

  private val status = Opts.subcommand(
    "status",
    "Compare the plugins directory's digest with the installed binary's and the running server's: is a rebuild (relink) or restart needed? Also the kit and signing"
  ) {
    Opts.unit.map(_ =>
      (c: Client) =>
        local(c).map { ops =>
          val (r, answered) = running(c)
          println(ops.status(r, answered)._2.mkString("\n"))
        }
    )
  }

  private val rebuild = Opts.subcommand(
    "rebuild",
    "Relink the server with the plugins directory (bin/histmersion-relink: pre-flight, native-image, self-check, swap, restart, health; the previous binary comes back on failure)"
  ) {
    (
      Opts.flag("no-restart", "Swap the binary but do not restart the service").orFalse,
      Opts.flag("unsigned", "Go on when the plugins directory does not match its signed manifest").orFalse,
      Opts.flag("ignore-memory", "Build even with less memory than a relink wants").orFalse,
      Opts.flag("jvm", "Switch to JVM mode instead of building (plugins found at each start)").orFalse,
      Opts.flag("check", "Run the pre-flight only").orFalse
    ).mapN { (noRestart, unsigned, ignoreMemory, jvm, check) => (c: Client) =>
      val args = List(
        noRestart -> "--no-restart",
        unsigned -> "--unsigned",
        ignoreMemory -> "--ignore-memory",
        jvm -> "--jvm",
        check -> "--check"
      ).collect { case (true, a) => a }
      local(c).flatMap(_.rebuild(args))
    }
  }

  private val rollback =
    Opts.subcommand("rollback", "Put the previous server binary back (the one before the last relink) and restart") {
      Opts.flag("no-restart", "Do not restart the service").orFalse.map { noRestart => (c: Client) =>
        local(c).flatMap(_.rebuild("--rollback" :: (if noRestart then List("--no-restart") else Nil)))
      }
    }

  private val repo = Opts.subcommand(
    "repo",
    "The Maven repository of this machine's install that a plugin compiles against: the plugin API, histmersion-plugin-platform (the libraries Histmersion provides, at the versions it ships) and the plugin testkit; --url: as a file: URL; --m2: copy it into the local Maven repository (~/.m2/repository), which a build names as m2Local (scala-cli) or Resolver.mavenLocal (sbt)"
  ) {
    (
      Opts.flag("url", "Print it as a file: URL").orFalse,
      Opts
        .flag(
          "m2",
          "Copy the install's artifacts into ~/.m2/repository (the files missing or changed there) and print its path"
        )
        .orFalse
    ).mapN { (asUrl, m2) => (_: Client) =>
      if m2 then
        for
          dir <- PluginAuthoring.mavenLocal(k => Option(java.lang.System.getenv(k)))
          copied <- PluginAuthoring.publishToMavenLocal(install, dir)
        yield
          println(if asUrl then dir.toUri.toString else dir.toString)
          java.lang.System.err.println(if copied.isEmpty then
            s"Histmersion ${PluginBuildVersions.histmersion}'s plugin artifacts are up to date there"
          else
            s"copied ${copied.size} file(s) of Histmersion ${PluginBuildVersions.histmersion}'s plugin artifacts there")
      else
        PluginAuthoring
          .localRepository(install)
          .map(i => println(if asUrl then i.mavenRepo.toUri.toString else i.mavenRepo.toString))
    }
  }

  private val apiJar = Opts.subcommand(
    "api-jar",
    "The path of the plugin API jar in this machine's install's Maven repository; --sources: of its sources jar, with the Scaladoc"
  ) {
    Opts.flag("sources", "The sources jar instead of the jar").orFalse.map { sources => (_: Client) =>
      PluginAuthoring.apiJars(install).flatMap { (jar, src) =>
        if !sources then Right(println(jar))
        else
          Either.cond(
            Files.isRegularFile(src),
            println(src),
            CliError(s"no sources jar beside $jar", exitCode = ExitCode.Refused)
          )
      }
    }
  }

  private val create = Opts.subcommand(
    "new",
    "Write a new plugin project for scala-cli: project.scala (the build), <Name>.scala (the plugin), <Name>.test.scala (its tests) and resources/ (its services file, defaults, native-image metadata)"
  ) {
    (
      Opts.argument[String]("id"),
      Opts
        .option[String]("kind", s"What it adds: ${PluginKind.names} (default ${PluginKind.SourceFetcher.name})")
        .withDefault(PluginKind.SourceFetcher.name),
      Opts.option[String]("dir", "Where to write it (default ./<id>; must not exist or be empty)").orNone,
      Opts.option[String]("package", "The Scala package (default: the id without dashes)").orNone,
      Opts
        .option[String](
          "repo",
          "The Maven repository with the plugin API, platform and testkit to compile against, a directory or a URL (default: m2Local, the local Maven repository, into which this machine's install's are copied first: histmersion plugin repo --m2)"
        )
        .orNone,
      Opts.flag("with-agent-notes", "Also write AGENTS.md (and CLAUDE.md pointing at it) for coding agents").orFalse
    ).mapN { (rawId, rawKind, dir, rawPackage, repoArg, agentNotes) => (_: Client) =>
      val result = for
        id <- PluginId.parse(rawId)
        kind <- PluginKind.parse(rawKind)
        pkg <- ScalaPackage.parse(rawPackage.getOrElse(id.packageName))
      yield (id, kind, pkg)
      result.left.map(CliError(_, exitCode = ExitCode.Usage)).flatMap { (id, kind, pkg) =>
        val target = Path.of(dir.getOrElse(id.value)).toAbsolutePath.normalize
        for
          (repo, copied) <- PluginAuthoring.repository(
            repoArg,
            install,
            PluginAuthoring.mavenLocal(k => Option(java.lang.System.getenv(k)))
          )
          written <- PluginAuthoring.write(
            target,
            PluginTemplate.files(PluginTemplate.Request(id, kind, pkg, repo, agentNotes))
          )
        yield
          if copied.nonEmpty then
            println(
              s"copied the install's plugin API, platform and testkit into the local Maven repository (${copied.size} file(s))"
            )
          PluginAuthoring.nextSteps(target, id, pkg, written).foreach(println)
      }
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand(
      "plugin",
      "Plugins of the Histmersion installed on THIS machine: add, rm, list, status, rebuild, rollback (refused when the CLI points at another machine); new, repo and api-jar for writing one"
    ) {
      add orElse rm orElse list orElse status orElse rebuild orElse rollback orElse create orElse repo orElse apiJar
    }

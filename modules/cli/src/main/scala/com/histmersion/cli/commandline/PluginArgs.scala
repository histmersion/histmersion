package com.histmersion
package cli
package commandline

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.config.*
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line of [[PluginCmd]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion plugin`: declares its options, parses them into typed commands"
)
object PluginArgs:
  object PluginEntry extends CommandLine.Entry:
    val name = "plugin"
    def help: String =
      "Plugins of the Histmersion installed on THIS machine: add, rm, list, status, rebuild, rollback (refused when the CLI points at another machine); new, repo and api-jar for writing one"
    def conf(): Subcommand = GroupPlugin()

  final private class LeafAdd()
      extends Leaf(
        "histmersion plugin",
        "add",
        "Install a plugin on this machine: a local path (copied, with the .pom beside it), an https URL (downloaded, SHA-256 shown) or group:artifact:version from Maven (group::artifact:version = a Scala 3 artifact); the dependencies its POM lists are installed with it, except what Histmersion ships; shows its id, version, SPI, access and dependencies first"
      )
      with SharedFlags:
    private val arg_ = arg[String]("path|url|group:artifact:version")
    private val yes_ = flag("yes", "Install without asking (needed when there is no terminal)", 'y')
    private val rebuild_ = flag("rebuild", "Relink right after installing (histmersion plugin rebuild)")
    private val verify_ =
      flag("verify", "Require a valid GPG signature (<jar>.asc) even when GPG provenance is not set up")
    private val unsigned_ = flag("unsigned", "Install even when the signature is missing or does not verify")
    private val sha_ = value[String]("sha256", "The SHA-256 the jar must have (refused otherwise)")
    private val repoArgs_ = values[String](
      "repo",
      "A Maven repository (a directory or a URL) to look in first, for the plugin and its dependencies; repeatable"
    )

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      PluginCmd.Command.Add(
        arg_(),
        yes_.getOrElse(false),
        rebuild_.getOrElse(false),
        verify_.getOrElse(false),
        unsigned_.getOrElse(false),
        sha_.toOption,
        repoArgs_.getOrElse(Nil)
      )
    )

  final private class LeafRm()
      extends Leaf(
        "histmersion plugin",
        "rm",
        "Remove an installed plugin (by id or jar file name) from this machine's plugins directory"
      )
      with SharedFlags:
    private val what_ = arg[String]("id|file")
    private val rebuild_ = flag("rebuild", "Relink right after removing")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(PluginCmd.Command.Rm(what_(), rebuild_.getOrElse(false)))

  final private class LeafListing()
      extends Leaf(
        "histmersion plugin",
        "list",
        "The plugin jars in this machine's plugins directory, and whether the installed binary was linked with each"
      )
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(PluginCmd.Command.Listing())

  final private class LeafStatus()
      extends Leaf(
        "histmersion plugin",
        "status",
        "Compare the plugins directory's digest with the installed binary's and the running server's: is a rebuild (relink) or restart needed? Also the kit and signing"
      )
      with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(PluginCmd.Command.Status())

  final private class LeafRebuild()
      extends Leaf(
        "histmersion plugin",
        "rebuild",
        "Relink the server with the plugins directory (bin/histmersion-relink: pre-flight, native-image, self-check, swap, restart, health; the previous binary comes back on failure)"
      )
      with SharedFlags:
    private val noRestart_ = flag("no-restart", "Swap the binary but do not restart the service")
    private val unsigned_ = flag("unsigned", "Go on when the plugins directory does not match its signed manifest")
    private val ignoreMemory_ = flag("ignore-memory", "Build even with less memory than a relink wants")
    private val jvm_ = flag("jvm", "Switch to JVM mode instead of building (plugins found at each start)")
    private val check_ = flag("check", "Run the pre-flight only")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      PluginCmd.Command.Rebuild(
        noRestart_.getOrElse(false),
        unsigned_.getOrElse(false),
        ignoreMemory_.getOrElse(false),
        jvm_.getOrElse(false),
        check_.getOrElse(false)
      )
    )

  final private class LeafRollback()
      extends Leaf(
        "histmersion plugin",
        "rollback",
        "Put the previous server binary back (the one before the last relink) and restart"
      )
      with SharedFlags:
    private val noRestart_ = flag("no-restart", "Do not restart the service")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(PluginCmd.Command.Rollback(noRestart_.getOrElse(false)))

  final private class LeafNew()
      extends Leaf(
        "histmersion plugin",
        "new",
        "Write a new plugin project for scala-cli: project.scala (the build), <Name>.scala (the plugin), <Name>.test.scala (its tests) and resources/ (its services file, defaults, native-image metadata)"
      )
      with SharedFlags:
    private val rawId_ = arg[String]("id")
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    private val rawKind_ =
      value[String]("kind", s"What it adds: ${PluginKind.names} (default ${PluginKind.SourceFetcher.name})")
    private val dir_ = value[String]("dir", "Where to write it (default ./<id>; must not exist or be empty)")
    private val rawPackage_ = value[String]("package", "The Scala package (default: the id without dashes)")
    private val repoArg_ = value[String](
      "repo",
      "The Maven repository with the plugin API, platform and testkit to compile against, a directory or a URL (default: m2Local, the local Maven repository, into which this machine's install's are copied first: histmersion plugin repo --m2)"
    )
    private val agentNotes_ =
      flag("with-agent-notes", "Also write AGENTS.md (and CLAUDE.md pointing at it) for coding agents")

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      PluginCmd.Command.New(
        rawId_(),
        rawKind_.getOrElse(PluginKind.SourceFetcher.name.value),
        dir_.toOption,
        rawPackage_.toOption,
        repoArg_.toOption,
        agentNotes_.getOrElse(false)
      )
    )

  final private class LeafRepo()
      extends Leaf(
        "histmersion plugin",
        "repo",
        "The Maven repository of this machine's install that a plugin compiles against: the plugin API, histmersion-plugin-platform (the libraries Histmersion provides, at the versions it ships) and the plugin testkit; --url: as a file: URL; --m2: copy it into the local Maven repository (~/.m2/repository), which a build names as m2Local (scala-cli) or Resolver.mavenLocal (sbt)"
      )
      with SharedFlags:
    private val asUrl_ = flag("url", "Print it as a file: URL")
    private val m2_ = flag(
      "m2",
      "Copy the install's artifacts into ~/.m2/repository (the files missing or changed there) and print its path"
    )

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(
      PluginCmd.Command.Repo(asUrl_.getOrElse(false), m2_.getOrElse(false))
    )

  final private class LeafApiJar()
      extends Leaf(
        "histmersion plugin",
        "api-jar",
        "The path of the plugin API jar in this machine's install's Maven repository; --sources: of its sources jar, with the Scaladoc"
      )
      with SharedFlags:
    private val sources_ = flag("sources", "The sources jar instead of the jar")

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] = Right(PluginCmd.Command.ApiJar(sources_.getOrElse(false)))

  final private class GroupPlugin()
      extends Group(
        "histmersion",
        "plugin",
        "Plugins of the Histmersion installed on THIS machine: add, rm, list, status, rebuild, rollback (refused when the CLI points at another machine); new, repo and api-jar for writing one"
      ):
    holds(
      LeafAdd(),
      LeafRm(),
      LeafListing(),
      LeafStatus(),
      LeafRebuild(),
      LeafRollback(),
      LeafNew(),
      LeafRepo(),
      LeafApiJar()
    )

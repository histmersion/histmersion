package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** `plugin repo`, `plugin api-jar` and `plugin new`: what a plugin author needs from the install on this machine. They
  * read files only, and need no server.
  */
object PluginAuthoring:

  /** The install's Maven repository, when it has one (an install older than plugin dependencies has `plugin-api/`
    * instead).
    */
  def localRepository(install: Either[Cli.Error, LocalInstall]): Either[Cli.Error, LocalInstall] =
    install.flatMap { i =>
      Either.cond(
        Files.isRegularFile(i.apiPom),
        i,
        Cli.Error(
          s"the install at ${i.home} has no plugin Maven repository (${i.mavenRepo}): it predates plugin dependencies — " +
            s"download histmersion-maven-<version>.zip from the release, unzip it and pass its maven/ directory with --repo",
          exitCode = ExitCode.Usage
        )
      )
    }

  /** The local Maven repository, `$HOME/.m2/repository`: where Maven, Gradle, sbt (`Resolver.mavenLocal`) and scala-cli
    * (`m2Local`) look for artifacts installed on this machine.
    */
  def mavenLocal(env: String => Option[String]): Either[Cli.Error, Path] =
    env("HOME")
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(h => Path.of(h, ".m2", "repository"))
      .toRight(
        Cli.Error("HOME is unset: there is no local Maven repository (~/.m2/repository)", exitCode = ExitCode.Usage)
      )

  /** Copy the install's Maven repository into the local one, so that a build naming `m2Local` finds the plugin API, the
    * platform and the testkit: every file that is missing there or differs (a SNAPSHOT rebuilt by a newer install),
    * nothing else. The files copied, relative to the repository.
    */
  def publishToMavenLocal(install: Either[Cli.Error, LocalInstall], m2: Path): Either[Cli.Error, List[Path]] =
    localRepository(install).flatMap { i =>
      scala.util
        .Try {
          val walk = Files.walk(i.mavenRepo)
          val sources = try walk.iterator.asScala.filter(Files.isRegularFile(_)).toList.sortBy(_.toString)
          finally walk.close()
          sources.flatMap { from =>
            val relative = i.mavenRepo.relativize(from)
            val to = m2.resolve(relative.toString)
            if Files.isRegularFile(to) && Files.mismatch(from, to) == -1L then None
            else
              Files.createDirectories(to.getParent)
              Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
              Some(relative)
          }
        }
        .toEither
        .left
        .map(e => Cli.Error(s"could not copy ${i.mavenRepo} into $m2: ${e.getMessage}", exitCode = ExitCode.Refused))
    }

  /** The repository a new project resolves the plugin API, platform and testkit from: the one named with `--repo` (a
    * directory or a URL), else the local Maven repository, into which the install's are copied first (the files
    * copied).
    */
  def repository(
      named: Option[String],
      install: => Either[Cli.Error, LocalInstall],
      m2: => Either[Cli.Error, Path]
  ): Either[Cli.Error, (TemplateRepository, List[Path])] =
    named match
      case Some(raw) =>
        PluginRepository
          .parse(raw, Files.isDirectory(_))
          .left
          .map(Cli.Error(_, exitCode = ExitCode.Usage))
          .map(r => (TemplateRepository.Remote(r), Nil))
      case None =>
        m2.flatMap(dir => publishToMavenLocal(install, dir)).map(copied => (TemplateRepository.MavenLocal, copied))

  /** The plugin API jar in the install's repository, and its sources jar beside it. */
  def apiJars(install: Either[Cli.Error, LocalInstall]): Either[Cli.Error, (Path, Path)] =
    localRepository(install).map(i => (i.apiJar, i.apiSources))

  /** Write a new project into `dir`, which must not exist or be empty; the paths written. */
  def write(dir: Path, files: List[TemplateFile]): Either[Cli.Error, List[Path]] =
    val occupied = Files.exists(dir) && (!Files.isDirectory(dir) || {
      val s = Files.list(dir)
      try s.findAny().isPresent
      finally s.close()
    })
    if occupied then Left(Cli.Error(s"$dir exists and is not empty — choose another --dir", exitCode = ExitCode.Usage))
    else
      Right(files.map { f =>
        val target = dir.resolve(f.path)
        Files.createDirectories(target.getParent)
        Files.writeString(target, f.content, UTF_8)
      })

  /** What to do next, for the person who ran `plugin new`. */
  def nextSteps(dir: Path, id: PluginId, pkg: ScalaPackage, written: List[Path]): List[String] =
    val publishDir = PluginTemplate.PublishDir
    (s"wrote a scala-cli project in $dir:" :: written.map(p => s"  ${dir.relativize(p)}")) ++ List(
      "",
      "next:",
      s"  cd \"$dir\"",
      "  scala-cli test .          # compile it and run its tests",
      "  scala-cli setup-ide .     # then open the directory in VS Code with Metals, or in IntelliJ",
      s"  scala-cli --power publish . --publish-repository ./$publishDir   # the jar and its POM, which lists its dependencies",
      s"  histmersion plugin add ${pkg.value}::${id.value}:${PluginTemplate.FirstVersion} --repo ./$publishDir",
      s"  histmersion plugin rebuild   # and in histmersion.conf: histmersion.plugins.${id.value} { enabled = true }",
      "",
      s"the build is ${PluginTemplate.ProjectFile}: keep every //> using directive there, the test.* ones too, when you add files;",
      "a library of its own: //> using dep group::artifact:version — plugin add installs it beside the plugin;",
      "the plugin API and the libraries Histmersion provides come from the platform (compile-only, never bundled)"
    )

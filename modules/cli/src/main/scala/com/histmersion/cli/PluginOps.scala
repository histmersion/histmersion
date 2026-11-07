package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.util.plugins.PluginKit.{Coordinates, Index, Installed, Origin}

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*
import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.plugins.{PluginJar, PluginSet, PluginsMarkdown, PluginsReport}
import com.histmersion.util.console.Console

/** `histmersion plugin …` — the plugins of the LOCAL install (docs/research-plugins.md §7). These commands manage files
  * in the data directory and run the bundle's `histmersion-relink`; they never touch the database, and they refuse when
  * the CLI is pointed at a server on another machine.
  */
final class PluginOps(using console: Console)(
    install: LocalInstall,
    cfg: CliPluginsConfig,
    run: Runner = Runner.System,
    confirm: PluginOps.ConfirmArg1 => PluginOps.ConfirmResult = PluginOps.ask
):

  private def err(msg: com.histmersion.cli.PluginOps.Problem, code: com.histmersion.cli.Cli.Error.ExitCode = ExitCode.Refused) = Cli.Error(msg, exitCode = code)

  // ── the directory ─────────────────────────────────────────────────────────────────────────────

  def jars: List[Path] =
    if !Files.isDirectory(install.plugins) then Nil
    else
      val s = Files.list(install.plugins)
      try
        s.iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar"))
          .toList
          .sortBy(_.getFileName.toString)
      finally s.close()

  def index: Index =
    if Files.isRegularFile(install.index) then PluginKit.parseIndex(Files.readString(install.index)).getOrElse(Index())
    else Index()

  private def writeIndex(i: Index): Unit =
    Files.createDirectories(install.plugins)
    val tmp = install.index.resolveSibling("index.json.tmp")
    Files.writeString(tmp, PluginKit.renderIndex(i) + "\n")
    val _ = Files.move(tmp, install.index, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

  private def digestOf(p: Path, algorithm: com.histmersion.cli.PluginOps.DigestAlgorithm): com.histmersion.cli.PluginOps.Digest =
    val md = MessageDigest.getInstance(algorithm)
    val in = Files.newInputStream(p)
    try
      val buf = new Array[Byte](1 << 16)
      Iterator.continually(in.read(buf)).takeWhile(_ >= 0).foreach(n => md.update(buf, 0, n))
    finally in.close()
    md.digest().map(b => f"$b%02x").mkString

  def sha256(p: Path): com.histmersion.api.Sha256 = digestOf(p, com.histmersion.cli.PluginOps.DigestAlgorithm("SHA-256"))

  def dirEntries: List[(com.histmersion.cli.PluginOps.EntryName, com.histmersion.cli.PluginOps.EntryContent)] = jars.map(j => j.getFileName.toString -> sha256(j))

  /** The POMs beside the jars and the dependency jars `lib/classpath` lists (as `lib/<name>`), with their SHA-256: what
    * a relink links besides the jars.
    */
  def dependencyEntries: List[(com.histmersion.cli.PluginOps.EntryName, com.histmersion.cli.PluginOps.EntryContent)] =
    val poms = jars.map(pomOf).filter(Files.isRegularFile(_)).map(p => p.getFileName.toString -> sha256(p))
    val listed = scala.util
      .Try(Files.readAllLines(install.lib.resolve("classpath")).asScala.toList)
      .getOrElse(Nil)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(install.lib.resolve)
      .filter(Files.isRegularFile(_))
    poms ++ listed.map(d => s"lib/${d.getFileName}" -> sha256(d))

  def installedSet: Option[PluginSet] =
    if Files.isRegularFile(install.record) then PluginKit.parseSet(Files.readString(install.record)).toOption else None

  def spiVersion: Either[Cli.Error, com.histmersion.cli.PluginOps.SpiVersion] =
    if !Files.isRegularFile(install.kitJson) then Left(err(s"no kit manifest at ${install.kitJson}"))
    else PluginKit.parseKit(Files.readString(install.kitJson)).map(_.spiVersion).left.map(err(_))

  // ── describe (the kit's pluginTool on a JVM, through histmersion-relink) ───────────────────────

  /** What `jar` provides, loaded with the dependency jars of `lib` (`lib/classpath`). */
  def describe(jar: Path, lib: Option[Path] = None): Either[Cli.Error, PluginJar] =
    val withLib = lib.toList.flatMap(l => List("--lib", l.toString))
    run.capture(install.relink.toString :: "--describe" :: withLib ::: List(jar.toString), install.env) match
      case Left((_, e)) => Left(err(s"could not describe ${jar.getFileName}: $e"))
      case Right(json)  =>
        PluginKit
          .parseJars(json)
          .left
          .map(err(_))
          .flatMap(_.headOption.toRight(err(s"${jar.getFileName}: nothing described")))

  // ── fetching ──────────────────────────────────────────────────────────────────────────────────

  private def curl(url: com.histmersion.cli.PluginOps.Url, to: Path): Either[com.histmersion.cli.PluginOps.Problem, Unit] =
    run.capture(List(cfg.curl, "-fsSL", "--retry", "2", "-o", to.toString, url)) match
      case Right(_)      => Right(())
      case Left((22, _)) => Left(s"$url: not found (HTTP error)")
      case Left((rc, e)) => Left(s"$url: curl exited $rc ${e.linesIterator.toList.lastOption.getOrElse("")}".trim)

  /** The POM beside a jar, as the plugin keeps it: `x-1.0.jar` → `x-1.0.pom`. */
  private def pomOf(jar: Path): Path = jar.resolveSibling(jar.getFileName.toString.stripSuffix(".jar") + ".pom")

  /** The jar at `to`, its signature beside it when there is one (`<to>.asc`), and the POM published with it (`<to minus
    * .jar>.pom`, which lists the plugin's dependencies) when there is one.
    */
  private def fetch(origin: Origin, to: Path, wantAsc: com.histmersion.cli.PluginOps.Signature, repos: List[com.histmersion.cli.PluginOps.Repo]): Either[Cli.Error, com.histmersion.cli.PluginOps.Path] =
    origin match
      case Origin.File(path) =>
        val p = Path.of(path)
        if !Files.isRegularFile(p) then Left(err(s"no such file: $path", ExitCode.Usage))
        else
          Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING)
          val asc = Path.of(path + ".asc")
          if Files.isRegularFile(asc) then
            Files.copy(asc, Path.of(to.toString + ".asc"), StandardCopyOption.REPLACE_EXISTING)
          if Files.isRegularFile(pomOf(p)) then Files.copy(pomOf(p), pomOf(to), StandardCopyOption.REPLACE_EXISTING)
          Right(p.toAbsolutePath.normalize.toString)
      case Origin.Url(url) =>
        curl(url, to).left.map(err(_)).map { _ =>
          if wantAsc then curl(url + ".asc", Path.of(to.toString + ".asc")).left.foreach(_ => ())
          if url.endsWith(".jar") then curl(url.stripSuffix(".jar") + ".pom", pomOf(to)).left.foreach(_ => ())
          url
        }
      case Origin.Maven(c) => maven(c, to, wantAsc, repos)

  /** A plugin from a Maven repository: its jar and its POM, each in turn from the repositories, each checked against
    * the checksum the repository publishes beside it (SHA-256 when there is one, else SHA-1, which Maven Central always
    * has). The dependencies the POM lists are the resolver's (`histmersion-relink --resolve`).
    */
  private def maven(c: Coordinates, to: Path, wantAsc: com.histmersion.cli.PluginOps.Signature, repos: List[com.histmersion.cli.PluginOps.Repo]): Either[Cli.Error, com.histmersion.cli.PluginOps.Path] =
    def one(repo: com.histmersion.cli.PluginOps.Repo): Either[com.histmersion.cli.PluginOps.Problem, com.histmersion.cli.PluginOps.Path] =
      val url = c.jarUrl(repo)
      for
        _ <- curl(url, to)
        _ <- checksum(url, to)
        _ <- curl(c.pomUrl(repo), pomOf(to))
        _ <- checksum(c.pomUrl(repo), pomOf(to))
      yield
        if wantAsc then curl(url + ".asc", Path.of(to.toString + ".asc")).left.foreach(_ => ())
        url
    repos
      .foldLeft[Either[List[String], String]](Left(Nil)) {
        case (r @ Right(_), _)  => r
        case (Left(errs), repo) => one(repo).left.map(errs :+ _)
      }
      .left
      .map(errs => err(s"$c: not resolved from ${repos.mkString(", ")}: ${errs.mkString("; ")}"))

  private def checksum(url: com.histmersion.cli.PluginOps.Url, jar: Path): Either[com.histmersion.cli.PluginOps.Problem, Unit] =
    val sum = Path.of(jar.toString + ".sum")
    try
      curl(url + ".sha256", sum)
        .map(_ => "SHA-256")
        .orElse(curl(url + ".sha1", sum).map(_ => "SHA-1"))
        .left
        .map(_ => s"$url: the repository publishes no .sha256 or .sha1 beside it")
        .flatMap { algorithm =>
          val published = Files.readString(sum).trim.split("\\s+").head.toLowerCase
          val actual = digestOf(jar, algorithm)
          Either.cond(actual == published, (), s"$url: $algorithm mismatch (published $published, downloaded $actual)")
        }
    finally { val _ = Files.deleteIfExists(sum) }

  // ── commands ──────────────────────────────────────────────────────────────────────────────────

  /** The repositories an `add` looks in: the ones named for it first, then the configured ones. */
  private def repositories(o: AddOptions): List[com.histmersion.cli.PluginOps.Repo] = (o.repos ++ cfg.repos).distinct

  private def repoArgs(repos: List[com.histmersion.cli.PluginOps.Repo]): List[com.histmersion.cli.PluginOps.Arg] = repos.flatMap(r => List(com.histmersion.cli.PluginOps.Arg("--repo"), r))

  /** The dependencies `jar`'s POM lists, resolved against what the server ships into `lib`: the lines the resolver
    * reports, or why the plugin is refused (a library the server ships at an older version than the plugin needs) or
    * could not be resolved.
    */
  private def dependencies(jar: Path, lib: Path, repos: List[com.histmersion.cli.PluginOps.Repo]): Either[Cli.Error, List[com.histmersion.cli.PluginOps.Dependency]] =
    run.capture(
      install.relink.toString :: "--resolve" :: "--lib" :: lib.toString :: repoArgs(repos) ::: List(jar.toString),
      install.env
    ) match
      case Right(report) => Right(report.linesIterator.map(_.trim).filter(_.nonEmpty).toList)
      case Left((2, e))  =>
        val reasons =
          e.linesIterator.map(_.trim).filter(_.startsWith("refused:")).map(_.stripPrefix("refused:").trim).toList
        val shown = if reasons.nonEmpty then reasons else e.linesIterator.map(_.trim).filter(_.nonEmpty).toList
        Left(err(s"refused:\n${shown.map("  - " + _).mkString("\n")}"))
      case Left((_, e)) => Left(err(s"the plugin's dependencies could not be resolved: $e"))

  /** Resolve the dependencies of every installed plugin into `lib/` again, after the set changed. The relink does it
    * too, so a failure here (no network) only warns.
    */
  private def resolveInstalled(repos: List[com.histmersion.cli.PluginOps.Repo]): Unit =
    run.capture(install.relink.toString :: "--resolve" :: repoArgs(repos), install.env) match
      case Right(_)     => ()
      case Left((_, e)) =>
        Cli.out.println(
          s"warning: the plugins' dependencies were not resolved now ($e) — `histmersion plugin rebuild` resolves them"
        )

  /** `plugin add`: fetch into a staging file (with its POM), verify, resolve its dependencies, describe it with them,
    * refuse what cannot be linked, show what it is and needs, install it under `<id>-<version>.jar` (replacing an older
    * version of the same plugin), its POM beside it and its dependencies in `lib/`.
    */
  def add(arg: com.histmersion.cli.PluginOps.Arg, o: AddOptions): Either[Cli.Error, PluginJar] =
    PluginKit.origin(arg, a => Files.isRegularFile(Path.of(a))).left.map(err(_, ExitCode.Usage)).flatMap(addFrom(_, o))

  /** The name the plugin is staged under: the one it is published with, which the resolver's report and refusals name.
    */
  private def stagedName(origin: Origin): com.histmersion.cli.PluginOps.FileName =
    val raw = origin match
      case Origin.File(path) => Option(Path.of(path).getFileName).map(_.toString).getOrElse("plugin.jar")
      case Origin.Url(url)   => url.split('/').lastOption.filter(_.endsWith(".jar")).getOrElse("plugin.jar")
      case Origin.Maven(c)   => s"${c.artifact}-${c.version}.jar"
    raw.replaceAll("[^A-Za-z0-9._+\\-]", "_")

  private def addFrom(origin: Origin, o: AddOptions): Either[Cli.Error, PluginJar] =
    val work = install.staging.resolve(s"add-${ProcessHandle.current().pid()}")
    deleteTree(work)
    Files.createDirectories(work)
    val stage = work.resolve(stagedName(origin))
    val asc = Path.of(stage.toString + ".asc")
    val lib = work.resolve("lib")
    val repos = repositories(o)
    val verifying = install.signingOn || o.verify
    try
      for
        source <- fetch(origin, stage, wantAsc = verifying, repos)
        sha = sha256(stage)
        _ <- o.sha256
          .map(_.trim.toLowerCase)
          .filter(_ != sha)
          .map(w => err(s"SHA-256 mismatch: expected $w, got $sha — not installed"))
          .toLeft(())
        signer <- signature(stage, asc, verifying, o.unsigned)
        spi <- spiVersion
        deps <- dependencies(stage, lib, repos)
        jar <- describe(stage, Some(lib))
        idx = index
        name = PluginKit.jarName(jar.plugins, Path.of(source).getFileName.toString)
        ids = jar.plugins.map(_.id).toSet
        // another version of the same plugin is REPLACED; any other jar's ids are taken
        replaced = jars
          .filter(j => PluginKit.idsOf(j.getFileName.toString, idx).exists(ids))
          .map(_.getFileName.toString)
        taken = jars
          .map(_.getFileName.toString)
          .filterNot(f => replaced.contains(f) || f == name)
          .flatMap(f => PluginKit.idsOf(f, idx).map(_ -> f))
          .toMap
        _ <- PluginKit.refusals(jar.modify(_.file).setTo(name), spi, taken) match
          case Nil      => Right(())
          case problems => Left(err(s"refused:\n${problems.map("  - " + _).mkString("\n")}"))
        _ <- signer.fold(Right(()))(fpr => PluginKit.checkSigner(jar.plugins.map(_.id), fpr, idx).left.map(err(_)))
        _ = show(jar.modify(_.file).setTo(name), sha, source, signer, replaced, deps)
        _ <- Either.cond(
          o.yes || confirm(s"Install ${jar.plugins.map(p => s"${p.id} ${p.version}").mkString(", ")}?"),
          (),
          err("not installed (answer y, or pass --yes when there is no terminal)", ExitCode.Usage)
        )
      yield
        replaced.filterNot(_ == name).foreach { f =>
          Files.deleteIfExists(install.plugins.resolve(f))
          Files.deleteIfExists(pomOf(install.plugins.resolve(f)))
        }
        val target = install.plugins.resolve(name)
        Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING)
        if Files.isRegularFile(pomOf(stage)) then
          Files.move(pomOf(stage), pomOf(target), StandardCopyOption.REPLACE_EXISTING)
        else Files.deleteIfExists(pomOf(target))
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString
        writeIndex(replaced.foldLeft(idx)(_.drop(_)).put(Installed(name, sha, source, now, jar.plugins, signer)))
        resolveInstalled(repos)
        resign()
        Cli.out.println(s"installed $target")
        jar.modify(_.file).setTo(name)
    finally deleteTree(work)

  private def deleteTree(dir: Path): Unit =
    if Files.isDirectory(dir) then
      val s = Files.walk(dir)
      try s.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally s.close()

  /** The signer's fingerprint when the jar was verified; refused when verification is required and fails. */
  private def signature(
      jar: Path,
      asc: Path,
      verifying: com.histmersion.cli.PluginOps.Verify,
      unsigned: com.histmersion.cli.PluginOps.Unsigned
  ): Either[Cli.Error, Option[com.histmersion.cli.PluginOps.Message]] =
    if !verifying then Right(None)
    else if !Files.isRegularFile(asc) then
      if unsigned then {
        Cli.out.println("warning: no signature (.asc) for this jar — installed unsigned (--unsigned)"); Right(None)
      } else
        Left(
          err(
            "no signature (.asc) found for the jar, and signatures are required " +
              "(GPG provenance is set up, or --verify) — pass --unsigned to install it anyway"
          )
        )
    else
      run.capture(List(install.signing.toString, "verify-file", jar.toString, asc.toString), install.env) match
        case Right(fpr)               => Right(Some(fpr.trim))
        case Left((_, e)) if unsigned =>
          Cli.out.println(s"warning: signature not verified ($e) — installed anyway (--unsigned)"); Right(None)
        case Left((_, e)) => Left(err(s"signature not verified: $e"))

  private def show(
      jar: PluginJar,
      sha: String,
      source: String,
      signer: Option[String],
      replaced: List[String],
      dependencies: List[String]
  ): Unit =
    jar.plugins.foreach { p =>
      Cli.out.println(
        s"plugin ${p.id} ${p.version} · SPI ${p.spiVersion} · access: ${
            if p.access.isEmpty then "none declared" else p.access.mkString(", ")
          }" +
          (if p.provides.isEmpty then "" else s" · provides ${p.provides.mkString(", ")}")
      )
      p.error.foreach(e =>
        Cli.out.println(
          s"  with its defaults alone it does not register: $e (it may need settings under histmersion.plugins.${p.id})"
        )
      )
      if p.access.contains("unauthorised") then
        Cli.out.println(
          "  its access is UNAUTHORISED: it runs only with histmersion.acquire.allow-unauthorised = true, never on the legal-only path"
        )
    }
    Cli.out.println(s"file ${jar.file} · SHA-256 $sha")
    Cli.out.println(s"from $source")
    if dependencies.isEmpty then Cli.out.println("dependencies: none of its own (no POM lists any)")
    else dependencies.foreach(value => Cli.out.println(value))
    Cli.out.println(signer.fold("signature: not checked")(f => s"signature: good, key $f"))
    replaced.filterNot(_ == jar.file).foreach(f => Cli.out.println(s"replaces $f"))

  /** Re-sign the directory's manifest when GPG provenance is set up. */
  private def resign(): Unit =
    if install.signingOn then
      run.capture(List(install.signing.toString, "sign-plugins"), install.env) match
        case Right(m)     => Cli.out.println(m.trim)
        case Left((_, e)) => Cli.out.println(s"warning: the plugins manifest was not re-signed: $e")

  /** `plugin rm`: by plugin id or file name. */
  def rm(what: com.histmersion.cli.PluginOps.RmWhat): Either[Cli.Error, List[com.histmersion.cli.PluginOps.RmResultPart2]] =
    val idx = index
    val hits = jars.map(_.getFileName.toString).filter(f => f == what || PluginKit.idsOf(f, idx).contains(what))
    if hits.isEmpty then Left(err(s"no installed plugin '$what' (histmersion plugin list)", ExitCode.Usage))
    else
      hits.foreach { f =>
        Files.deleteIfExists(install.plugins.resolve(f))
        Files.deleteIfExists(pomOf(install.plugins.resolve(f)))
      }
      writeIndex(hits.foldLeft(idx)(_.drop(_)))
      resolveInstalled(Nil)
      resign()
      hits.foreach(f => Cli.out.println(s"removed $f"))
      Right(hits)

  /** `plugin list`: every jar of the directory, what it holds, whether the installed binary has it. */
  def list(running: Option[PluginsReport]): List[com.histmersion.cli.PluginOps.Listing] =
    val idx = index
    val linked = installedSet.map(_.jars.map(j => j.file -> j.sha256).toMap).getOrElse(Map.empty)
    val rows = jars.map { j =>
      val f = j.getFileName.toString
      val sha = sha256(j)
      val entry = idx.jars.find(_.file == f)
      val what = entry
        .map(_.plugins)
        .filter(_.nonEmpty)
        .map(
          _.map(p => s"${p.id} ${p.version}" + (if p.access.isEmpty then "" else s" (${p.access.mkString(", ")})"))
            .mkString(", ")
        )
        .getOrElse(s"${PluginKit.idsOf(f, idx).mkString(", ")} (dropped in; not described)")
      val state = linked.get(f) match
        case Some(h) if h == sha => "in the binary"
        case Some(_)             => "CHANGED since the link"
        case None                => "NOT in the binary yet"
      s"- $f — $what · $state" + entry
        .fold("")(e => s" · from ${e.source}") + entry.flatMap(_.signer).fold("")(s => s" · signed $s")
    }
    val gone = linked.keys
      .filterNot(f => jars.exists(_.getFileName.toString == f))
      .toList
      .sorted
      .map(f => s"- $f — removed from the directory, still in the binary")
    val deps =
      scala.util.Try(Files.readAllLines(install.lib.resolve("classpath")).asScala.count(_.trim.nonEmpty)).getOrElse(0)
    val header = s"# Plugins of ${install.home}\n\n- directory: ${install.plugins}" +
      (if deps == 0 then "" else s"\n- dependencies: $deps jar(s) in ${install.lib}")
    val firstParty = running.toList.flatMap(r =>
      val user = r.set.toList.flatMap(_.jars.flatMap(_.plugins.map(_.id))).toSet
      r.plugins.filterNot(p => user(p.id)).map(p => s"- (first-party) ${PluginsMarkdown.line(p)}")
    )
    header :: "" :: (if rows.isEmpty && gone.isEmpty then List("- no user plugins installed") else rows ++ gone) ++
      (if firstParty.isEmpty then Nil else "" :: "## Built into every binary" :: "" :: firstParty)

  /** `plugin status`: the directory, the installed binary and the running server compared. */
  def status(running: Option[PluginsReport], serverAnswered: com.histmersion.cli.PluginOps.ServerAnswered): (PluginKit.SetStatus, List[com.histmersion.cli.PluginOps.Message]) =
    val st = PluginKit.status(dirEntries, installedSet, running.flatMap(_.set), serverAnswered, dependencyEntries)
    val kit = PluginKit.parseKit(scala.util.Try(Files.readString(install.kitJson)).getOrElse("")).toOption
    val signing = run
      .capture(List(install.signing.toString, "status"), install.env)
      .toOption
      .toList
      .flatMap(_.linesIterator.filter(_.nonEmpty).toList)
    (
      st,
      (s"# Plugin status of ${install.home}" :: "" ::
        kit.fold("- kit: none")(k => s"- kit: ${k.label} · GraalVM ${k.graalvm} · plugin SPI ${k.spiVersion}") ::
        PluginKit.statusLines(st, serverAnswered)) ++ signing
    )

  def rebuild(args: List[com.histmersion.cli.PluginOps.Arg]): Either[Cli.Error, Unit] =
    run.interactive(install.relink.toString :: args, install.env) match
      case 0  => Right(())
      case rc =>
        Left(
          Cli.Error(
            s"histmersion-relink exited $rc (log: ${install.data.resolve("logs").resolve("relink.log")})",
            exitCode = if rc == 1 then ExitCode.Usage else ExitCode.Refused
          )
        )

object PluginOps:
  type Arg = Arg.T
  object Arg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dependency = Dependency.T
  object Dependency extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Digest = Digest.T
  object Digest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DigestAlgorithm = DigestAlgorithm.T
  object DigestAlgorithm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryContent = EntryContent.T
  object EntryContent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryName = EntryName.T
  object EntryName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileName = FileName.T
  object FileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Listing = Listing.T
  object Listing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Message = Message.T
  object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Repo = Repo.T
  object Repo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ServerAnswered = ServerAnswered.T
  object ServerAnswered extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Signature = Signature.T
  object Signature extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpiVersion = SpiVersion.T
  object SpiVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unsigned = Unsigned.T
  object Unsigned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Verify = Verify.T
  object Verify extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Ask on the terminal; no terminal = no. */

  type ConfirmArg1 = com.histmersion.cli.Cli.Question
  val ConfirmArg1: _root_.com.histmersion.cli.Cli.Question.type = _root_.com.histmersion.cli.Cli.Question
  type ConfirmResult = ConfirmResult.T
  object ConfirmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RmResultPart2 = RmResultPart2.T
  object RmResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RmWhat = RmWhat.T
  object RmWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def ask(question: com.histmersion.cli.Cli.Question)(using console: Console): Boolean =
    console.readTerminalLine(s"$question [y/N] ").map(_.trim.toLowerCase).exists(a => a == "y" || a == "yes")

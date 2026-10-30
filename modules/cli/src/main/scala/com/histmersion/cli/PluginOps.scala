package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.files.PluginKit.{Coordinates, Index, Installed, Origin}

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*

/** `histmersion plugin …` — the plugins of the LOCAL install (docs/research-plugins.md §7). These commands manage files
  * in the data directory and run the bundle's `histmersion-relink`; they never touch the database, and they refuse when
  * the CLI is pointed at a server on another machine.
  */
final class PluginOps(
    install: LocalInstall,
    cfg: CliPluginsConfig,
    run: Runner = Runner.System,
    out: String => Unit = s => println(s),
    confirm: String => Boolean = PluginOps.ask
):

  private def err(msg: String, code: Int = ExitCode.Refused) = CliError(msg, exitCode = code)

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

  private def digestOf(p: Path, algorithm: String): String =
    val md = MessageDigest.getInstance(algorithm)
    val in = Files.newInputStream(p)
    try
      val buf = new Array[Byte](1 << 16)
      Iterator.continually(in.read(buf)).takeWhile(_ >= 0).foreach(n => md.update(buf, 0, n))
    finally in.close()
    md.digest().map(b => f"$b%02x").mkString

  def sha256(p: Path): String = digestOf(p, "SHA-256")

  def dirEntries: List[(String, String)] = jars.map(j => j.getFileName.toString -> sha256(j))

  /** The POMs beside the jars and the dependency jars `lib/classpath` lists (as `lib/<name>`), with their SHA-256: what
    * a relink links besides the jars.
    */
  def dependencyEntries: List[(String, String)] =
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

  def spiVersion: Either[CliError, Int] =
    if !Files.isRegularFile(install.kitJson) then Left(err(s"no kit manifest at ${install.kitJson}"))
    else PluginKit.parseKit(Files.readString(install.kitJson)).map(_.spiVersion).left.map(err(_))

  // ── describe (the kit's pluginTool on a JVM, through histmersion-relink) ───────────────────────

  /** What `jar` provides, loaded with the dependency jars of `lib` (`lib/classpath`). */
  def describe(jar: Path, lib: Option[Path] = None): Either[CliError, PluginJar] =
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

  private def curl(url: String, to: Path): Either[String, Unit] =
    run.capture(List(cfg.curl, "-fsSL", "--retry", "2", "-o", to.toString, url)) match
      case Right(_)      => Right(())
      case Left((22, _)) => Left(s"$url: not found (HTTP error)")
      case Left((rc, e)) => Left(s"$url: curl exited $rc ${e.linesIterator.toList.lastOption.getOrElse("")}".trim)

  /** The POM beside a jar, as the plugin keeps it: `x-1.0.jar` → `x-1.0.pom`. */
  private def pomOf(jar: Path): Path = jar.resolveSibling(jar.getFileName.toString.stripSuffix(".jar") + ".pom")

  /** The jar at `to`, its signature beside it when there is one (`<to>.asc`), and the POM published with it (`<to minus
    * .jar>.pom`, which lists the plugin's dependencies) when there is one.
    */
  private def fetch(origin: Origin, to: Path, wantAsc: Boolean, repos: List[String]): Either[CliError, String] =
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
  private def maven(c: Coordinates, to: Path, wantAsc: Boolean, repos: List[String]): Either[CliError, String] =
    def one(repo: String): Either[String, String] =
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

  private def checksum(url: String, jar: Path): Either[String, Unit] =
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
  private def repositories(o: AddOptions): List[String] = (o.repos ++ cfg.repos).distinct

  private def repoArgs(repos: List[String]): List[String] = repos.flatMap(r => List("--repo", r))

  /** The dependencies `jar`'s POM lists, resolved against what the server ships into `lib`: the lines the resolver
    * reports, or why the plugin is refused (a library the server ships at an older version than the plugin needs) or
    * could not be resolved.
    */
  private def dependencies(jar: Path, lib: Path, repos: List[String]): Either[CliError, List[String]] =
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
  private def resolveInstalled(repos: List[String]): Unit =
    run.capture(install.relink.toString :: "--resolve" :: repoArgs(repos), install.env) match
      case Right(_)     => ()
      case Left((_, e)) =>
        out(
          s"warning: the plugins' dependencies were not resolved now ($e) — `histmersion plugin rebuild` resolves them"
        )

  /** `plugin add`: fetch into a staging file (with its POM), verify, resolve its dependencies, describe it with them,
    * refuse what cannot be linked, show what it is and needs, install it under `<id>-<version>.jar` (replacing an older
    * version of the same plugin), its POM beside it and its dependencies in `lib/`.
    */
  def add(arg: String, o: AddOptions): Either[CliError, PluginJar] =
    PluginKit.origin(arg, a => Files.isRegularFile(Path.of(a))).left.map(err(_, ExitCode.Usage)).flatMap(addFrom(_, o))

  /** The name the plugin is staged under: the one it is published with, which the resolver's report and refusals name.
    */
  private def stagedName(origin: Origin): String =
    val raw = origin match
      case Origin.File(path) => Option(Path.of(path).getFileName).map(_.toString).getOrElse("plugin.jar")
      case Origin.Url(url)   => url.split('/').lastOption.filter(_.endsWith(".jar")).getOrElse("plugin.jar")
      case Origin.Maven(c)   => s"${c.artifact}-${c.version}.jar"
    raw.replaceAll("[^A-Za-z0-9._+\\-]", "_")

  private def addFrom(origin: Origin, o: AddOptions): Either[CliError, PluginJar] =
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
        _ <- PluginKit.refusals(jar.copy(file = name), spi, taken) match
          case Nil      => Right(())
          case problems => Left(err(s"refused:\n${problems.map("  - " + _).mkString("\n")}"))
        _ <- signer.fold(Right(()))(fpr => PluginKit.checkSigner(jar.plugins.map(_.id), fpr, idx).left.map(err(_)))
        _ = show(jar.copy(file = name), sha, source, signer, replaced, deps)
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
        out(s"installed $target")
        jar.copy(file = name)
    finally deleteTree(work)

  private def deleteTree(dir: Path): Unit =
    if Files.isDirectory(dir) then
      val s = Files.walk(dir)
      try s.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally s.close()

  /** The signer's fingerprint when the jar was verified; refused when verification is required and fails. */
  private def signature(jar: Path, asc: Path, verifying: Boolean, unsigned: Boolean): Either[CliError, Option[String]] =
    if !verifying then Right(None)
    else if !Files.isRegularFile(asc) then
      if unsigned then {
        out("warning: no signature (.asc) for this jar — installed unsigned (--unsigned)"); Right(None)
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
          out(s"warning: signature not verified ($e) — installed anyway (--unsigned)"); Right(None)
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
      out(
        s"plugin ${p.id} ${p.version} · SPI ${p.spiVersion} · access: ${
            if p.access.isEmpty then "none declared" else p.access.mkString(", ")
          }" +
          (if p.provides.isEmpty then "" else s" · provides ${p.provides.mkString(", ")}")
      )
      p.error.foreach(e =>
        out(
          s"  with its defaults alone it does not register: $e (it may need settings under histmersion.plugins.${p.id})"
        )
      )
      if p.access.contains("unauthorised") then
        out(
          "  its access is UNAUTHORISED: it runs only with histmersion.acquire.allow-unauthorised = true, never on the legal-only path"
        )
    }
    out(s"file ${jar.file} · SHA-256 $sha")
    out(s"from $source")
    if dependencies.isEmpty then out("dependencies: none of its own (no POM lists any)")
    else dependencies.foreach(out)
    out(signer.fold("signature: not checked")(f => s"signature: good, key $f"))
    replaced.filterNot(_ == jar.file).foreach(f => out(s"replaces $f"))

  /** Re-sign the directory's manifest when GPG provenance is set up. */
  private def resign(): Unit =
    if install.signingOn then
      run.capture(List(install.signing.toString, "sign-plugins"), install.env) match
        case Right(m)     => out(m.trim)
        case Left((_, e)) => out(s"warning: the plugins manifest was not re-signed: $e")

  /** `plugin rm`: by plugin id or file name. */
  def rm(what: String): Either[CliError, List[String]] =
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
      hits.foreach(f => out(s"removed $f"))
      Right(hits)

  /** `plugin list`: every jar of the directory, what it holds, whether the installed binary has it. */
  def list(running: Option[PluginsReport]): List[String] =
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
  def status(running: Option[PluginsReport], serverAnswered: Boolean): (PluginKit.SetStatus, List[String]) =
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

  def rebuild(args: List[String]): Either[CliError, Unit] =
    run.interactive(install.relink.toString :: args, install.env) match
      case 0  => Right(())
      case rc =>
        Left(
          CliError(
            s"histmersion-relink exited $rc (log: ${install.data.resolve("logs").resolve("relink.log")})",
            exitCode = if rc == 1 then ExitCode.Usage else ExitCode.Refused
          )
        )

object PluginOps:
  /** Ask on the terminal; no terminal = no. */
  def ask(question: String): Boolean =
    Option(java.lang.System.console()).exists { c =>
      val a = Option(c.readLine(s"$question [y/N] ")).getOrElse("").trim.toLowerCase
      a == "y" || a == "yes"
    }

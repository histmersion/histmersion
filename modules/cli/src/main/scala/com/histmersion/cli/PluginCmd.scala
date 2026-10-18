package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*
import com.histmersion.core.PluginKit.{Coordinates, Index, Installed, Origin}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*

/** The Histmersion installed on THIS machine, as the plugin commands see it: the bundle (`bin/`,
  * `kit/`) and the data directory (`plugins/`, `signing/`). */
final case class LocalInstall(home: Path, data: Path):
  def bin: Path           = home.resolve("bin")
  def relink: Path        = bin.resolve("histmersion-relink")
  def signing: Path       = bin.resolve("histmersion-signing")
  def kitJson: Path       = home.resolve("kit").resolve("kit.json")
  def record: Path        = bin.resolve("histmersion-server.plugin-set.json")
  def plugins: Path       = data.resolve("plugins")
  def index: Path         = plugins.resolve("index.json")
  def staging: Path       = plugins.resolve(".staging")
  def signingOn: Boolean  = scala.util.Try(Files.size(data.resolve("signing").resolve("gpg-key")) > 0).getOrElse(false)
  /** What the scripts need to act on THIS install and data directory, whatever the caller's environment. */
  def env: Map[String, String] = Map("HISTMERSION_HOME" -> home.toString, "HISTMERSION_DATA_DIR" -> data.toString)

object LocalInstall:
  /** The install: `cli.plugins.install-dir`, else the one this executable sits in (`<home>/bin/histmersion`,
    * through the `~/bin` symlink), else the platform default. It must hold the relink kit. */
  def locate(cfg: CliPluginsConfig, dataDir: String, env: String => Option[String], osName: String,
      exe: Option[Path]): Either[String, LocalInstall] =
    def kitAt(p: Path) = Files.isRegularFile(p.resolve("bin").resolve("histmersion-relink"))
    val home = env("HOME").getOrElse("")
    val configured = Option(cfg.installDir).map(_.trim).filter(_.nonEmpty).map(p =>
      Path.of(if p == "~" || p.startsWith("~/") then home + p.drop(1) else p))
    val fromExe = exe.flatMap(e => scala.util.Try(e.toRealPath()).toOption)
      .flatMap(e => Option(e.getParent)).flatMap(b => Option(b.getParent)).filter(kitAt)
    val default =
      if osName.toLowerCase.contains("mac") then Path.of(home, "Applications", "Histmersion")
      else Path.of(home, ".local", "opt", "histmersion")
    val install = configured.orElse(fromExe).getOrElse(default)
    for
      d <- UserPaths.dataDir(dataDir, env, osName).toRight("no data directory (HOME is unset; set HISTMERSION_DATA_DIR)")
      _ <- Either.cond(kitAt(install), (), s"no Histmersion install with a relink kit at $install " +
             "(set cli.plugins.install-dir / HISTMERSION_INSTALL_DIR, or install a bundle built with the kit)")
    yield LocalInstall(install.toAbsolutePath.normalize, d.toAbsolutePath.normalize)

/** Running the kit's scripts and curl. Behind a trait so the commands can be tested without them. */
trait Runner:
  /** stdout on success; on failure the exit code and stderr. */
  def capture(cmd: List[String], env: Map[String, String] = Map.empty): Either[(Int, String), String]
  /** The process's output goes to this terminal; its exit code. */
  def interactive(cmd: List[String], env: Map[String, String] = Map.empty): Int

object Runner:
  object System extends Runner:
    def capture(cmd: List[String], env: Map[String, String]) =
      scala.util.Try {
        val pb = new ProcessBuilder(cmd.asJava)
        env.foreach((k, v) => pb.environment().put(k, v))
        val p   = pb.start()
        // drain stderr beside stdout: a full stderr pipe would block the child
        val err = java.util.concurrent.CompletableFuture.supplyAsync(() => new String(p.getErrorStream.readAllBytes, UTF_8))
        val out = new String(p.getInputStream.readAllBytes, UTF_8)
        val rc  = p.waitFor()
        if rc == 0 then Right(out) else Left(rc -> err.get().trim.take(2000))
      }.fold(e => Left(127 -> s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"), identity)

    def interactive(cmd: List[String], env: Map[String, String]) =
      scala.util.Try {
        val pb = new ProcessBuilder(cmd.asJava).inheritIO()
        env.foreach((k, v) => pb.environment().put(k, v))
        pb.start().waitFor()
      }.getOrElse(127)

/** What `plugin add` is told. */
final case class AddOptions(yes: Boolean = false, unsigned: Boolean = false, verify: Boolean = false,
    sha256: Option[String] = None)

/** `histmersion plugin …` — the plugins of the LOCAL install (docs/research-plugins.md §7). These
  * commands manage files in the data directory and run the bundle's `histmersion-relink`; they never
  * touch the database, and they refuse when the CLI is pointed at a server on another machine. */
final class PluginOps(install: LocalInstall, cfg: CliPluginsConfig, run: Runner = Runner.System,
    out: String => Unit = s => println(s), confirm: String => Boolean = PluginOps.ask):

  private def err(msg: String, code: Int = ExitCode.Refused) = CliError(msg, exitCode = code)

  // ── the directory ─────────────────────────────────────────────────────────────────────────────

  def jars: List[Path] =
    if !Files.isDirectory(install.plugins) then Nil
    else
      val s = Files.list(install.plugins)
      try s.iterator().asScala.filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar")).toList
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

  def installedSet: Option[PluginSet] =
    if Files.isRegularFile(install.record) then PluginKit.parseSet(Files.readString(install.record)).toOption else None

  def spiVersion: Either[CliError, Int] =
    if !Files.isRegularFile(install.kitJson) then Left(err(s"no kit manifest at ${install.kitJson}"))
    else PluginKit.parseKit(Files.readString(install.kitJson)).map(_.spiVersion).left.map(err(_))

  // ── describe (the kit's PluginTool on a JVM, through histmersion-relink) ───────────────────────

  def describe(jar: Path): Either[CliError, PluginJar] =
    run.capture(List(install.relink.toString, "--describe", jar.toString), install.env) match
      case Left((_, e)) => Left(err(s"could not describe ${jar.getFileName}: $e"))
      case Right(json) =>
        PluginKit.parseJars(json).left.map(err(_)).flatMap(_.headOption.toRight(err(s"${jar.getFileName}: nothing described")))

  // ── fetching ──────────────────────────────────────────────────────────────────────────────────

  private def curl(url: String, to: Path): Either[String, Unit] =
    run.capture(List(cfg.curl, "-fsSL", "--retry", "2", "-o", to.toString, url)) match
      case Right(_)       => Right(())
      case Left((22, _))  => Left(s"$url: not found (HTTP error)")
      case Left((rc, e))  => Left(s"$url: curl exited $rc ${e.linesIterator.toList.lastOption.getOrElse("")}".trim)

  /** The jar at `to`, and its signature beside it when there is one (`<to>.asc`). */
  private def fetch(origin: Origin, to: Path, wantAsc: Boolean): Either[CliError, String] = origin match
    case Origin.File(path) =>
      val p = Path.of(path)
      if !Files.isRegularFile(p) then Left(err(s"no such file: $path", ExitCode.Usage))
      else
        Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING)
        val asc = Path.of(path + ".asc")
        if Files.isRegularFile(asc) then Files.copy(asc, Path.of(to.toString + ".asc"), StandardCopyOption.REPLACE_EXISTING)
        Right(p.toAbsolutePath.normalize.toString)
    case Origin.Url(url) =>
      curl(url, to).left.map(err(_)).map { _ =>
        if wantAsc then curl(url + ".asc", Path.of(to.toString + ".asc")).left.foreach(_ => ())
        url
      }
    case Origin.Maven(c) => maven(c, to, wantAsc)

  /** A minimal Maven resolver: a plugin jar shades what the host does not ship (§7.1), so the one
    * artifact is all there is to resolve — no POM walk, no transitive dependencies, no coursier. Each
    * repository in turn; the jar is checked against the checksum the repository publishes beside it
    * (SHA-256 when there is one, else SHA-1, which Maven Central always has). */
  private def maven(c: Coordinates, to: Path, wantAsc: Boolean): Either[CliError, String] =
    val repos = cfg.repos
    def one(repo: String): Either[String, String] =
      val url = c.jarUrl(repo)
      for
        _ <- curl(url, to)
        _ <- checksum(url, to)
      yield
        if wantAsc then curl(url + ".asc", Path.of(to.toString + ".asc")).left.foreach(_ => ())
        url
    repos.foldLeft[Either[List[String], String]](Left(Nil)) {
      case (r @ Right(_), _) => r
      case (Left(errs), repo) => one(repo).left.map(errs :+ _)
    }.left.map(errs => err(s"$c: not resolved from ${repos.mkString(", ")}: ${errs.mkString("; ")}"))

  private def checksum(url: String, jar: Path): Either[String, Unit] =
    val sum = Path.of(jar.toString + ".sum")
    try
      curl(url + ".sha256", sum).map(_ => "SHA-256").orElse(curl(url + ".sha1", sum).map(_ => "SHA-1"))
        .left.map(_ => s"$url: the repository publishes no .sha256 or .sha1 beside it")
        .flatMap { algorithm =>
          val published = Files.readString(sum).trim.split("\\s+").head.toLowerCase
          val actual    = digestOf(jar, algorithm)
          Either.cond(actual == published, (), s"$url: $algorithm mismatch (published $published, downloaded $actual)")
        }
    finally { val _ = Files.deleteIfExists(sum) }

  // ── commands ──────────────────────────────────────────────────────────────────────────────────

  /** `plugin add`: fetch into a staging file, verify, describe, refuse what cannot be linked, show
    * what it is, install it under `<id>-<version>.jar` (replacing an older version of the same plugin). */
  def add(arg: String, o: AddOptions): Either[CliError, PluginJar] =
    Files.createDirectories(install.staging)
    val stage = install.staging.resolve(s"add-${ProcessHandle.current().pid()}.jar")
    val asc   = Path.of(stage.toString + ".asc")
    val verifying = install.signingOn || o.verify
    try
      for
        origin <- PluginKit.origin(arg, a => Files.isRegularFile(Path.of(a))).left.map(err(_, ExitCode.Usage))
        source <- fetch(origin, stage, wantAsc = verifying)
        sha     = sha256(stage)
        _ <- o.sha256.map(_.trim.toLowerCase).filter(_ != sha)
               .map(w => err(s"SHA-256 mismatch: expected $w, got $sha — not installed")).toLeft(())
        signer <- signature(stage, asc, verifying, o.unsigned)
        spi    <- spiVersion
        jar    <- describe(stage)
        idx     = index
        name    = PluginKit.jarName(jar.plugins, Path.of(source).getFileName.toString)
        ids     = jar.plugins.map(_.id).toSet
        // another version of the same plugin is REPLACED; any other jar's ids are taken
        replaced = jars.filter(j => PluginKit.idsOf(j.getFileName.toString, idx).exists(ids)).map(_.getFileName.toString)
        taken    = jars.map(_.getFileName.toString).filterNot(f => replaced.contains(f) || f == name)
                     .flatMap(f => PluginKit.idsOf(f, idx).map(_ -> f)).toMap
        _ <- PluginKit.refusals(jar.copy(file = name), spi, taken) match
               case Nil      => Right(())
               case problems => Left(err(s"refused:\n${problems.map("  - " + _).mkString("\n")}"))
        _ <- signer.fold(Right(()))(fpr => PluginKit.checkSigner(jar.plugins.map(_.id), fpr, idx).left.map(err(_)))
        _  = show(jar.copy(file = name), sha, source, signer, replaced)
        _ <- Either.cond(o.yes || confirm(s"Install ${jar.plugins.map(p => s"${p.id} ${p.version}").mkString(", ")}?"),
               (), err("not installed (answer y, or pass --yes when there is no terminal)", ExitCode.Usage))
      yield
        replaced.filterNot(_ == name).foreach(f => Files.deleteIfExists(install.plugins.resolve(f)))
        Files.move(stage, install.plugins.resolve(name), StandardCopyOption.REPLACE_EXISTING)
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString
        writeIndex(replaced.foldLeft(idx)(_.drop(_)).put(Installed(name, sha, source, now, jar.plugins, signer)))
        resign()
        out(s"installed ${install.plugins.resolve(name)}")
        jar.copy(file = name)
    finally
      val _ = Files.deleteIfExists(stage)
      val _ = Files.deleteIfExists(asc)

  /** The signer's fingerprint when the jar was verified; refused when verification is required and fails. */
  private def signature(jar: Path, asc: Path, verifying: Boolean, unsigned: Boolean): Either[CliError, Option[String]] =
    if !verifying then Right(None)
    else if !Files.isRegularFile(asc) then
      if unsigned then { out("warning: no signature (.asc) for this jar — installed unsigned (--unsigned)"); Right(None) }
      else Left(err("no signature (.asc) found for the jar, and signatures are required " +
        "(GPG provenance is set up, or --verify) — pass --unsigned to install it anyway"))
    else
      run.capture(List(install.signing.toString, "verify-file", jar.toString, asc.toString), install.env) match
        case Right(fpr) => Right(Some(fpr.trim))
        case Left((_, e)) if unsigned => out(s"warning: signature not verified ($e) — installed anyway (--unsigned)"); Right(None)
        case Left((_, e)) => Left(err(s"signature not verified: $e"))

  private def show(jar: PluginJar, sha: String, source: String, signer: Option[String], replaced: List[String]): Unit =
    jar.plugins.foreach { p =>
      out(s"plugin ${p.id} ${p.version} · SPI ${p.spiVersion} · access: ${if p.access.isEmpty then "none declared" else p.access.mkString(", ")}" +
        (if p.provides.isEmpty then "" else s" · provides ${p.provides.mkString(", ")}"))
      p.error.foreach(e => out(s"  with its defaults alone it does not register: $e (it may need settings under histmersion.plugins.${p.id})"))
      if p.access.contains("unauthorised") then
        out("  its access is UNAUTHORISED: it runs only with histmersion.acquire.allow-unauthorised = true, never on the legal-only path")
    }
    out(s"file ${jar.file} · SHA-256 $sha")
    out(s"from $source")
    out(signer.fold("signature: not checked")(f => s"signature: good, key $f"))
    replaced.filterNot(_ == jar.file).foreach(f => out(s"replaces $f"))

  /** Re-sign the directory's manifest when GPG provenance is set up. */
  private def resign(): Unit =
    if install.signingOn then
      run.capture(List(install.signing.toString, "sign-plugins"), install.env) match
        case Right(m)      => out(m.trim)
        case Left((_, e))  => out(s"warning: the plugins manifest was not re-signed: $e")

  /** `plugin rm`: by plugin id or file name. */
  def rm(what: String): Either[CliError, List[String]] =
    val idx  = index
    val hits = jars.map(_.getFileName.toString).filter(f => f == what || PluginKit.idsOf(f, idx).contains(what))
    if hits.isEmpty then Left(err(s"no installed plugin '$what' (histmersion plugin list)", ExitCode.Usage))
    else
      hits.foreach(f => Files.deleteIfExists(install.plugins.resolve(f)))
      writeIndex(hits.foldLeft(idx)(_.drop(_)))
      resign()
      hits.foreach(f => out(s"removed $f"))
      Right(hits)

  /** `plugin list`: every jar of the directory, what it holds, whether the installed binary has it. */
  def list(running: Option[PluginsReport]): List[String] =
    val idx    = index
    val linked = installedSet.map(_.jars.map(j => j.file -> j.sha256).toMap).getOrElse(Map.empty)
    val rows = jars.map { j =>
      val f   = j.getFileName.toString
      val sha = sha256(j)
      val entry = idx.jars.find(_.file == f)
      val what = entry.map(_.plugins).filter(_.nonEmpty).map(_.map(p =>
        s"${p.id} ${p.version}" + (if p.access.isEmpty then "" else s" (${p.access.mkString(", ")})")).mkString(", "))
        .getOrElse(s"${PluginKit.idsOf(f, idx).mkString(", ")} (dropped in; not described)")
      val state = linked.get(f) match
        case Some(h) if h == sha => "in the binary"
        case Some(_)             => "CHANGED since the link"
        case None                => "NOT in the binary yet"
      s"- $f — $what · $state" + entry.fold("")(e => s" · from ${e.source}") + entry.flatMap(_.signer).fold("")(s => s" · signed $s")
    }
    val gone = linked.keys.filterNot(f => jars.exists(_.getFileName.toString == f)).toList.sorted
      .map(f => s"- $f — removed from the directory, still in the binary")
    val header = s"# Plugins of ${install.home}\n\n- directory: ${install.plugins}"
    val firstParty = running.toList.flatMap(r =>
      val user = r.set.toList.flatMap(_.jars.flatMap(_.plugins.map(_.id))).toSet
      r.plugins.filterNot(p => user(p.id)).map(p => s"- (first-party) ${PluginsMarkdown.line(p)}"))
    header :: "" :: (if rows.isEmpty && gone.isEmpty then List("- no user plugins installed") else rows ++ gone) ++
      (if firstParty.isEmpty then Nil else "" :: "## Built into every binary" :: "" :: firstParty)

  /** `plugin status`: the directory, the installed binary and the running server compared. */
  def status(running: Option[PluginsReport], serverAnswered: Boolean): (PluginKit.SetStatus, List[String]) =
    val st = PluginKit.status(dirEntries, installedSet, running.flatMap(_.set), serverAnswered)
    val kit = PluginKit.parseKit(scala.util.Try(Files.readString(install.kitJson)).getOrElse("")).toOption
    val signing = run.capture(List(install.signing.toString, "status"), install.env).toOption.toList
      .flatMap(_.linesIterator.filter(_.nonEmpty).toList)
    (st, (s"# Plugin status of ${install.home}" :: "" ::
      kit.fold("- kit: none")(k => s"- kit: ${k.label} · GraalVM ${k.graalvm} · plugin SPI ${k.spiVersion}") ::
      PluginKit.statusLines(st, serverAnswered)) ++ signing)

  def rebuild(args: List[String]): Either[CliError, Unit] =
    run.interactive(install.relink.toString :: args, install.env) match
      case 0  => Right(())
      case rc => Left(CliError(s"histmersion-relink exited $rc (log: ${install.data.resolve("logs").resolve("relink.log")})",
                   exitCode = if rc == 1 then ExitCode.Usage else ExitCode.Refused))

object PluginOps:
  /** Ask on the terminal; no terminal = no. */
  def ask(question: String): Boolean =
    Option(java.lang.System.console()).exists { c =>
      val a = Option(c.readLine(s"$question [y/N] ")).getOrElse("").trim.toLowerCase
      a == "y" || a == "yes"
    }

object PluginCmd:

  private[cli] def local(c: Client): Either[CliError, PluginOps] =
    if !PluginKit.isLoopback(c.serverUrl) then
      Left(CliError(s"histmersion plugin manages the install on THIS machine, but the CLI is pointed at ${c.serverUrl} — " +
        "run the command on the machine the server runs on", exitCode = ExitCode.Usage))
    else
      val env = (k: String) => Option(java.lang.System.getenv(k))
      val exe = scala.util.Try(ProcessHandle.current().info().command().orElse(null)).toOption.flatMap(Option(_)).map(Path.of(_))
      val cfg = Settings.app
      LocalInstall.locate(cfg.cli.plugins, cfg.runtime.dataDir, env, java.lang.System.getProperty("os.name", ""), exe)
        .left.map(e => CliError(e, exitCode = ExitCode.Usage))
        .map(i => PluginOps(i, cfg.cli.plugins))

  /** The running server's plugins, when it answers. */
  private def running(c: Client): (Option[PluginsReport], Boolean) =
    c.about.fold(_ => (None, false), a => (Some(a.plugins), true))

  private val add = Opts.subcommand("add",
      "Install a plugin jar on this machine: a local path (copied), an https URL (downloaded, SHA-256 shown) or group:artifact:version from Maven (group::artifact:version = a Scala 3 artifact); shows its id, version, SPI and access first") {
    (
      Opts.argument[String]("path|url|group:artifact:version"),
      Opts.flag("yes", "Install without asking (needed when there is no terminal)", "y").orFalse,
      Opts.flag("rebuild", "Relink right after installing (histmersion plugin rebuild)").orFalse,
      Opts.flag("verify", "Require a valid GPG signature (<jar>.asc) even when GPG provenance is not set up").orFalse,
      Opts.flag("unsigned", "Install even when the signature is missing or does not verify").orFalse,
      Opts.option[String]("sha256", "The SHA-256 the jar must have (refused otherwise)").orNone
    ).mapN { (arg, yes, rebuild, verify, unsigned, sha) => (c: Client) =>
      local(c).flatMap { ops =>
        ops.add(arg, AddOptions(yes, unsigned, verify, sha)).flatMap { _ =>
          if rebuild then ops.rebuild(Nil)
          else Right(println("the running binary does not have it yet: histmersion plugin rebuild (or plugin status)"))
        }
      }
    }
  }

  private val rm = Opts.subcommand("rm", "Remove an installed plugin (by id or jar file name) from this machine's plugins directory") {
    (Opts.argument[String]("id|file"), Opts.flag("rebuild", "Relink right after removing").orFalse).mapN { (what, rebuild) =>
      (c: Client) => local(c).flatMap(ops => ops.rm(what).flatMap(_ => if rebuild then ops.rebuild(Nil) else
        Right(println("the running binary still has it until: histmersion plugin rebuild"))))
    }
  }

  private val list = Opts.subcommand("list", "The plugin jars in this machine's plugins directory, and whether the installed binary was linked with each") {
    Opts.unit.map(_ => (c: Client) => local(c).map(ops => println(ops.list(running(c)._1).mkString("\n"))))
  }

  private val status = Opts.subcommand("status",
      "Compare the plugins directory's digest with the installed binary's and the running server's: is a rebuild (relink) or restart needed? Also the kit and signing") {
    Opts.unit.map(_ => (c: Client) => local(c).map { ops =>
      val (r, answered) = running(c)
      println(ops.status(r, answered)._2.mkString("\n"))
    })
  }

  private val rebuild = Opts.subcommand("rebuild",
      "Relink the server with the plugins directory (bin/histmersion-relink: pre-flight, native-image, self-check, swap, restart, health; the previous binary comes back on failure)") {
    (
      Opts.flag("no-restart", "Swap the binary but do not restart the service").orFalse,
      Opts.flag("unsigned", "Go on when the plugins directory does not match its signed manifest").orFalse,
      Opts.flag("ignore-memory", "Build even with less memory than a relink wants").orFalse,
      Opts.flag("jvm", "Switch to JVM mode instead of building (plugins found at each start)").orFalse,
      Opts.flag("check", "Run the pre-flight only").orFalse
    ).mapN { (noRestart, unsigned, ignoreMemory, jvm, check) => (c: Client) =>
      val args = List(noRestart -> "--no-restart", unsigned -> "--unsigned", ignoreMemory -> "--ignore-memory",
        jvm -> "--jvm", check -> "--check").collect { case (true, a) => a }
      local(c).flatMap(_.rebuild(args))
    }
  }

  private val rollback = Opts.subcommand("rollback", "Put the previous server binary back (the one before the last relink) and restart") {
    Opts.flag("no-restart", "Do not restart the service").orFalse.map { noRestart => (c: Client) =>
      local(c).flatMap(_.rebuild("--rollback" :: (if noRestart then List("--no-restart") else Nil)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("plugin", "Plugins of the Histmersion installed on THIS machine: add, rm, list, status, rebuild, rollback (refused when the CLI points at another machine)") {
      add orElse rm orElse list orElse status orElse rebuild orElse rollback
    }

package com.histmersion.files

import com.histmersion.api.*
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.{parser, Printer}
import io.circe.syntax.*

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/** The pure half of user-added plugins and the relink kit (docs/research-plugins.md §7, §8): the plugin-set digest and
  * what `histmersion plugin status` concludes from it, the plugins directory's index, Maven coordinates, and the kit's
  * checksum manifest. No file is read here: the callers (the CLI's `plugin` commands, the server's `PluginTool`) hash
  * and list, and hand the results in.
  */
object PluginKit:

  /** JSON as these files are written: two-space indented, absent options left out. */
  private val printer = Printer.spaces2.copy(dropNullValues = true)

  // ── digests ───────────────────────────────────────────────────────────────────────────────────

  def sha256Hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"$b%02x").mkString

  /** One digest of a plugin set — `(jar file name, its SHA-256)` pairs, order-independent: SHA-256 over the sorted
    * `<name> <sha256>\n` lines. The empty set's is [[PluginSet.EmptyDigest]].
    */
  def digest(entries: List[(String, String)]): String =
    sha256Hex(entries.sortBy(_._1).map((n, h) => s"$n $h\n").mkString.getBytes(UTF_8))

  /** The set for these jars, linked with these POMs and `lib/` jars (`(file, sha256)`). */
  def pluginSet(
      jars: List[PluginJar],
      spiVersion: Int,
      mode: String,
      kit: Option[String],
      linkedAt: Option[String],
      dependencies: List[(String, String)] = Nil
  ): PluginSet =
    PluginSet(
      digest(jars.map(j => j.file -> j.sha256)),
      spiVersion,
      jars.sortBy(_.file),
      mode,
      kit,
      linkedAt,
      Some(dependencies.sortBy(_._1).map(PluginFile.apply))
    )

  def parseSet(text: String): Either[String, PluginSet] =
    parser.decode[PluginSet](text).left.map(e => s"plugin-set.json: ${e.getMessage}")

  def renderSet(s: PluginSet): String = s.asJson.printWith(printer)

  def parseJars(text: String): Either[String, List[PluginJar]] =
    parser.decode[List[PluginJar]](text).left.map(e => s"plugin description: ${e.getMessage}")

  def renderJars(js: List[PluginJar]): String = js.asJson.printWith(printer)

  // ── status ────────────────────────────────────────────────────────────────────────────────────

  enum Verdict:
    /** The plugins directory is what the installed binary was linked with, and the server runs it. */
    case UpToDate

    /** The directory changed since the installed binary was linked: `histmersion plugin rebuild`. */
    case RelinkNeeded

    /** The installed binary is not the one the server runs (a relink without a restart). */
    case RestartNeeded

    /** No record of the installed binary's set (an install older than the kit). */
    case Unknown

  /** @param dir
    *   the plugins directory now: `(file, sha256)` of every jar
    * @param installed
    *   the set recorded beside the installed binary (`bin/histmersion-server.plugin-set.json`)
    * @param running
    *   the set the running server reports (`/api/about`), when it answered
    * @param added
    *   the jars, POMs and `lib/` jars in the directory and not in the binary (`removed`: the other way round;
    *   `changed`: in both, with another SHA-256)
    */
  final case class SetStatus(
      dirDigest: String,
      installed: Option[PluginSet],
      running: Option[PluginSet],
      added: List[String],
      removed: List[String],
      changed: List[String],
      verdict: Verdict
  )

  /** Pure: compare the directory with the installed binary's set, and that with the running one's. A running server
    * that reports no set runs a first-party-only binary (the empty set). `dependencies` = the directory's POMs and
    * `lib/` jars now (`(file, sha256)`, a `lib/` jar as `lib/<name>`): a binary whose record lists the ones it was
    * linked with is out of date when they differ, though its jars are the same.
    */
  def status(
      dir: List[(String, String)],
      installed: Option[PluginSet],
      running: Option[PluginSet],
      serverAnswered: Boolean,
      dependencies: List[(String, String)] = Nil
  ): SetStatus =
    val dirDigest = digest(dir)
    val linkedDeps = installed.flatMap(_.dependencies).map(_.map(f => f.file -> f.sha256).toMap)
    val linked =
      installed.map(_.jars.map(j => j.file -> j.sha256).toMap).getOrElse(Map.empty) ++ linkedDeps.getOrElse(Map.empty)
    val now = dir.toMap ++ (if linkedDeps.isDefined then dependencies.toMap else Map.empty)
    val added = now.keys.filterNot(linked.contains).toList.sorted
    val removed = linked.keys.filterNot(now.contains).toList.sorted
    val changed = now.collect { case (f, h) if linked.get(f).exists(_ != h) => f }.toList.sorted
    val depsMoved = linkedDeps.exists(_ != dependencies.toMap)
    val runningDigest = if serverAnswered then Some(running.fold(PluginSet.EmptyDigest)(_.digest)) else None
    val verdict = installed match
      case None                                           => Verdict.Unknown
      case Some(i) if i.digest != dirDigest || depsMoved  => Verdict.RelinkNeeded
      case Some(i) if runningDigest.exists(_ != i.digest) => Verdict.RestartNeeded
      case Some(_)                                        => Verdict.UpToDate
    SetStatus(dirDigest, installed, running, added, removed, changed, verdict)

  /** The status as Markdown lines (the CLI prints them). */
  def statusLines(s: SetStatus, serverAnswered: Boolean): List[String] =
    def short(d: String) = d.take(12) + "…"
    val verdict = s.verdict match
      case Verdict.UpToDate     => "up to date: the binary holds exactly the plugins in the directory"
      case Verdict.RelinkNeeded =>
        "REBUILD NEEDED: the plugins directory (its jars, POMs or lib/) changed since the binary was linked — run `histmersion plugin rebuild`"
      case Verdict.RestartNeeded => "RESTART NEEDED: the installed binary is not the one running — restart the service"
      case Verdict.Unknown       =>
        "UNKNOWN: no record of what the installed binary was linked with — run `histmersion plugin rebuild`"
    List(
      s"- plugins directory: digest ${short(s.dirDigest)}",
      s"- installed binary: ${s.installed.fold("no plugin-set record")(i =>
          s"digest ${short(i.digest)} (${i.mode}" +
            i.linkedAt.fold(")")(t => s", linked $t)")
        )}",
      s"- running server: ${if !serverAnswered then "did not answer" else PluginSet.describe(s.running)}"
    ) ++ s.added.map(f => s"  - added since the link: $f") ++ s.removed.map(f => s"  - removed since the link: $f") ++
      s.changed.map(f => s"  - changed since the link: $f") :+ s"- verdict: $verdict"

  // ── where the plugin comes from ───────────────────────────────────────────────────────────────

  /** Maven coordinates. `g::a:v` is the Scala 3 artifact `a_3`. */
  final case class Coordinates(group: String, artifact: String, version: String):
    def path: String = s"${group.replace('.', '/')}/$artifact/$version/$artifact-$version"
    def jarUrl(repo: String): String = s"${repo.stripSuffix("/")}/$path.jar"
    def pomUrl(repo: String): String = s"${repo.stripSuffix("/")}/$path.pom"
    override def toString: String = s"$group:$artifact:$version"

  private val Part = "[A-Za-z0-9_.\\-+]+"

  def parseCoordinates(s: String): Either[String, Coordinates] =
    val scala3 = s"($Part)::($Part):($Part)".r
    val plain = s"($Part):($Part):($Part)".r
    s.trim match
      case scala3(g, a, v) => Right(Coordinates(g, s"${a}_3", v))
      case plain(g, a, v)  => Right(Coordinates(g, a, v))
      case other => Left(s"'$other' is not group:artifact:version (or group::artifact:version for a Scala 3 artifact)")

  enum Origin:
    case File(path: String)
    case Url(url: String)
    case Maven(coordinates: Coordinates)

  /** Pure: what `plugin add <arg>` names. An `http(s)://` URL is downloaded; anything that exists as a file, ends with
    * `.jar` or holds a `/` is a path; else Maven coordinates.
    */
  def origin(arg: String, exists: String => Boolean): Either[String, Origin] =
    val a = arg.trim
    if a.startsWith("https://") || a.startsWith("http://") then Right(Origin.Url(a))
    else if exists(a) || a.endsWith(".jar") || a.contains("/") then Right(Origin.File(a))
    else parseCoordinates(a).map(Origin.Maven(_))

  /** The file name a plugin jar is kept under: `<id>-<version>.jar` for a jar of one plugin (the convention of §7.1),
    * else the name it came with. Only `[A-Za-z0-9._+-]` survive.
    */
  def jarName(plugins: List[PluginDescription], fallback: String): String =
    def clean(s: String) = s.replaceAll("[^A-Za-z0-9._+\\-]", "_")
    plugins match
      case List(p) if p.id.nonEmpty => clean(s"${p.id}-${p.version}.jar")
      case _                        => clean(if fallback.endsWith(".jar") then fallback else s"$fallback.jar")

  /** Whether `url`'s host is this machine: the plugin commands act on the local install, and refuse to when the CLI is
    * pointed at a server elsewhere.
    */
  def isLoopback(url: String): Boolean =
    scala.util
      .Try(Option(URI(url.trim).getHost))
      .toOption
      .flatten
      .map(_.toLowerCase.stripPrefix("[").stripSuffix("]"))
      .exists(h => h == "localhost" || h == "::1" || h == "0:0:0:0:0:0:0:1" || h.startsWith("127."))

  // ── the plugins directory's index ─────────────────────────────────────────────────────────────

  /** One jar `plugin add` installed: where it came from, its checksum, what it holds and (with signatures configured)
    * the fingerprint of the key that signed it. A jar dropped into the directory by hand has no entry; it is still part
    * of the plugin set.
    */
  final case class Installed(
      file: String,
      sha256: String,
      source: String,
      addedAt: String,
      plugins: List[PluginDescription] = Nil,
      signer: Option[String] = None
  ) derives KindlingsCodecAsObject

  final case class Index(jars: List[Installed] = Nil) derives KindlingsCodecAsObject:
    def put(i: Installed): Index = Index((jars.filterNot(_.file == i.file) :+ i).sortBy(_.file))
    def drop(file: String): Index = Index(jars.filterNot(_.file == file))

    /** The key fingerprint pinned for a plugin id by an earlier signed install (trust on first use). */
    def pinnedSigner(id: String): Option[String] =
      jars.find(j => j.plugins.exists(_.id == id) && j.signer.nonEmpty).flatMap(_.signer)

  def parseIndex(text: String): Either[String, Index] =
    parser.decode[Index](text).left.map(e => s"plugins index: ${e.getMessage}")

  def renderIndex(i: Index): String = i.asJson.printWith(printer)

  /** The plugin id a jar file belongs to: the index's, else `<id>` of the `<id>-<version>.jar` name. */
  def idsOf(file: String, index: Index): List[String] =
    index.jars
      .find(_.file == file)
      .map(_.plugins.map(_.id))
      .filter(_.nonEmpty)
      .getOrElse(List(file.stripSuffix(".jar").replaceAll("-[0-9].*$", "")))

  /** Pure: may a jar signed by `seen` be installed for these ids? The first signed install pins its key; a later
    * version signed by another key is refused (trust on first use, §9.3).
    */
  def checkSigner(ids: List[String], seen: String, index: Index): Either[String, Unit] =
    ids.flatMap(id => index.pinnedSigner(id).filter(_ != seen).map(id -> _)) match
      case Nil               => Right(())
      case (id, pinned) :: _ =>
        Left(
          s"plugin '$id' was installed signed by $pinned, this jar is signed by $seen — refused " +
            s"(remove the plugin first with `histmersion plugin rm $id` to trust the new key)"
        )

  /** Pure: what refuses a described jar before it is installed: no plugin in it, a foreign SPI, a provider that could
    * not be instantiated, an id another installed jar (or the binary itself) already has.
    */
  def refusals(jar: PluginJar, spiVersion: Int, taken: Map[String, String]): List[String] =
    val none = if jar.plugins.isEmpty then
      List(s"${jar.file} holds no plugin (no META-INF/services/com.histmersion.plugin.Plugin provider)")
    else Nil
    none ++ jar.plugins.flatMap { p =>
      // a provider that could not be loaded has no id, version or SPI to compare: its error is the reason
      if p.id.isEmpty then List(s"a provider in ${jar.file} could not be loaded: ${p.error.getOrElse("?")}")
      else
        val spi = if p.spiVersion != spiVersion then
          List(
            s"${p.id} ${p.version} was built for plugin SPI ${p.spiVersion}; this Histmersion speaks SPI $spiVersion — " +
              s"rebuild it against SPI $spiVersion"
          )
        else Nil
        val clash = taken.get(p.id).map(o => s"plugin id '${p.id}' is already provided by $o").toList
        spi ++ clash
    }

  // ── the relink kit's manifest ─────────────────────────────────────────────────────────────────

  final case class KitFile(file: String, sha256: String) derives KindlingsCodecAsObject

  /** `kit/kit.json`: what the kit is (version, commit, the GraalVM it must be linked with, the SPI it speaks, the main
    * class) and the SHA-256 of every file in it (paths relative to `kit/`).
    */
  final case class KitManifest(
      format: Int,
      version: String,
      commit: Option[String],
      graalvm: String,
      spiVersion: Int,
      mainClass: String,
      files: List[KitFile]
  ) derives KindlingsCodecAsObject:
    def label: String = version + commit.fold("")(c => s" ($c)")

  val KitFormat = 1

  def parseKit(text: String): Either[String, KitManifest] =
    parser
      .decode[KitManifest](text)
      .left
      .map(e => s"kit.json: ${e.getMessage}")
      .filterOrElse(_.format <= KitFormat, s"kit.json: a newer kit format than this Histmersion reads ($KitFormat)")

  def renderKit(k: KitManifest): String = k.asJson.printWith(printer)

  /** `SHA256SUMS`, the format `shasum -a 256 -c` / `sha256sum -c` read: `<sha256>  <path>`, sorted. */
  def sha256sums(files: List[KitFile]): String =
    files.sortBy(_.file).map(f => s"${f.sha256}  ${f.file}\n").mkString

  def parseSha256sums(text: String): Either[String, List[KitFile]] =
    val line = "([0-9a-f]{64}) [ *](.+)".r
    text.linesIterator.filter(_.trim.nonEmpty).toList.foldLeft[Either[String, List[KitFile]]](Right(Nil)) {
      case (Right(acc), line(h, f)) => Right(acc :+ KitFile(f, h))
      case (Right(_), bad)          => Left(s"SHA256SUMS: not a checksum line: $bad")
      case (left, _)                => left
    }

  /** Pure: every difference between the manifest and the kit on disk (`actual`: path → SHA-256 of every file found
    * under `kit/`, the manifests themselves excluded). Empty = intact.
    */
  def verifyKit(expected: List[KitFile], actual: Map[String, String]): List[String] =
    val want = expected.map(f => f.file -> f.sha256).toMap
    val missing = want.keys.filterNot(actual.contains).toList.sorted.map(f => s"missing: $f")
    val changed = want.collect { case (f, h) if actual.get(f).exists(_ != h) => s"changed: $f" }.toList.sorted
    val extra = actual.keys.filterNot(want.contains).toList.sorted.map(f => s"not in the manifest: $f")
    missing ++ changed ++ extra

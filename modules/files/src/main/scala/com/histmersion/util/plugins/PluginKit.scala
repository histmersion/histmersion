package com.histmersion
package util.plugins
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.api.json.NewtypeCodecs.given

import com.histmersion.util.problem.{External, Problem}

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

import com.histmersion.util.json.JsonCodec

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import com.histmersion.api.plugins.{PluginDescription, PluginFile, PluginJar, PluginSet}
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.api.automation.ProviderKind

/** The pure half of user-added plugins and the relink kit (docs/research-plugins.md §7, §8): the plugin-set digest and
  * what `histmersion plugin status` concludes from it, the plugins directory's index, Maven coordinates, and the kit's
  * checksum manifest. No file is read here: the callers (the CLI's `plugin` commands, the server's `pluginTool`) hash
  * and list, and hand the results in.
  */
object PluginKit:

  extension (id: PluginDescription.Id)
    private def isEmpty: Boolean = id.value.isEmpty
    private def nonEmpty: Boolean = id.value.nonEmpty

  /** JSON as these files are written: two-space indented, absent options left out. */

  // ── digests ───────────────────────────────────────────────────────────────────────────────────

  private def hex(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"$b%02x").mkString

  def sha256Hex(bytes: Array[Byte]): Sha256 = Sha256.applyUnsafe(hex(bytes))

  /** One digest of a plugin set — `(jar file name, its SHA-256)` pairs, order-independent: SHA-256 over the sorted
    * `<name> <sha256>\n` lines. The empty set's is [[PluginSet.EmptyDigest]].
    */
  def digest(entries: List[(PluginJar.File, com.histmersion.api.Sha256)]): PluginSet.Digest =
    PluginSet.Digest(hex(entries.sortBy(_._1.value).map((n, h) => s"$n $h\n").mkString.getBytes(UTF_8)))

  /** The set for these jars, linked with these POMs and `lib/` jars (`(file, sha256)`). */
  def pluginSet(
      jars: List[PluginJar],
      spiVersion: PluginSet.SpiVersion,
      mode: PluginSet.Mode,
      kit: Option[PluginSet.Kit],
      linkedAt: Option[PluginSet.LinkedAt],
      dependencies: List[(PluginFile.File, com.histmersion.api.Sha256)] = Nil
  ): PluginSet =
    PluginSet(
      digest(jars.map(j => j.file -> j.sha256)),
      spiVersion,
      jars.sortBy(_.file.value),
      mode,
      kit,
      linkedAt,
      Some(dependencies.sortBy(_._1.value).map(PluginFile.apply))
    )

  def parseSet(text: String): Either[Problem, PluginSet] =
    RawJson.decode[PluginSet](text).left.map(e => unreadable("plugin-set.json", s"$e"))

  def renderSet(s: PluginSet): String = s.asJson.withoutNulls.spaces2

  def parseJars(text: String): Either[Problem, List[PluginJar]] =
    RawJson
      .decode[List[PluginJar]](text)
      .left
      .map(e => unreadable("the plugin description", s"$e"))

  private def unreadable(where: String, reported: String): Problem =
    Problem.Io.Corrupt(
      Problem.Io.Format.Json,
      Problem.Where(s"$where"),
      Problem.Io.Damage.Unreadable(External.Reported.of(reported))
    )

  def renderJars(js: List[PluginJar]): String = js.asJson.withoutNulls.spaces2

  // ── status ────────────────────────────────────────────────────────────────────────────────────

  enum Verdict derives CanEqual:
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
      dirDigest: PluginSet.Digest,
      installed: Option[PluginSet],
      running: Option[PluginSet],
      added: List[PluginJar.File],
      removed: List[PluginJar.File],
      changed: List[PluginJar.File],
      verdict: Verdict
  )

  /** Pure: compare the directory with the installed binary's set, and that with the running one's. A running server
    * that reports no set runs a first-party-only binary (the empty set). `dependencies` = the directory's POMs and
    * `lib/` jars now (`(file, sha256)`, a `lib/` jar as `lib/<name>`): a binary whose record lists the ones it was
    * linked with is out of date when they differ, though its jars are the same.
    */
  def status(
      dir: List[(PluginJar.File, com.histmersion.api.Sha256)],
      installed: Option[PluginSet],
      running: Option[PluginSet],
      serverAnswered: Boolean,
      dependencies: List[(PluginFile.File, com.histmersion.api.Sha256)] = Nil
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
    val runningDigest =
      if serverAnswered then Some(running.fold(PluginSet.Digest(PluginSet.EmptyDigest))(_.digest)) else None
    val verdict = installed match
      case None                                           => Verdict.Unknown
      case Some(i) if i.digest != dirDigest || depsMoved  => Verdict.RelinkNeeded
      case Some(i) if runningDigest.exists(_ != i.digest) => Verdict.RestartNeeded
      case Some(_)                                        => Verdict.UpToDate
    SetStatus(dirDigest, installed, running, added, removed, changed, verdict)

  /** The status as Markdown lines (the CLI prints them). */
  def statusLines(s: SetStatus, serverAnswered: Boolean): List[String] =
    def short(d: PluginSet.Digest) = d.value.take(12) + "…"
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
  final case class Coordinates(group: Coordinates.Group, artifact: Coordinates.Artifact, version: Coordinates.Version):
    def path: String = s"${group.value.replace('.', '/')}/$artifact/$version/$artifact-$version"
    def jarUrl(repo: String): String = s"${repo.stripSuffix("/")}/$path.jar"
    def pomUrl(repo: String): String = s"${repo.stripSuffix("/")}/$path.pom"
    override def toString: String = s"$group:$artifact:$version"

  object Coordinates:
    type Group = Group.T
    object Group extends Newtype[String, Pure]
    type Artifact = Artifact.T
    object Artifact extends Newtype[String, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]

    /** Coordinates as written: `group:artifact:version`, or `group::artifact:version` for a Scala 3 artifact. */
    type Written = Written.T
    object Written extends Newtype[String, Match["[A-Za-z0-9_.\\-+]+::?[A-Za-z0-9_.\\-+]+:[A-Za-z0-9_.\\-+]+"]]

  private val Part = "[A-Za-z0-9_.\\-+]+"

  def parseCoordinates(s: String): Either[Problem, Coordinates] =
    val scala3 = s"($Part)::($Part):($Part)".r
    val plain = s"($Part):($Part):($Part)".r
    s.trim match
      case scala3(g, a, v) =>
        Right(Coordinates(Coordinates.Group(g), Coordinates.Artifact(s"${a}_3"), Coordinates.Version(v)))
      case plain(g, a, v) => Right(Coordinates(Coordinates.Group(g), Coordinates.Artifact(a), Coordinates.Version(v)))
      case other          => Left(Problem.Invalid.malformed(Coordinates.Written.refused(other)))

  enum Origin derives CanEqual:
    case File(path: Origin.File.Path)
    case Url(url: Origin.Url.Value)
    case Maven(coordinates: Coordinates)

  object Origin:
    object File:
      type Path = Path.T
      object Path extends Newtype[String, Pure]
    object Url:
      type Value = Value.T
      object Value extends Newtype[String, Pure]

  /** Pure: what `plugin add <arg>` names. An `http(s)://` URL is downloaded; anything that exists as a file, ends with
    * `.jar` or holds a `/` is a path; else Maven coordinates.
    */
  def origin(arg: String, exists: String => Boolean): Either[Problem, Origin] =
    val a = arg.trim
    if a.startsWith("https://") || a.startsWith("http://") then Right(Origin.Url(Origin.Url.Value(a)))
    else if exists(a) || a.endsWith(".jar") || a.contains("/") then Right(Origin.File(Origin.File.Path(a)))
    else parseCoordinates(a).map(Origin.Maven(_))

  /** The file name a plugin jar is kept under: `<id>-<version>.jar` for a jar of one plugin (the convention of §7.1),
    * else the name it came with. Only `[A-Za-z0-9._+-]` survive.
    */
  def jarName(plugins: List[PluginDescription], fallback: String): PluginJar.File =
    def clean(s: String) = PluginJar.File(s.replaceAll("[^A-Za-z0-9._+\\-]", "_"))
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
      file: Installed.File,
      sha256: Installed.Sha256,
      source: Installed.Source,
      addedAt: Installed.AddedAt,
      plugins: List[PluginDescription] = Nil,
      signer: Option[Installed.Signer] = None
  ) derives JsonCodec

  object Installed:
    type File = PluginJar.File
    val File: ProviderKind.Plugin.FilePath.type = PluginJar.File
    type Sha256 = com.histmersion.Sha256
    val Sha256: com.histmersion.Sha256.type = com.histmersion.Sha256
    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type AddedAt = AddedAt.T
    object AddedAt extends Newtype[String, Pure]
    type Signer = Signer.T
    object Signer extends Newtype[String, Pure]

  final case class Index(jars: List[Installed] = Nil) derives JsonCodec:
    def put(i: Installed): Index = Index((jars.filterNot(_.file == i.file) :+ i).sortBy(_.file.value))
    def drop(file: Installed.File): Index = Index(jars.filterNot(_.file == file))

    /** The key fingerprint pinned for a plugin id by an earlier signed install (trust on first use). */
    def pinnedSigner(id: PluginDescription.Id): Option[Installed.Signer] =
      jars.find(j => j.plugins.exists(_.id == id) && j.signer.nonEmpty).flatMap(_.signer)

  def parseIndex(text: String): Either[Problem, Index] =
    RawJson.decode[Index](text).left.map(e => unreadable("the plugins index", s"$e"))

  def renderIndex(i: Index): String = i.asJson.withoutNulls.spaces2

  /** The plugin id a jar file belongs to: the index's, else `<id>` of the `<id>-<version>.jar` name. */
  def idsOf(file: Installed.File, index: Index): List[PluginDescription.Id] =
    index.jars
      .find(_.file == file)
      .map(_.plugins.map(_.id))
      .filter(_.nonEmpty)
      .getOrElse(List(PluginDescription.Id(file.value.stripSuffix(".jar").replaceAll("-[0-9].*$", ""))))

  /** Pure: may a jar signed by `seen` be installed for these ids? The first signed install pins its key; a later
    * version signed by another key is refused (trust on first use, §9.3).
    */
  def checkSigner(ids: List[PluginDescription.Id], seen: Installed.Signer, index: Index): Either[Problem, Unit] =
    ids.flatMap(id => index.pinnedSigner(id).filter(_ != seen).map(id -> _)) match
      case Nil               => Right(())
      case (id, pinned) :: _ =>
        Left(
          Problem.Plugin.Load(
            Problem.Plugin.Id(s"$id"),
            Problem.Plugin.Fault.SignerChanged(Problem.Plugin.Signer(s"$pinned"), Problem.Plugin.Signer(s"$seen"))
          )
        )

  /** Pure: what refuses a described jar before it is installed: no plugin in it, a foreign SPI, a provider that could
    * not be instantiated, an id another installed jar (or the binary itself) already has.
    */
  def refusals(
      jar: PluginJar,
      spiVersion: PluginSet.SpiVersion,
      taken: Map[PluginDescription.Id, String]
  ): List[Problem] =
    val file = Problem.Plugin.Id(s"${jar.file}")
    val none =
      if jar.plugins.isEmpty then List(Problem.Plugin.Load(file, Problem.Plugin.Fault.NoProvider)) else Nil
    none ++ jar.plugins.flatMap { p =>
      // a provider that could not be loaded has no id, version or SPI to compare: its error is the reason
      if p.id.isEmpty then List(p.error.getOrElse(Problem.Plugin.Load(file, Problem.Plugin.Fault.BadDescriptor)))
      else
        val id = Problem.Plugin.Id(s"${p.id}")
        val spi = Option
          .when(p.spiVersion != spiVersion)(
            Problem.Plugin
              .Load(id, Problem.Plugin.Fault.SpiMismatch(Problem.Raw(s"${p.spiVersion}"), Problem.Raw(s"$spiVersion")))
          )
          .toList
        val clash =
          taken.get(p.id).map(o => Problem.Plugin.Load(id, Problem.Plugin.Fault.Taken(Problem.Raw(s"$o")))).toList
        spi ++ clash
    }

  // ── the relink kit's manifest ─────────────────────────────────────────────────────────────────

  final case class KitFile(file: KitFile.File, sha256: KitFile.Sha256) derives JsonCodec

  object KitFile:
    type File = File.T
    object File extends Newtype[String, Pure]
    type Sha256 = com.histmersion.Sha256
    val Sha256: com.histmersion.Sha256.type = com.histmersion.Sha256

  /** `kit/kit.json`: what the kit is (version, commit, the GraalVM it must be linked with, the SPI it speaks, the main
    * class) and the SHA-256 of every file in it (paths relative to `kit/`).
    */
  final case class KitManifest(
      format: KitManifest.Format,
      version: KitManifest.Version,
      commit: Option[KitManifest.Commit],
      graalvm: KitManifest.Graalvm,
      spiVersion: PluginSet.SpiVersion,
      mainClass: KitManifest.MainClass,
      files: List[KitFile]
  ) derives JsonCodec:
    def label: String = s"$version${commit.fold("")(c => s" ($c)")}"

  object KitManifest:
    type Format = Format.T
    object Format extends Newtype[Int, Pure]
    type Version = Version.T
    object Version extends Newtype[String, Pure]
    type Commit = Commit.T
    object Commit extends Newtype[String, Pure]
    type Graalvm = Graalvm.T
    object Graalvm extends Newtype[String, Pure]
    type MainClass = MainClass.T
    object MainClass extends Newtype[String, Pure]

  val KitFormat: KitManifest.Format = KitManifest.Format(1)

  def parseKit(text: String): Either[Problem, KitManifest] =
    RawJson
      .decode[KitManifest](text)
      .left
      .map(e => unreadable("kit.json", s"$e"))
      .flatMap(k =>
        Either.cond(
          k.format <= KitFormat,
          k,
          Problem.Io.Corrupt(
            Problem.Io.Format.Json,
            Problem.Where("kit.json"),
            Problem.Io.Damage.Newer(Problem.Raw(s"${k.format}"), Problem.Raw(s"$KitFormat"))
          )
        )
      )

  def renderKit(k: KitManifest): String = k.asJson.withoutNulls.spaces2

  /** `SHA256SUMS`, the format `shasum -a 256 -c` / `sha256sum -c` read: `<sha256>  <path>`, sorted. */
  def sha256sums(files: List[KitFile]): String =
    files.sortBy(_.file.value).map(f => s"${f.sha256}  ${f.file}\n").mkString

  def parseSha256sums(text: String): Either[Problem, List[KitFile]] =
    val line = "([0-9a-f]{64}) [ *](.+)".r
    text.linesIterator.zipWithIndex
      .filter(_._1.trim.nonEmpty)
      .toList
      .foldLeft[Either[Problem, List[KitFile]]](Right(Nil)) {
        case (Right(acc), (line(h, f), _)) => Right(acc :+ KitFile(KitFile.File(f), KitFile.Sha256.applyUnsafe(h)))
        case (Right(_), (_, i))            =>
          Left(
            Problem.Io.Corrupt(
              Problem.Io.Format.Text,
              Problem.Where(s"SHA256SUMS line ${i + 1}"),
              Problem.Io.Damage.Undecodable
            )
          )
        case (left, _) => left
      }

  /** Pure: every difference between the manifest and the kit on disk (`actual`: path → SHA-256 of every file found
    * under `kit/`, the manifests themselves excluded). Empty = intact.
    */
  def verifyKit(expected: List[KitFile], actual: Map[KitFile.File, KitFile.Sha256]): List[Problem] =
    val want = expected.map(f => f.file -> f.sha256).toMap
    val missing =
      want.keys
        .filterNot(actual.contains)
        .toList
        .sorted
        .map(f => Problem.Io.Wrong(Problem.Io.Path(s"$f"), Problem.Io.Kind.Present))
    val changed =
      want
        .collect { case (f, h) if actual.get(f).exists(_ != h) => f }
        .toList
        .sorted
        .map(f => Problem.Io.Corrupt(Problem.Io.Format.Kit, Problem.Where(s"$f"), Problem.Io.Damage.Checksum))
    val extra = actual.keys
      .filterNot(want.contains)
      .toList
      .sorted
      .map(f => Problem.Io.Wrong(Problem.Io.Path(s"$f"), Problem.Io.Kind.Listed))
    missing ++ changed ++ extra

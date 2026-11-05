package com.histmersion.util.plugins
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.{parser, Printer}
import io.circe.syntax.*

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import com.histmersion.api.plugins.{PluginDescription, PluginFile, PluginJar, PluginSet}

/** The pure half of user-added plugins and the relink kit (docs/research-plugins.md §7, §8): the plugin-set digest and
  * what `histmersion plugin status` concludes from it, the plugins directory's index, Maven coordinates, and the kit's
  * checksum manifest. No file is read here: the callers (the CLI's `plugin` commands, the server's `pluginTool`) hash
  * and list, and hand the results in.
  */
object PluginKit:

  /** JSON as these files are written: two-space indented, absent options left out. */
  type Sha256HexBytes = Sha256HexBytes.T
  object Sha256HexBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Sha256HexResult = Sha256HexResult.T
  object Sha256HexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DigestResult = DigestResult.T
  object DigestResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DigestEntriesPart1 = DigestEntriesPart1.T
  object DigestEntriesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DigestEntriesPart2 = DigestEntriesPart2.T
  object DigestEntriesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginSetSpiVersion = PluginSetSpiVersion.T
  object PluginSetSpiVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginSetMode = PluginSetMode.T
  object PluginSetMode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginSetKit = PluginSetKit.T
  object PluginSetKit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginSetLinkedAt = PluginSetLinkedAt.T
  object PluginSetLinkedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginSetDependenciesPart1 = PluginSetDependenciesPart1.T
  object PluginSetDependenciesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginSetDependenciesPart2 = PluginSetDependenciesPart2.T
  object PluginSetDependenciesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSetResultPart1 = ParseSetResultPart1.T
  object ParseSetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSetText = ParseSetText.T
  object ParseSetText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderSetResult = RenderSetResult.T
  object RenderSetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJarsResultPart1 = ParseJarsResultPart1.T
  object ParseJarsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJarsText = ParseJarsText.T
  object ParseJarsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderJarsResult = RenderJarsResult.T
  object RenderJarsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusDirPart1 = StatusDirPart1.T
  object StatusDirPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusDirPart2 = StatusDirPart2.T
  object StatusDirPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusServerAnswered = StatusServerAnswered.T
  object StatusServerAnswered extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusDependenciesPart1 = StatusDependenciesPart1.T
  object StatusDependenciesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusDependenciesPart2 = StatusDependenciesPart2.T
  object StatusDependenciesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusLinesResult = StatusLinesResult.T
  object StatusLinesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusLinesServerAnswered = StatusLinesServerAnswered.T
  object StatusLinesServerAnswered extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseCoordinatesResultPart1 = ParseCoordinatesResultPart1.T
  object ParseCoordinatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseCoordinatesS = ParseCoordinatesS.T
  object ParseCoordinatesS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginResultPart1 = OriginResultPart1.T
  object OriginResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginArg = OriginArg.T
  object OriginArg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginExistsArg1 = OriginExistsArg1.T
  object OriginExistsArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginExistsResult = OriginExistsResult.T
  object OriginExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JarNameResult = JarNameResult.T
  object JarNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JarNameFallback = JarNameFallback.T
  object JarNameFallback extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsLoopbackResult = IsLoopbackResult.T
  object IsLoopbackResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsLoopbackUrl = IsLoopbackUrl.T
  object IsLoopbackUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseIndexResultPart1 = ParseIndexResultPart1.T
  object ParseIndexResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseIndexText = ParseIndexText.T
  object ParseIndexText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderIndexResult = RenderIndexResult.T
  object RenderIndexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsOfResult = IdsOfResult.T
  object IdsOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdsOfFile = IdsOfFile.T
  object IdsOfFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckSignerResultPart1 = CheckSignerResultPart1.T
  object CheckSignerResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckSignerIds = CheckSignerIds.T
  object CheckSignerIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckSignerSeen = CheckSignerSeen.T
  object CheckSignerSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalsResult = RefusalsResult.T
  object RefusalsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalsSpiVersion = RefusalsSpiVersion.T
  object RefusalsSpiVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalsTakenPart1 = RefusalsTakenPart1.T
  object RefusalsTakenPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalsTakenPart2 = RefusalsTakenPart2.T
  object RefusalsTakenPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseKitResultPart1 = ParseKitResultPart1.T
  object ParseKitResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseKitText = ParseKitText.T
  object ParseKitText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderKitResult = RenderKitResult.T
  object RenderKitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256sumsResult = Sha256sumsResult.T
  object Sha256sumsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSha256sumsResultPart1 = ParseSha256sumsResultPart1.T
  object ParseSha256sumsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSha256sumsText = ParseSha256sumsText.T
  object ParseSha256sumsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyKitResult = VerifyKitResult.T
  object VerifyKitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyKitActualPart1 = VerifyKitActualPart1.T
  object VerifyKitActualPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerifyKitActualPart2 = VerifyKitActualPart2.T
  object VerifyKitActualPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val printer = Printer.spaces2.copy(dropNullValues = true)

  // ── digests ───────────────────────────────────────────────────────────────────────────────────

  def sha256Hex(bytes: com.histmersion.util.plugins.PluginKit.Sha256HexBytes): com.histmersion.util.plugins.PluginKit.Sha256HexResult =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"$b%02x").mkString

  /** One digest of a plugin set — `(jar file name, its SHA-256)` pairs, order-independent: SHA-256 over the sorted
    * `<name> <sha256>\n` lines. The empty set's is [[PluginSet.EmptyDigest]].
    */
  def digest(entries: List[(com.histmersion.util.plugins.PluginKit.DigestEntriesPart1, com.histmersion.util.plugins.PluginKit.DigestEntriesPart2)]): com.histmersion.util.plugins.PluginKit.DigestResult =
    sha256Hex(entries.sortBy(_._1).map((n, h) => s"$n $h\n").mkString.getBytes(UTF_8))

  /** The set for these jars, linked with these POMs and `lib/` jars (`(file, sha256)`). */
  def pluginSet(
      jars: List[PluginJar],
      spiVersion: com.histmersion.util.plugins.PluginKit.PluginSetSpiVersion,
      mode: com.histmersion.util.plugins.PluginKit.PluginSetMode,
      kit: Option[com.histmersion.util.plugins.PluginKit.PluginSetKit],
      linkedAt: Option[com.histmersion.util.plugins.PluginKit.PluginSetLinkedAt],
      dependencies: List[(com.histmersion.util.plugins.PluginKit.PluginSetDependenciesPart1, com.histmersion.util.plugins.PluginKit.PluginSetDependenciesPart2)] = Nil
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

  def parseSet(text: com.histmersion.util.plugins.PluginKit.ParseSetText): Either[com.histmersion.util.plugins.PluginKit.ParseSetResultPart1, PluginSet] =
    parser.decode[PluginSet](text).left.map(e => s"plugin-set.json: ${e.getMessage}")

  def renderSet(s: PluginSet): com.histmersion.util.plugins.PluginKit.RenderSetResult = s.asJson.printWith(printer)

  def parseJars(text: com.histmersion.util.plugins.PluginKit.ParseJarsText): Either[com.histmersion.util.plugins.PluginKit.ParseJarsResultPart1, List[PluginJar]] =
    parser.decode[List[PluginJar]](text).left.map(e => s"plugin description: ${e.getMessage}")

  def renderJars(js: List[PluginJar]): com.histmersion.util.plugins.PluginKit.RenderJarsResult = js.asJson.printWith(printer)

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
      dirDigest: SetStatus.DirDigest,
      installed: Option[PluginSet],
      running: Option[PluginSet],
      added: List[SetStatus.Added],
      removed: List[SetStatus.Removed],
      changed: List[SetStatus.Changed],
      verdict: Verdict
  )

  object SetStatus:
    type DirDigest = DirDigest.T
    object DirDigest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Added = Added.T
    object Added extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Removed = Removed.T
    object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Changed = Changed.T
    object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: compare the directory with the installed binary's set, and that with the running one's. A running server
    * that reports no set runs a first-party-only binary (the empty set). `dependencies` = the directory's POMs and
    * `lib/` jars now (`(file, sha256)`, a `lib/` jar as `lib/<name>`): a binary whose record lists the ones it was
    * linked with is out of date when they differ, though its jars are the same.
    */
  def status(
      dir: List[(com.histmersion.util.plugins.PluginKit.StatusDirPart1, com.histmersion.util.plugins.PluginKit.StatusDirPart2)],
      installed: Option[PluginSet],
      running: Option[PluginSet],
      serverAnswered: com.histmersion.util.plugins.PluginKit.StatusServerAnswered,
      dependencies: List[(com.histmersion.util.plugins.PluginKit.StatusDependenciesPart1, com.histmersion.util.plugins.PluginKit.StatusDependenciesPart2)] = Nil
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
  def statusLines(s: SetStatus, serverAnswered: com.histmersion.util.plugins.PluginKit.StatusLinesServerAnswered): List[com.histmersion.util.plugins.PluginKit.StatusLinesResult] =
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
  final case class Coordinates(group: Coordinates.Group, artifact: Coordinates.Artifact, version: Coordinates.Version):
    def path: com.histmersion.util.plugins.PluginKit.Coordinates.PathResult = s"${group.replace('.', '/')}/$artifact/$version/$artifact-$version"
    def jarUrl(repo: com.histmersion.util.plugins.PluginKit.Coordinates.JarUrlRepo): com.histmersion.util.plugins.PluginKit.Coordinates.JarUrlResult = s"${repo.stripSuffix("/")}/$path.jar"
    def pomUrl(repo: com.histmersion.util.plugins.PluginKit.Coordinates.PomUrlRepo): com.histmersion.util.plugins.PluginKit.Coordinates.PomUrlResult = s"${repo.stripSuffix("/")}/$path.pom"
    override def toString: String = s"$group:$artifact:$version"

  object Coordinates:
    type PathResult = PathResult.T
    object PathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type JarUrlResult = JarUrlResult.T
    object JarUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type JarUrlRepo = JarUrlRepo.T
    object JarUrlRepo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PomUrlResult = PomUrlResult.T
    object PomUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PomUrlRepo = PomUrlRepo.T
    object PomUrlRepo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Group = Group.T
    object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Artifact = Artifact.T
    object Artifact extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Part = "[A-Za-z0-9_.\\-+]+"

  def parseCoordinates(s: com.histmersion.util.plugins.PluginKit.ParseCoordinatesS): Either[com.histmersion.util.plugins.PluginKit.ParseCoordinatesResultPart1, Coordinates] =
    val scala3 = s"($Part)::($Part):($Part)".r
    val plain = s"($Part):($Part):($Part)".r
    s.trim match
      case scala3(g, a, v) => Right(Coordinates(g, s"${a}_3", v))
      case plain(g, a, v)  => Right(Coordinates(g, a, v))
      case other => Left(s"'$other' is not group:artifact:version (or group::artifact:version for a Scala 3 artifact)")

  enum Origin:
    case File(path: Origin.File.Path)
    case Url(url: Origin.Url.Value)
    case Maven(coordinates: Coordinates)

  object Origin:
    object File:
      type Path = Path.T
      object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Url:
      type Value = Value.T
      object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: what `plugin add <arg>` names. An `http(s)://` URL is downloaded; anything that exists as a file, ends with
    * `.jar` or holds a `/` is a path; else Maven coordinates.
    */
  def origin(arg: com.histmersion.util.plugins.PluginKit.OriginArg, exists: com.histmersion.util.plugins.PluginKit.OriginExistsArg1 => com.histmersion.util.plugins.PluginKit.OriginExistsResult): Either[com.histmersion.util.plugins.PluginKit.OriginResultPart1, Origin] =
    val a = arg.trim
    if a.startsWith("https://") || a.startsWith("http://") then Right(Origin.Url(a))
    else if exists(a) || a.endsWith(".jar") || a.contains("/") then Right(Origin.File(a))
    else parseCoordinates(a).map(Origin.Maven(_))

  /** The file name a plugin jar is kept under: `<id>-<version>.jar` for a jar of one plugin (the convention of §7.1),
    * else the name it came with. Only `[A-Za-z0-9._+-]` survive.
    */
  def jarName(plugins: List[PluginDescription], fallback: com.histmersion.util.plugins.PluginKit.JarNameFallback): com.histmersion.util.plugins.PluginKit.JarNameResult =
    def clean(s: String) = s.replaceAll("[^A-Za-z0-9._+\\-]", "_")
    plugins match
      case List(p) if p.id.nonEmpty => clean(s"${p.id}-${p.version}.jar")
      case _                        => clean(if fallback.endsWith(".jar") then fallback else s"$fallback.jar")

  /** Whether `url`'s host is this machine: the plugin commands act on the local install, and refuse to when the CLI is
    * pointed at a server elsewhere.
    */
  def isLoopback(url: com.histmersion.util.plugins.PluginKit.IsLoopbackUrl): com.histmersion.util.plugins.PluginKit.IsLoopbackResult =
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
  ) derives KindlingsCodecAsObject

  object Installed:
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sha256 = Sha256.T
    object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddedAt = AddedAt.T
    object AddedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Signer = Signer.T
    object Signer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Index(jars: List[Installed] = Nil) derives KindlingsCodecAsObject:
    def put(i: Installed): Index = Index((jars.filterNot(_.file == i.file) :+ i).sortBy(_.file))
    def drop(file: com.histmersion.util.plugins.PluginKit.Index.DropFile): Index = Index(jars.filterNot(_.file == file))

    /** The key fingerprint pinned for a plugin id by an earlier signed install (trust on first use). */
    def pinnedSigner(id: com.histmersion.util.plugins.PluginKit.Index.PinnedSignerId): Option[com.histmersion.util.plugins.PluginKit.Index.PinnedSignerResult] =
      jars.find(j => j.plugins.exists(_.id == id) && j.signer.nonEmpty).flatMap(_.signer)

  object Index:
    type DropFile = DropFile.T
    object DropFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PinnedSignerResult = PinnedSignerResult.T
    object PinnedSignerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PinnedSignerId = PinnedSignerId.T
    object PinnedSignerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parseIndex(text: com.histmersion.util.plugins.PluginKit.ParseIndexText): Either[com.histmersion.util.plugins.PluginKit.ParseIndexResultPart1, Index] =
    parser.decode[Index](text).left.map(e => s"plugins index: ${e.getMessage}")

  def renderIndex(i: Index): com.histmersion.util.plugins.PluginKit.RenderIndexResult = i.asJson.printWith(printer)

  /** The plugin id a jar file belongs to: the index's, else `<id>` of the `<id>-<version>.jar` name. */
  def idsOf(file: com.histmersion.util.plugins.PluginKit.IdsOfFile, index: Index): List[com.histmersion.util.plugins.PluginKit.IdsOfResult] =
    index.jars
      .find(_.file == file)
      .map(_.plugins.map(_.id))
      .filter(_.nonEmpty)
      .getOrElse(List(file.stripSuffix(".jar").replaceAll("-[0-9].*$", "")))

  /** Pure: may a jar signed by `seen` be installed for these ids? The first signed install pins its key; a later
    * version signed by another key is refused (trust on first use, §9.3).
    */
  def checkSigner(ids: List[com.histmersion.util.plugins.PluginKit.CheckSignerIds], seen: com.histmersion.util.plugins.PluginKit.CheckSignerSeen, index: Index): Either[com.histmersion.util.plugins.PluginKit.CheckSignerResultPart1, Unit] =
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
  def refusals(jar: PluginJar, spiVersion: com.histmersion.util.plugins.PluginKit.RefusalsSpiVersion, taken: Map[com.histmersion.util.plugins.PluginKit.RefusalsTakenPart1, com.histmersion.util.plugins.PluginKit.RefusalsTakenPart2]): List[com.histmersion.util.plugins.PluginKit.RefusalsResult] =
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

  final case class KitFile(file: KitFile.File, sha256: KitFile.Sha256) derives KindlingsCodecAsObject

  object KitFile:
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sha256 = Sha256.T
    object Sha256 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `kit/kit.json`: what the kit is (version, commit, the GraalVM it must be linked with, the SPI it speaks, the main
    * class) and the SHA-256 of every file in it (paths relative to `kit/`).
    */
  final case class KitManifest(
      format: KitManifest.Format,
      version: KitManifest.Version,
      commit: Option[KitManifest.Commit],
      graalvm: KitManifest.Graalvm,
      spiVersion: KitManifest.SpiVersion,
      mainClass: KitManifest.MainClass,
      files: List[KitFile]
  ) derives KindlingsCodecAsObject:
    def label: com.histmersion.util.plugins.PluginKit.KitManifest.LabelResult = version + commit.fold("")(c => s" ($c)")

  object KitManifest:
    type LabelResult = LabelResult.T
    object LabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Format = Format.T
    object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Commit = Commit.T
    object Commit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Graalvm = Graalvm.T
    object Graalvm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpiVersion = SpiVersion.T
    object SpiVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MainClass = MainClass.T
    object MainClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val KitFormat = 1

  def parseKit(text: com.histmersion.util.plugins.PluginKit.ParseKitText): Either[com.histmersion.util.plugins.PluginKit.ParseKitResultPart1, KitManifest] =
    parser
      .decode[KitManifest](text)
      .left
      .map(e => s"kit.json: ${e.getMessage}")
      .filterOrElse(_.format <= KitFormat, s"kit.json: a newer kit format than this Histmersion reads ($KitFormat)")

  def renderKit(k: KitManifest): com.histmersion.util.plugins.PluginKit.RenderKitResult = k.asJson.printWith(printer)

  /** `SHA256SUMS`, the format `shasum -a 256 -c` / `sha256sum -c` read: `<sha256>  <path>`, sorted. */
  def sha256sums(files: List[KitFile]): com.histmersion.util.plugins.PluginKit.Sha256sumsResult =
    files.sortBy(_.file).map(f => s"${f.sha256}  ${f.file}\n").mkString

  def parseSha256sums(text: com.histmersion.util.plugins.PluginKit.ParseSha256sumsText): Either[com.histmersion.util.plugins.PluginKit.ParseSha256sumsResultPart1, List[KitFile]] =
    val line = "([0-9a-f]{64}) [ *](.+)".r
    text.linesIterator.filter(_.trim.nonEmpty).toList.foldLeft[Either[String, List[KitFile]]](Right(Nil)) {
      case (Right(acc), line(h, f)) => Right(acc :+ KitFile(f, h))
      case (Right(_), bad)          => Left(s"SHA256SUMS: not a checksum line: $bad")
      case (left, _)                => left
    }

  /** Pure: every difference between the manifest and the kit on disk (`actual`: path → SHA-256 of every file found
    * under `kit/`, the manifests themselves excluded). Empty = intact.
    */
  def verifyKit(expected: List[KitFile], actual: Map[com.histmersion.util.plugins.PluginKit.VerifyKitActualPart1, com.histmersion.util.plugins.PluginKit.VerifyKitActualPart2]): List[com.histmersion.util.plugins.PluginKit.VerifyKitResult] =
    val want = expected.map(f => f.file -> f.sha256).toMap
    val missing = want.keys.filterNot(actual.contains).toList.sorted.map(f => s"missing: $f")
    val changed = want.collect { case (f, h) if actual.get(f).exists(_ != h) => s"changed: $f" }.toList.sorted
    val extra = actual.keys.filterNot(want.contains).toList.sorted.map(f => s"not in the manifest: $f")
    missing ++ changed ++ extra

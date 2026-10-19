package com.histmersion.server.basemap

import com.histmersion.core.*
import com.histmersion.server.Deps

import io.circe.syntax.*

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** The terrain map's named styles (docs/user-guide/how-to/terrain-styles.md): the built-in document
  * with the user's style file over it, checked, resolved and served to the page.
  *
  * HOT without a watcher: every read compares the file's stamp (path, size, modification time) and
  * the settings it depends on with those it was built from, and builds again only when one changed —
  * a `stat` per request, the parse once per change. So an edited file, a file put in place or
  * removed, and a config reload that names another file (`basemap.style`) or language all apply at
  * the next read; the page asks every `basemap.style-check-seconds` while it shows the terrain map. A
  * file with an error is refused whole and the built-in styles are served, the error said in
  * `warnings`, `/api/health` and `/api/about` — never a blank map. */
final class BasemapStyles(deps: Deps, builtInText: () => String = () => TerrainStyleFile.builtInText()):

  private def cfg = deps.cfg.basemap

  /** The last answer and the key it was built for. */
  private val last = AtomicReference[Option[(String, TerrainStyles)]](None)

  // the built-in text, read once (a resource; the class is built once per server)
  private lazy val builtIn: String = builtInText()

  /** The style file looked for, and whether `basemap.style` named it. */
  def file: (Option[Path], Boolean) =
    BasemapStyles.file(cfg.style, deps.configFile.path.map(Path.of(_)), sys.props.get("user.home"))

  private def stamp(p: Path): String =
    scala.util.Try(s"${Files.size(p)}:${Files.getLastModifiedTime(p).toMillis}").getOrElse("unreadable")

  /** The styles as they are now (cached until the file or a setting it depends on changes). */
  def current: TerrainStyles =
    val (f, named) = file
    val present = f.filter(Files.isRegularFile(_))
    val key = List(f.fold("-")(_.toString), present.fold("absent")(stamp), cfg.labels.language, cfg.styleCheckSeconds.toString)
      .mkString("|")
    last.get match
      case Some((k, v)) if k == key => v
      case previous =>
        val next = build(f, present, named)
        last.set(Some(key -> next))
        if previous.forall(_._2.version != next.version) then
          if next.warnings.isEmpty then scribe.info(s"terrain styles: ${next.styles.map(_.id).mkString(", ")} (${next.source})")
          else next.warnings.foreach(w => scribe.warn(s"terrain styles: $w"))
        next

  private def build(f: Option[Path], present: Option[Path], named: Boolean): TerrainStyles =
    val read: Either[String, Option[(String, String)]] = present match
      case Some(p) => scala.util.Try(Files.readString(p)).toEither.left.map(e => s"$p cannot be read: ${e.getMessage}")
          .map(t => Some((p.toString, t)))
      case None => Right(None)
    val missing = f.filter(_ => named && present.isEmpty).map(p => s"basemap.style names $p, which is not there — the built-in styles are shown")
    val loaded = TerrainStyleFile.combine(builtIn, read.toOption.flatten).fold(
      errors => throw IllegalStateException(s"the built-in terrain styles are invalid: ${errors.mkString("; ")}"), identity)
    val warnings = read.left.toOption.toList ++ missing.toList ++ loaded.warnings
    val language = loaded.language.getOrElse(cfg.labels.language)
    val body = TerrainStyles(loaded.styles, loaded.default, language, loaded.source, f.map(_.toString), "", warnings,
      cfg.styleCheckSeconds)
    body.copy(version = BasemapStyles.digest(body.asJson.noSpaces))

  /** The warnings someone should see in health and about. */
  def warnings: List[String] = scala.util.Try(current.warnings).fold(e => List(s"terrain styles: ${e.getMessage}"), identity)

  /** The built-in document, as shipped (`--example`). */
  def example: String = builtIn

  /** A document checked over the built-in styles, not used. */
  def check(text: String): TerrainStyleCheck =
    TerrainStyleFile.combine(builtIn, Some(("the file", text))) match
      case Left(errors) => TerrainStyleCheck(ok = false, errors = errors)
      case Right(l) if l.source == TerrainStyles.BuiltIn && l.warnings.nonEmpty =>
        TerrainStyleCheck(ok = false, errors = l.warnings.drop(1).map(_.stripPrefix("the file: ")))
      case Right(l) =>
        TerrainStyleCheck(ok = true, styles = l.styles.map(_.id), default = Some(l.default),
          warnings = l.warnings.map(_.stripPrefix("the file: ")))

object BasemapStyles:

  /** Where the style file is: `basemap.style` (`~/` the home directory, relative = beside the config
    * file), else `basemap-style.conf` or `basemap-style.json` beside the config file (the first that is
    * there; the first when neither is). The flag says whether `basemap.style` named it. Pure. */
  def file(setting: String, configFile: Option[Path], home: Option[String]): (Option[Path], Boolean) =
    val dir = configFile.flatMap(p => Option(p.toAbsolutePath.getParent))
    Option(setting).map(_.trim).filter(_.nonEmpty) match
      case Some(s) =>
        val expanded = home.filter(_ => s == "~" || s.startsWith("~/")).fold(s)(h => h + s.drop(1))
        val p = Path.of(expanded)
        (Some((if p.isAbsolute then p else dir.fold(p)(_.resolve(p))).toAbsolutePath.normalize), true)
      case None =>
        val candidates = dir.toList.flatMap(d => List(d.resolve(TerrainStyleFile.FileName), d.resolve(TerrainStyleFile.JsonFileName)))
        (candidates.find(Files.isRegularFile(_)).orElse(candidates.headOption), false)

  /** A short, stable digest of the served styles: the page redraws when it changes. Pure. */
  def digest(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).take(8).map(b => f"$b%02x").mkString

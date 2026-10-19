package com.histmersion.core

import org.ekrich.config.{ConfigFactory, ConfigParseOptions, ConfigRenderOptions, ConfigSyntax}

import scala.util.Try

/** The terrain style documents as text: the built-in one (a classpath resource) and a person's file,
  * HOCON or JSON (JSON is HOCON), read into [[TerrainStyleDoc]] and combined. Pure given the texts —
  * the server reads the files and caches the result ([[TerrainStyles]]). */
object TerrainStyleFile:

  /** The built-in styles, beside `reference.conf`. */
  val Resource = "basemap/styles.conf"

  /** The file looked for beside the user config file when `basemap.style` is empty. */
  val FileName = "basemap-style.conf"

  /** The same, as JSON, also looked for (the first that exists wins). */
  val JsonFileName = "basemap-style.json"

  /** The built-in document's text (a resource read, not a constant: no work in an initialiser). */
  def builtInText(): String =
    val in = Option(getClass.getClassLoader.getResourceAsStream(Resource))
      .getOrElse(throw IllegalStateException(s"the built-in terrain styles ($Resource) are not on the class path"))
    try String(in.readAllBytes(), "UTF-8") finally in.close()

  /** HOCON (or JSON) text → JSON, its substitutions resolved; a syntax error names the line. Pure. */
  def toJson(text: String, origin: String): Either[String, io.circe.Json] =
    Try {
      val c = ConfigFactory.parseString(text, ConfigParseOptions.defaults.setOriginDescription(origin).setSyntax(ConfigSyntax.CONF))
      c.resolve().root.render(ConfigRenderOptions.concise)
    }.toEither.left.map(e => Option(e.getMessage).getOrElse(e.toString))
      .flatMap(s => io.circe.parser.parse(s).left.map(e => s"$origin: ${e.getMessage}"))

  /** What a load produced: the styles served, where they came from, and what was refused or ignored. */
  final case class Loaded(styles: List[TerrainStyle], default: String, language: Option[String], source: String,
      warnings: List[String])

  /** The built-in document alone. Its errors are a build fault, so a test holds it valid. Pure. */
  def builtIn(text: String): Either[List[String], (TerrainStyleDoc.Parsed, List[io.circe.JsonObject])] =
    toJson(text, "built-in terrain styles").left.map(List(_)).flatMap(TerrainStyleDoc.parse(_))

  /** The built-in styles with the user's file (`path`, `text`) over them. A file that does not parse
    * or has any error is refused whole: the built-in styles are served, and the errors become
    * warnings ("… is not used: …"), never a blank map. Pure. */
  def combine(builtInText: String, user: Option[(String, String)]): Either[List[String], Loaded] =
    builtIn(builtInText).map { (base, raw) =>
      val fallback = Loaded(base.styles, base.default.getOrElse(base.styles.head.id), base.language, TerrainStyles.BuiltIn, Nil)
      user match
        case None => fallback
        case Some((path, text)) =>
          toJson(text, path).left.map(List(_)).flatMap(TerrainStyleDoc.parse(_, raw)) match
            case Left(errors) =>
              fallback.copy(warnings = s"$path is not used — the built-in terrain styles are shown instead: " +
                s"${errors.size} ${if errors.size == 1 then "error" else "errors"}" :: errors.map(e => s"$path: $e"))
            case Right((mine, _)) =>
              Loaded(mine.styles, mine.default.orElse(base.default).filter(d => mine.styles.exists(_.id == d))
                .getOrElse(mine.styles.head.id), mine.language.orElse(base.language), s"$path over the built-in styles",
                mine.warnings.map(w => s"$path: $w"))
    }

package com.histmersion
package testsupport

/** Finds a type's code name where an English sentence should name it as a reader does: a dotted type path
  * (`Queue.QueueItem.Id`, `com.histmersion.x`) or a CamelCase type name (`ModelRef`). Quoted input ('…', "…", `…`) is
  * shown as given, the outside text after an em dash is the outside's, a throwable's class is what an internal failure
  * reports, the MCP library's framing of a decoding failure (circe's `DecodingFailure`, gone with G62) is its own, and
  * a field's name (`isPublicDomain`) is what the data calls it, so none of them counts.
  */
object TypeNamesShown:

  /** Names spelled in CamelCase in English too. */
  val ProperNames: Set[String] =
    Set(
      "GeoJSON",
      "YubiKey",
      "YubiKeys",
      "KeePassXC",
      "ChatGPT",
      "OpenAlex",
      "PostgreSQL",
      "CrossRef",
      "PeriodO",
      "WebP",
      "FeatureCollection",
      "GeoTIFF",
      "BigTIFF"
    )

  private val Dotted = """\b[A-Za-z][A-Za-z0-9]*(\.[A-Z][A-Za-z0-9]*)+\b""".r
  private val Camel = """\b[A-Z][a-z]+[A-Z][A-Za-z]*\b""".r

  def in(sentence: String): Option[String] =
    val own = sentence
      .replaceAll("^Invalid arguments: DecodingFailure at ", "Invalid arguments at ")
      .split(" — ")
      .head
      .replaceAll("'[^']*'", "''")
      .replaceAll("\"[^\"]*\"", "\"\"")
      .replaceAll("`[^`]*`", "``")
      .replaceAll("META-INF/\\S*", "")
      .replaceAll("\\b[a-z][\\w.]*\\.[A-Z]\\w*(Exception|Error)\\b", "")
    Dotted.findFirstIn(own).orElse(Camel.findAllIn(own).find(w => !ProperNames(w)))

  /** The problem sentences of a transcript: the `message` and `text` strings of its JSON bodies. */
  def sentencesOf(transcript: String): Vector[String] =
    """"(?:message|text)"\s*:\s*"((?:[^"\\]|\\.)*)"""".r
      .findAllMatchIn(transcript)
      .map(_.group(1).replace("\\\"", "\"").replace("\\n", "\n"))
      .toVector

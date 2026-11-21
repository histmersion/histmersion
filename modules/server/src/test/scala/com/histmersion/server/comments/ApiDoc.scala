package com.histmersion
package server
package comments

import scala.util.matching.Regex

final case class ApiDoc(doc: String, declaration: String):
  import ApiDoc.*

  private def prose: String =
    doc.linesIterator
      .map(_.trim.stripPrefix("/**").stripSuffix("*/").trim.stripPrefix("*").trim)
      .filter(_.nonEmpty)
      .mkString("\n")

  private lazy val signature: Set[String] = words(declaration).toSet

  def public: Boolean =
    definition.findPrefixMatchOf(declaration.trim).exists(m => Option(m.group("hidden")).isEmpty)

  def excuse: Option[String] =
    excuses.collectFirst { case (name, pattern) if pattern.findFirstIn(prose).isDefined => name }

  def restatedTags: List[String] =
    tagGroups(prose.linesIterator.toList).map(_.mkString(" ")).filter { group =>
      val tag = group.takeWhile(!_.isWhitespace)
      restatingTags.contains(tag) && content(group.drop(tag.length)).forall(signature)
    }

  def restatedTagLines: List[Int] =
    val raw = doc.linesIterator.toList
    val proseLines = raw.map(l => l.trim.stripPrefix("/**").stripSuffix("*/").trim.stripPrefix("*").trim)
    val restated = restatedTags.toSet
    val starts = proseLines.indices.filter(i => proseLines(i).startsWith("@")).toList
    starts.flatMap { start =>
      val next = starts.find(_ > start).getOrElse(raw.size)
      val span = (start until next).filter(i => proseLines(i).nonEmpty)
      Option.when(restated.contains(span.map(proseLines).mkString(" ")))(span).toList.flatten
    }

  def restatedShare: Double =
    val said = content(prose)
    if said.isEmpty then 1.0 else said.count(signature).toDouble / said.size

  def tautology: Boolean = restatedShare >= Threshold || restatedTags.nonEmpty

  def allowed: Boolean = public && excuse.isEmpty && !tautology

  def verdict: String =
    if !public then "doc not on a public definition"
    else
      excuse
        .map(e => s"doc excuse ($e): file an issue")
        .getOrElse(if tautology then "doc restating its signature" else "doc")

object ApiDoc:
  val PublicApiModules: Set[String] = Set("plugin-api", "plugin-testkit")

  val Threshold: Double = 0.8

  val restatingTags: Set[String] = Set("@param", "@tparam", "@return")

  val excuses: List[(String, Regex)] = List(
    "workaround" -> """(?i)\bwork[- ]?arounds?\b""".r,
    "hack" -> """(?i)\bhack(s|y|ed|ish)?\b""".r,
    "for now" -> """(?i)\bfor now\b""".r,
    "temporary" -> """(?i)\btemporar(y|ily)\b""".r,
    "TODO" -> """(?i)\b(todo|fixme|xxx)\b""".r,
    "until" -> """(?i)\buntil\b""".r,
    "kept for old" -> """(?i)\bkept\s+(for|so(\s+that)?)\b[^.]*\bold""".r,
    "legacy" -> """(?i)\blegacy\b""".r,
    "kept for old builds" -> """(?i)\b(compiled before|still compiles?)\b""".r,
    "should be but" -> """(?i)\bshould be\b[^.]*\bbut\b""".r,
    "cannot because" -> """(?i)\b(can't|cannot|can not)\b[^.]*\bbecause\b""".r
  )

  val stopWords: Set[String] = Set(
    "a",
    "an",
    "the",
    "of",
    "to",
    "in",
    "on",
    "at",
    "by",
    "for",
    "from",
    "with",
    "as",
    "is",
    "are",
    "be",
    "been",
    "it",
    "its",
    "this",
    "that",
    "these",
    "those",
    "and",
    "or",
    "if",
    "when",
    "then",
    "than",
    "which",
    "who",
    "what",
    "one",
    "returns",
    "return",
    "get",
    "gets",
    "given",
    "value",
    "param",
    "tparam",
    "new",
    "all",
    "any",
    "each",
    "some",
    "here",
    "there",
    "into",
    "out",
    "not",
    "no",
    "do",
    "does",
    "so",
    "such",
    "has",
    "have",
    "can",
    "will",
    "may",
    "must"
  )

  val definition: Regex =
    ("""^(?:@\S+\s+)*(?:(?<hidden>private|protected)(?:\[\w+\])?\s+|(?:final|sealed|abstract|override|implicit|lazy""" +
      """|inline|opaque|transparent|infix|open|case)\s+)*(?:def|val|var|class|trait|object|enum|type|given|case""" +
      """|extension)\b""").r

  private val identifier: Regex = """[A-Za-z][A-Za-z0-9]*""".r

  private val camel: Regex = """(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])""".r

  private def stem(word: String): String =
    if word.endsWith("ies") && word.length > 4 then word.dropRight(3) + "y"
    else if word.endsWith("s") && !word.endsWith("ss") && word.length > 3 then word.dropRight(1)
    else word

  def tagGroups(lines: List[String]): List[List[String]] =
    lines
      .foldLeft(List.empty[List[String]]) { (groups, line) =>
        if line.startsWith("@") then List(line) :: groups
        else
          groups match
            case current :: rest => (current :+ line) :: rest
            case Nil             => Nil
      }
      .reverse

  def parts(text: String): List[String] =
    identifier.findAllIn(text).toList.flatMap(camel.split(_)).map(_.toLowerCase).filter(_.length > 1)

  def words(text: String): List[String] = parts(text).map(stem)

  def content(text: String): List[String] = parts(text).filterNot(w => stopWords.contains(w)).map(stem)

  def declarationAfter(text: String, end: Int): String =
    val lines = text.substring(end).linesIterator.map(_.trim).filter(_.nonEmpty).toList
    val body = lines.dropWhile(l => l.startsWith("@") && definition.findPrefixMatchOf(l).isEmpty)
    val taken = body.take(8)
    val upTo = taken.indexWhere(l => l.endsWith("=") || l.endsWith("{") || l.endsWith(":"))
    (if upTo < 0 then taken else taken.take(upTo + 1)).mkString(" ")

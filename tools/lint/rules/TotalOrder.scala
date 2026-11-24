package com.histmersion.linting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

object TotalOrder:

  final case class Clause(file: String, line: Int, text: String, offset: Int):
    def row: String = s"$file\t$text"

  type Keys = Map[String, List[Set[String]]]

  private val Migrations: Path = Path.of("modules/db/src/main/resources/db/migration")

  def keys(sql: String): Keys =
    val constraint =
      """(?s)ALTER TABLE (?:ONLY )?(?:public\.)?(\w+)\s+ADD CONSTRAINT \w+ (?:PRIMARY KEY|UNIQUE) \(([^)]*)\)""".r
    val index = """CREATE UNIQUE INDEX \w+ ON (?:ONLY )?(?:public\.)?(\w+)(?: USING \w+)? \(([^)]*)\)""".r
    val inline = """(?s)CREATE TABLE (?:public\.)?(\w+) \((.*?)\n\);""".r
    val inlineKey = """(?:PRIMARY KEY|UNIQUE) \(([^)]*)\)""".r
    val found =
      constraint.findAllMatchIn(sql).map(m => m.group(1) -> m.group(2)) ++
        index.findAllMatchIn(sql).map(m => m.group(1) -> m.group(2)) ++
        inline.findAllMatchIn(sql).flatMap(t => inlineKey.findAllMatchIn(t.group(2)).map(k => t.group(1) -> k.group(1)))
    found.toList
      .map((table, cols) => table -> cols.split(',').map(_.trim).toSet)
      .filter((_, cols) => cols.forall(_.matches("\\w+")))
      .groupMap(_._1)(_._2)
      .view
      .mapValues(_.distinct)
      .toMap

  lazy val migrationKeys: Keys =
    if !Files.isDirectory(Migrations) then Map.empty
    else
      keys(
        Files
          .list(Migrations)
          .iterator
          .asScala
          .filter(_.toString.endsWith(".sql"))
          .toList
          .sorted
          .map(Files.readString(_, UTF_8))
          .mkString("\n")
      )

  private val OrderBy = "ORDER BY\\s+".r

  private val Stop = "(?s)^\\s+(LIMIT|OFFSET|FOR\\s|UNION|ON CONFLICT|FETCH|\\))".r

  def clauses(key: String, raw: String): List[(Clause, String)] =
    clausesAround(key, raw).map((c, before, _) => c -> before)

  private def clausesAround(key: String, raw: String): List[(Clause, String, String)] =
    OrderBy.findAllMatchIn(raw).toList.flatMap { m =>
      val lineStart = raw.lastIndexOf('\n', m.start) + 1
      val lead = raw.substring(lineStart, m.start).trim
      if lead.startsWith("*") || lead.startsWith("//") || lead.startsWith("/*") then Nil
      else
        val text = clauseAt(raw, m.end)
        val start = math.max(0, statementStart(raw, m.start))
        val before = raw.substring(start, m.start)
        val around = raw.substring(start, math.min(raw.length, m.end + AroundAfter))
        List((Clause(key, raw.substring(0, m.start).count(_ == '\n') + 1, text, m.start), before, around))
    }

  private val AroundAfter = 600

  private val Ordinality =
    """(?i)\)\s*WITH\s+ORDINALITY\s+(?:AS\s+)?([a-z_][a-z0-9_]*)\s*\(([^)]*)\)""".r

  def ordinals(sql: String): Set[(String, String)] =
    Ordinality
      .findAllMatchIn(sql)
      .flatMap(m => m.group(2).split(',').map(_.trim.toLowerCase).lastOption.map(m.group(1).toLowerCase -> _))
      .toSet

  def byOrdinal(clause: String, sql: String): Boolean =
    val ords = ordinals(sql)
    columns(clause).exists((alias, col) => ords.exists((a, o) => o == col && alias.forall(_ == a)))

  private def clauseAt(raw: String, from: Int): String =
    val out = new StringBuilder
    var i = from
    var depth = 0
    var braces = 0
    var done = false
    while i < raw.length && !done do
      val c = raw(i)
      if braces > 0 then
        if c == '{' then braces += 1 else if c == '}' then braces -= 1
        out += c
        i += 1
      else if raw.startsWith("${", i) then
        braces = 1
        out ++= "${"
        i += 2
      else if c == '"' && raw.substring(math.max(0, i - 8), i) == "COLLATE " && raw.startsWith("\"C\"", i) then
        out ++= "\"C\""
        i += 3
      else if c == '"' && raw.substring(math.max(0, i - 8), i) == "COLLATE " && raw.startsWith("\"C\\\"", i) then
        out ++= "\"C\""
        i += 4
      else if depth == 0 && c == '"' then done = true
      else if depth == 0 && Stop.findPrefixOf(raw.substring(i, math.min(raw.length, i + 200))).isDefined &&
        c.isWhitespace
      then done = true
      else if depth == 0 && c == ')' then done = true
      else
        if c == '(' then depth += 1 else if c == ')' then depth -= 1
        out += c
        i += 1
    out.toString.split("\\s+").filter(_.nonEmpty).mkString(" ").stripSuffix(",")

  private def statementStart(raw: String, at: Int): Int =
    val previous = math.max(raw.lastIndexOf("ORDER BY", at - 1) + 1, at - 3000)
    if TableRef.findFirstIn(raw.substring(previous, at)).isDefined then previous else math.max(0, at - 3000)

  private val TableRef =
    """(?i)\b(?:FROM|JOIN)\s+(?:ONLY\s+)?(?:public\.)?([a-z_][a-z0-9_]*)(?:\s+(?:AS\s+)?([a-z_][a-z0-9_]*))?""".r
  private val NotAlias = Set(
    "where",
    "on",
    "join",
    "left",
    "right",
    "inner",
    "outer",
    "cross",
    "full",
    "group",
    "order",
    "limit",
    "using",
    "natural",
    "lateral",
    "union",
    "set",
    "returning",
    "and",
    "or"
  )

  def tables(statement: String): Map[String, String] =
    TableRef
      .findAllMatchIn(statement)
      .flatMap { m =>
        val table = m.group(1).toLowerCase
        val alias = Option(m.group(2)).map(_.toLowerCase).filterNot(NotAlias)
        (table -> table) :: alias.map(_ -> table).toList
      }
      .toMap

  def pinned(statement: String): Set[(Option[String], String)] =
    """(?i)(?<![\w.])(?:([a-z_][a-z0-9_]*)\.)?([a-z_][a-z0-9_]*)\s*(?:=\s*(?:\$|\?|'|\d|lower\()|IS NULL)""".r
      .findAllMatchIn(statement)
      .map(m => (Option(m.group(1)).map(_.toLowerCase), m.group(2).toLowerCase))
      .toSet

  def columns(clause: String): List[(Option[String], String)] =
    terms(clause).flatMap { t =>
      val bare = t
        .replaceAll("""(?i)COLLATE\s+"?\w+"?""", "")
        .replaceAll("""(?i)\b(ASC|DESC|NULLS\s+(FIRST|LAST))\b""", "")
        .trim
      """(?i)^(?:([a-z_][a-z0-9_]*)\.)?([a-z_][a-z0-9_]*)$""".r
        .findFirstMatchIn(bare)
        .map(m => (Option(m.group(1)).map(_.toLowerCase), m.group(2).toLowerCase))
    }

  private def terms(clause: String): List[String] =
    val (last, done, _) = clause.foldLeft(("", List.empty[String], 0)) { case ((cur, acc, depth), c) =>
      c match
        case '(' | '{'         => (cur + c, acc, depth + 1)
        case ')' | '}'         => (cur + c, acc, depth - 1)
        case ',' if depth == 0 => ("", cur :: acc, depth)
        case other             => (cur + other, acc, depth)
    }
    (last :: done).reverse

  def total(clause: String, statement: String, keys: Keys): Boolean =
    val named = tables(statement)
    val cols = columns(clause)
    val pins = pinned(statement)
    def covers(table: String, key: Set[String]): Boolean =
      def names(ref: Option[String]): Boolean = ref.forall(r => named.get(r).contains(table))
      key.forall(c => cols.exists((a, col) => col == c && names(a)) || pins.exists((a, col) => col == c && names(a)))
    named.values.toSet.exists(t => keys.getOrElse(t, Nil).exists(covers(t, _)))

  def partial(sources: List[SourceFile], keys: Keys): List[Clause] =
    sources.flatMap(s => clausesAround(s.key, s.raw)).collect {
      case (c, statement, around) if !total(c.text, statement, keys) && !byOrdinal(c.text, around) => c
    }

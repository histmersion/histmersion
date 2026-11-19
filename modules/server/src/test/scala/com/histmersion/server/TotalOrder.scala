package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import com.histmersion.server.ArchitectureSuite.SourceFile

/** Whether an `ORDER BY` of the code's SQL names a total order: its terms, with the columns its `WHERE` pins to one
  * value, cover a primary key or a unique constraint of a table its statement reads. A list read in an order with ties
  * comes back in whatever order the plan gives, and a page of it may skip or repeat rows.
  *
  * The tables' keys are read from the migrations. A clause the reading cannot judge (a column of a sub-select or a CTE,
  * a select list's position, an expression) is a finding unless a person has reviewed it: then it is listed in
  * `architecture/total-order.tsv` with why it is total, or why its order does not matter.
  */
object TotalOrder:

  /** One `ORDER BY`: where it is and its clause as written, spaces collapsed. */
  final case class Clause(file: String, line: Int, text: String):
    def row: String = s"$file\t$text"

  /** A table's unique keys, each a set of its columns. */
  type Keys = Map[String, List[Set[String]]]

  private val Migrations: Path = Path.of("modules/db/src/main/resources/db/migration")

  /** The primary keys, unique constraints and unique indexes of plain columns the migrations declare, by table. */
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

  /** The keys of every migration. */
  def migrationKeys(): Keys =
    val sql = Files
      .list(Migrations)
      .iterator
      .asScala
      .filter(_.toString.endsWith(".sql"))
      .toList
      .sorted
      .map(Files.readString(_, UTF_8))
      .mkString("\n")
    keys(sql)

  private val OrderBy = "ORDER BY\\s+".r

  /** Where a clause ends: a `LIMIT`, `OFFSET`, `FOR`, `UNION`, `ON CONFLICT` or a parenthesis it did not open (the end
    * of the string literal ends it too).
    */
  private val Stop = "(?s)^\\s+(LIMIT|OFFSET|FOR\\s|UNION|ON CONFLICT|FETCH|\\))".r

  /** The `ORDER BY` clauses of the SQL in a source (`key`, its text `raw`), with the text of the statement before each
    * (where its tables and its pinned columns are). A clause in a comment is none.
    */
  def clauses(key: String, raw: String): List[(Clause, String)] =
    OrderBy.findAllMatchIn(raw).toList.flatMap { m =>
      val lineStart = raw.lastIndexOf('\n', m.start) + 1
      val lead = raw.substring(lineStart, m.start).trim
      if lead.startsWith("*") || lead.startsWith("//") || lead.startsWith("/*") then Nil
      else
        val text = clauseAt(raw, m.end)
        val before = raw.substring(math.max(0, statementStart(raw, m.start)), m.start)
        List(Clause(key, raw.substring(0, m.start).count(_ == '\n') + 1, text) -> before)
    }

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
      // the same collation as the last thing in a `sql"""…"""`, its closing quote escaped (`sql` reads escapes)
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

  /** Where the statement an `ORDER BY` belongs to starts: after the previous `ORDER BY`, or 3000 characters back when
    * that names no table (one of several clauses a `match` chooses between).
    */
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

  /** The tables a statement reads, by the name each is known by in it (its alias, and its own name). */
  def tables(statement: String): Map[String, String] =
    TableRef
      .findAllMatchIn(statement)
      .flatMap { m =>
        val table = m.group(1).toLowerCase
        val alias = Option(m.group(2)).map(_.toLowerCase).filterNot(NotAlias)
        (table -> table) :: alias.map(_ -> table).toList
      }
      .toMap

  /** The columns a statement's conditions pin to one value: `col = $x`, `a.col = ?`, `col = 'v'`, `col IS NULL`. */
  def pinned(statement: String): Set[(Option[String], String)] =
    """(?i)(?<![\w.])(?:([a-z_][a-z0-9_]*)\.)?([a-z_][a-z0-9_]*)\s*(?:=\s*(?:\$|\?|'|\d|lower\()|IS NULL)""".r
      .findAllMatchIn(statement)
      .map(m => (Option(m.group(1)).map(_.toLowerCase), m.group(2).toLowerCase))
      .toSet

  /** A clause's terms that name a column: `(alias, column)`, without collation, direction or nulls placement. */
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

  /** Does the clause order the statement's rows totally? */
  def total(clause: String, statement: String, keys: Keys): Boolean =
    val named = tables(statement)
    val cols = columns(clause)
    val pins = pinned(statement)
    def covers(table: String, key: Set[String]): Boolean =
      def names(ref: Option[String]): Boolean = ref.forall(r => named.get(r).contains(table))
      key.forall(c => cols.exists((a, col) => col == c && names(a)) || pins.exists((a, col) => col == c && names(a)))
    named.values.toSet.exists(t => keys.getOrElse(t, Nil).exists(covers(t, _)))

  /** The clauses of the sources that do not order totally. */
  def partial(sources: List[SourceFile], keys: Keys): List[Clause] =
    sources.flatMap(s => clauses(s.key, s.raw)).collect { case (c, statement) if !total(c.text, statement, keys) => c }

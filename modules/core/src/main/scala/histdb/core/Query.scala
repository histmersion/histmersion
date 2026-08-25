package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `histdb query "<SQL>"` — the read-only SQL escape hatch (plan §5.1 row `report`, §5.2 stream 2.8).
  *
  * The old workflow reached for `sync_db.py --query` whenever a question did not fit `search` /
  * `facets` / `aggregate`. This is that, against the live store: the statement runs inside a
  * `READ ONLY` transaction with `SET LOCAL statement_timeout` and a row cap, so **Postgres itself**
  * refuses every write — no statement sniffing here that a cleverly-worded query could walk around.
  *
  * Everything in this file is pure: the DTOs, the Markdown table, and the schema blurb that becomes
  * the MCP tool description. `db.QueryRepo` does the executing.
  */
object Query:

  /** The statement to run. One statement — a `;`-separated batch is rejected by the driver. */
  final case class QueryRequest(sql: String) derives KindlingsCodecAsObject
  object QueryRequest:
    given Schema[QueryRequest] = KindlingsSchema.derived[QueryRequest].schema

  /** Column names plus rows of stringified cells (NULL = absent), which is the only shape that
    * survives an arbitrary projection through one static JSON schema. `truncated` says the row cap
    * (`query.max-rows`) cut the result short. */
  final case class QueryResult(
      columns: List[String],
      rows: List[List[Option[String]]],
      rowCount: Int,
      truncated: Boolean,
      elapsedMs: Long
  ) derives KindlingsCodecAsObject
  object QueryResult:
    given Schema[QueryResult] = KindlingsSchema.derived[QueryResult].schema

  final case class SchemaColumn(name: String, dataType: String, nullable: Boolean)
      derives KindlingsCodecAsObject
  object SchemaColumn:
    given Schema[SchemaColumn] = KindlingsSchema.derived[SchemaColumn].schema

  final case class SchemaTable(name: String, kind: String, columns: List[SchemaColumn])
      derives KindlingsCodecAsObject
  object SchemaTable:
    given Schema[SchemaTable] = KindlingsSchema.derived[SchemaTable].schema

  /** `GET /api/schema/tables` — read from `information_schema` at request time, never a hand-written
    * list, so it cannot go stale as migrations land. */
  final case class SchemaTables(tables: List[SchemaTable]) derives KindlingsCodecAsObject
  object SchemaTables:
    given Schema[SchemaTables] = KindlingsSchema.derived[SchemaTables].schema

  // ── rendering (pure) ─────────────────────────────────────────────────────────────────────────

  private def cell(v: Option[String]): String =
    v.getOrElse("NULL").replace("|", "\\|").linesIterator.mkString(" ").trim

  /** A GitHub-flavoured Markdown table. Empty columns (a statement that returned nothing) render as
    * a note rather than a broken table, because an LLM reading `| |\n|---|` learns nothing. */
  def markdownTable(columns: List[String], rows: List[List[Option[String]]]): String =
    if columns.isEmpty then "_(no columns)_"
    else
      val header = columns.map(_.replace("|", "\\|")).mkString("| ", " | ", " |")
      val rule   = columns.map(_ => "---").mkString("|", "|", "|")
      val body   = rows.map(r => columns.indices.map(i => cell(r.lift(i).flatten)).mkString("| ", " | ", " |"))
      // NB: no stripMargin — every row starts with '|', which stripMargin would eat.
      (List(header, rule) ++ body).mkString("\n")

  def markdown(r: QueryResult): String =
    val head = s"### ${r.rowCount} row(s)${if r.truncated then " (truncated by the row cap)" else ""} · ${r.elapsedMs} ms"
    if r.rows.isEmpty then s"$head\n\n_no rows_"
    else s"$head\n\n${markdownTable(r.columns, r.rows)}"

  /** The columns worth naming in a tool description: the identity/join columns an LLM needs to write
    * a correct query, not all 24 of `sources`. Everything else is one `GET /api/schema/tables` away. */
  private val Key = Set("id", "citekey", "src", "dst", "kind", "list", "status", "title", "doi",
    "year", "topics", "regions", "tags", "period", "claim", "region_name", "geom", "ord", "topic",
    "slug", "created_at", "done_at", "text", "name", "label")

  def schemaMarkdown(s: SchemaTables): String =
    if s.tables.isEmpty then "_(no tables)_"
    else
      val rows = s.tables.map { t =>
        val cols = t.columns.map(c => s"${c.name} ${c.dataType}").mkString(", ")
        s"- **${t.name}** (${t.kind}) — $cols"
      }
      s"### ${s.tables.size} table(s)\n\n${rows.mkString("\n")}"

  /** The `query` MCP tool description, generated from the live schema so it lists the tables that
    * actually exist (and their key columns) instead of a list that rots as migrations land. */
  def toolDescription(s: SchemaTables): String =
    val tables =
      if s.tables.isEmpty then "(schema unavailable — call the schema endpoint)"
      else
        s.tables
          .map { t =>
            val keys = t.columns.map(_.name).filter(Key.contains)
            val cols = (if keys.isEmpty then t.columns.map(_.name).take(6) else keys).mkString(", ")
            s"${t.name}($cols)"
          }
          .mkString("; ")
    "Run ONE read-only SQL statement against the hist-db Postgres/PostGIS store and get columns + " +
      "rows back as a Markdown table. Use it for the questions `search`/`facets`/`histogram` cannot " +
      "express — cross-tabulations, counts by two dimensions, set differences. The statement runs " +
      "in a READ ONLY transaction with a statement timeout and a row cap, so INSERT/UPDATE/DELETE/" +
      "CREATE/DROP are refused by Postgres itself; SELECT only. Deep time is an int8range of SIGNED " +
      "years (BCE negative) — read the inclusive upper bound as `upper(period) - 1`. Geometry is " +
      "PostGIS; use ST_X/ST_Y/ST_AsText. Tables: " + tables + "."

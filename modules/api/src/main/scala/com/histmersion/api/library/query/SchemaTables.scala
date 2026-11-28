package com.histmersion
package api.library.query

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.library.Query.SchemaMarkdown
import com.histmersion.api.library.Query.ToolDescription

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SchemaTables(tables: List[SchemaTable]) derives JsonCodec, KindlingsSchema, api.json.Schema

object SchemaTables:
  private val Key = Set(
    "id",
    "citekey",
    "src",
    "dst",
    "kind",
    "list",
    "status",
    "title",
    "doi",
    "year",
    "topics",
    "regions",
    "tags",
    "period",
    "claim",
    "region_name",
    "geom",
    "ord",
    "topic",
    "slug",
    "created_at",
    "done_at",
    "text",
    "name",
    "label"
  )

  extension (s: SchemaTables)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def schemaMarkdown: SchemaMarkdown =
      if s.tables.isEmpty then SchemaMarkdown("_(no tables)_")
      else
        val rows = s.tables.map { t =>
          val cols = t.columns.map(c => s"${c.name} ${c.dataType}").mkString(", ")
          s"- **${t.name}** (${t.kind}) — $cols"
        }
        SchemaMarkdown(s"### ${s.tables.size} table(s)\n\n${rows.mkString("\n")}")

  extension (s: SchemaTables)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def toolDescription: ToolDescription =
      val tables =
        if s.tables.isEmpty then "(schema unavailable — call the schema endpoint)"
        else
          s.tables
            .map { t =>
              val keys = t.columns.map(_.name).filter(n => Key.contains(n.value))
              val cols = (if keys.isEmpty then t.columns.map(_.name).take(6) else keys).mkString(", ")
              s"${t.name}($cols)"
            }
            .mkString("; ")
      ToolDescription(
        "Run ONE read-only SQL statement against the Histmersion Postgres/PostGIS store and get columns + " +
          "rows back as a Markdown table. Use it for the questions `search`/`facets`/`histogram` cannot " +
          "express — cross-tabulations, counts by two dimensions, set differences. The statement runs " +
          "in a READ ONLY transaction with a statement timeout and a row cap, so INSERT/UPDATE/DELETE/" +
          "CREATE/DROP are refused by Postgres itself; SELECT only. Deep time is an int8range of SIGNED " +
          "years (BCE negative) — read the inclusive upper bound as `upper(period) - 1`. Geometry is " +
          "PostGIS; use ST_X/ST_Y/ST_AsText. Tables: " + tables + "."
      )

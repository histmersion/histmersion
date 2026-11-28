package com.histmersion
package api.library.query

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.library.Query.Markdown
import com.histmersion.api.library.Query.markdownTable

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class QueryResult(
    columns: List[QueryResult.Columns],
    rows: List[List[Option[QueryResult.Rows]]],
    rowCount: QueryResult.RowCount,
    truncated: QueryResult.Truncated,
    elapsedMs: QueryResult.ElapsedMs
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object QueryResult:
  type Columns = Columns.T
  object Columns extends Newtype[String, Pure]
  type Rows = Rows.T
  object Rows extends Newtype[String, Pure]
  type RowCount = RowCount.T
  object RowCount extends Newtype[Int, Pure]
  enum Truncated derives CanEqual:
    case Truncated, Complete
  object Truncated extends TwoStates[Truncated]:
    extension (truncated: Truncated) def truncated: Boolean = truncated == Truncated
  type ElapsedMs = ElapsedMs.T
  object ElapsedMs extends Newtype[Long, Pure]

  extension (r: QueryResult)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown: Markdown =
      val head =
        s"### ${r.rowCount} row(s)${
            if (r.truncated) == QueryResult.Truncated.Truncated then " (truncated by the row cap)" else ""
          } · ${r.elapsedMs} ms"
      if r.rows.isEmpty then Markdown(s"$head\n\n_no rows_")
      else Markdown(s"$head\n\n${markdownTable(r.columns, r.rows)}")

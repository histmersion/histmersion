package com.histmersion
package util.data.backup

import com.augustnagro.magnum.*
import com.histmersion.util.data.TableName
import com.histmersion.lint.{tolerated, LintRule}

/** The one database question backup and restore ask: how many rows a table a restore must not drop unasked holds.
  */
object BackupRepo:

  /** 0 when the table does not exist yet — a brand-new database is exactly the empty case a restore is allowed to
    * overwrite, and it must not look like an error.
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  def rows(table: TableName)(using DbCon): RowCount =
    RowCount(
      scala.util
        .Try(
          sql"SELECT count(*) FROM ${SqlLiteral(quoted(table))}".query[Int].run().headOption.getOrElse(0)
        )
        .getOrElse(0)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def quoted(table: TableName): String = "\"" + table.value.replace("\"", "\"\"") + "\""

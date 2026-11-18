package com.histmersion
package util.data.backup

import com.augustnagro.magnum.*
import com.histmersion.util.data.TableName

/** The one database question backup and restore ask: how many rows a table a restore must not drop unasked holds.
  */
object BackupRepo:

  /** 0 when the table does not exist yet — a brand-new database is exactly the empty case a restore is allowed to
    * overwrite, and it must not look like an error.
    */

  def rows(table: TableName)(using DbCon): RowCount =
    RowCount(
      scala.util
        .Try(
          sql"SELECT count(*) FROM ${SqlLiteral(quoted(table))}".query[Int].run().headOption.getOrElse(0)
        )
        .getOrElse(0)
    )

  private def quoted(table: TableName): String = "\"" + table.value.replace("\"", "\"\"") + "\""

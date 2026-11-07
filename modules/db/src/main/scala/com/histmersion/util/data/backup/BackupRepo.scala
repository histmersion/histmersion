package com.histmersion.util.data.backup

import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql

/** The one database question backup and restore ask: how many rows a table a restore must not drop unasked holds.
  */
object BackupRepo:

  /** 0 when the table does not exist yet — a brand-new database is exactly the empty case a restore is allowed to
    * overwrite, and it must not look like an error.
    */

  def rows(table: com.histmersion.util.data.TableName)(using DbCon): com.histmersion.RowCount =
    com.histmersion.RowCount(
      scala.util
        .Try(Sql.raw(Sql.RawText(s"SELECT count(*) FROM ${quoted(table)}")).frag.query[Int].run().headOption.getOrElse(0))
        .getOrElse(0)
    )

  private def quoted(table: com.histmersion.util.data.TableName): String = "\"" + table.value.replace("\"", "\"\"") + "\""

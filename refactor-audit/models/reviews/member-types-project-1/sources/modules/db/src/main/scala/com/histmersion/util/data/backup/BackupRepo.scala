package com.histmersion.util.data.backup

import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql

/** The one database question backup and restore ask: how many rows a table a restore must not drop unasked holds.
  */
object BackupRepo:

  /** 0 when the table does not exist yet — a brand-new database is exactly the empty case a restore is allowed to
    * overwrite, and it must not look like an error.
    */
  type RowsResult = RowsResult.T
  object RowsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowsTable = RowsTable.T
  object RowsTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotedResult = QuotedResult.T
  object QuotedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotedTable = QuotedTable.T
  object QuotedTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def rows(table: com.histmersion.util.data.backup.BackupRepo.RowsTable)(using DbCon): com.histmersion.util.data.backup.BackupRepo.RowsResult =
    scala.util
      .Try(Sql.raw(s"SELECT count(*) FROM ${quoted(table)}").frag.query[Int].run().headOption.getOrElse(0))
      .getOrElse(0)

  private def quoted(table: com.histmersion.util.data.backup.BackupRepo.QuotedTable): com.histmersion.util.data.backup.BackupRepo.QuotedResult = "\"" + table.replace("\"", "\"\"") + "\""

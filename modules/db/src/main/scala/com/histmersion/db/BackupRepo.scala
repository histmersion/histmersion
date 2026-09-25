package com.histmersion.db

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The only database question backup/restore asks (plan §1.10): how many sources are in there?
  * `POST /api/restore` refuses to `pg_restore --clean` over a populated database without `force`,
  * and reports the before/after counts so a restore is visibly a restore. */
object BackupRepo:

  /** 0 when the table does not exist yet — a brand-new database is exactly the empty case a restore
    * is allowed to overwrite, and it must not look like an error. */
  def sourceCount(ds: DataSource): Int =
    scala.util.Try(connect(ds)(sql"SELECT count(*) FROM sources".query[Int].run().headOption.getOrElse(0)))
      .getOrElse(0)

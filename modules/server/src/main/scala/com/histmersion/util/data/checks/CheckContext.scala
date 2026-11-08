package com.histmersion
package util.data.checks

import com.histmersion.api.checks.Lint.FormatChecker
import com.histmersion.config.AppConfig
import javax.sql.DataSource
import com.histmersion.api.text.WikiLinks
import com.histmersion.util.time.Timer

/** What the checks and their fixes are handed by the composition root: the settings they read, the database their fixes
  * write in their own transactions, the formatting check (given whether a citekey is stored) and the guarded write a
  * fix makes through a record's own patch.
  */
final case class CheckContext(
    config: AppConfig,
    dataSource: DataSource,
    format: (WikiLinks.Occurrence.Target => Boolean) => FormatChecker,
    write: GuardedWrite,
    timer: Timer
)

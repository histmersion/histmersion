package com.histmersion.util.data.checks

import com.histmersion.api.checks.Lint.FormatChecker
import com.histmersion.config.AppConfig
import javax.sql.DataSource

/** What the checks and their fixes are handed by the composition root: the settings they read, the database their fixes
  * write in their own transactions, the formatting check (given whether a citekey is stored) and the guarded write a
  * fix makes through a record's own patch.
  */
final case class CheckContext(
    config: AppConfig,
    dataSource: DataSource,
    format: (CheckContext.FormatArg1Arg1 => CheckContext.FormatArg1Result) => FormatChecker,
    write: GuardedWrite, timer: com.histmersion.util.time.Timer
)

object CheckContext:
  type FormatArg1Arg1 = FormatArg1Arg1.T
  object FormatArg1Arg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormatArg1Result = FormatArg1Result.T
  object FormatArg1Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

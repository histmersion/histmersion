package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion query` (plan §5.2 stream 2.8): the read-only SQL escape hatch's safety rails. `timeoutMs` becomes
  * `SET LOCAL statement_timeout` inside the READ ONLY transaction, `maxRows` caps the rows returned (one extra is
  * fetched to detect truncation) and `maxCellChars` caps one cell — `select ocr_markdown from sources` is otherwise 84
  * MB of JSON.
  */
/** `user`/`password` are the DEDICATED read-only login (`histmersion_query`, created by migration V011), not the
  * application role — see the three layers documented on [[Query]].
  */
final case class QueryConfig(user: QueryConfig.User, password: QueryConfig.Password, timeoutMs: QueryConfig.TimeoutMs, maxRows: QueryConfig.MaxRows, maxCellChars: QueryConfig.MaxCellChars)
    derives ConfigReader

object QueryConfig:
  type User = User.T
  object User extends RefinedType[String, Pure]
  type Password = Password.T
  object Password extends RefinedType[String, Pure]
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends RefinedType[Int, Pure]
  type MaxRows = MaxRows.T
  object MaxRows extends RefinedType[Int, Pure]
  type MaxCellChars = MaxCellChars.T
  object MaxCellChars extends RefinedType[Int, Pure]

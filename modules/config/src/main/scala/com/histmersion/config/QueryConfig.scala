package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** `histmersion query` (plan §5.2 stream 2.8): the read-only SQL escape hatch's safety rails. `timeoutMs` becomes
  * `SET LOCAL statement_timeout` inside the READ ONLY transaction, `maxRows` caps the rows returned (one extra is
  * fetched to detect truncation) and `maxCellChars` caps one cell — `select ocr_markdown from sources` is otherwise 84
  * MB of JSON.
  */
/** `user`/`password` are the DEDICATED read-only login (`histmersion_query`, created by migration V011), not the
  * application role — see the three layers documented on [[Query]].
  */
@movesTo(FeaturePlace.LibrarySearch)
final case class QueryConfig(
    user: QueryConfig.User,
    password: QueryConfig.Password,
    timeoutMs: QueryConfig.TimeoutMs,
    maxRows: QueryConfig.MaxRows,
    maxCellChars: QueryConfig.MaxCellChars
) derives ConfigReader

object QueryConfig:
  type User = User.T
  object User extends Newtype[String, Pure]
  type Password = Password.T
  object Password extends Newtype[String, Pure]
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends Newtype[Int, Pure]
  type MaxRows = MaxRows.T
  object MaxRows extends Newtype[Int, Pure]
  type MaxCellChars = MaxCellChars.T
  object MaxCellChars extends Newtype[Int, Pure]

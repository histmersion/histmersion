package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

final case class CollectionUpdateEntry(
    id: CollectionId,
    keys: List[CollectionUpdateEntry.Keys],
    outcome: CollectionUpdateOutcome
)

object CollectionUpdateEntry:
  type Keys = Keys.T
  object Keys extends Newtype[String, Pure]

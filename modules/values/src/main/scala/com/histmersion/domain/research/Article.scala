package com.histmersion.domain.research

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Article:

  /** An article's stable identity across renames, merges and databases (`articles.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

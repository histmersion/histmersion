package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** An article's stable identity across renames, merges and databases (`articles.uid`). */
type ArticleUid = ArticleUid.T

object ArticleUid extends RefinedType[java.util.UUID, Pure]

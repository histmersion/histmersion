package com.histmersion
package domain.research

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type Slug = Slug.T
object Slug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:
  extension (s: T) def isIndex: Boolean = s.value == "index"

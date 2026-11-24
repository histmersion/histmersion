package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
type ArchiveId = ArchiveId.T

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
object ArchiveId extends Newtype[String, Pure]

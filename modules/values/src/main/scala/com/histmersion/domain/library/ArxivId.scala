package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
type ArxivId = ArxivId.T

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
object ArxivId extends Newtype[String, Pure]

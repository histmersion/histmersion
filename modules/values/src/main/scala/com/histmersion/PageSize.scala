package com.histmersion
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Positive
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
type PageSize = PageSize.T

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
object PageSize extends Newtype[Int, Positive]

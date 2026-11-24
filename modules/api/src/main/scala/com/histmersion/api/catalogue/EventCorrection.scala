package com.histmersion
package api.catalogue

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventCorrection:
  type Status = Status.T
  object Status extends Newtype[String, Pure]

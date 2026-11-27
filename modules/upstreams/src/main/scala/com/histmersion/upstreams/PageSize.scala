package com.histmersion
package upstreams

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Bucket, "pending G10 (no layer buckets)")
@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
type PageSize = PageSize.T
@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
object PageSize extends Newtype[Int, Pure]

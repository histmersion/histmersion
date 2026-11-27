package com.histmersion
package util.ocr

import io.github.iltotore.iron.*
import com.histmersion.lint.{tolerated, LintRule}

/** A number of seconds a conversion may take, in all or per page (a negative one counts as none). */
@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
type Seconds = Seconds.T
@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
object Seconds extends Newtype[Int, Pure]

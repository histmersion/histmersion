package com.histmersion
package domain.library.workflows

import com.histmersion.domain.AgentVerify.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class StatedYear(value: ToSignedYearValue, unit: YearUnit.Wire)

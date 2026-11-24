package com.histmersion
package util.problem

import com.histmersion.lint.{BoundaryKind, atBoundary}

// a render boundary: the one place outside text is shown
@atBoundary(BoundaryKind.Render, "fixture: shows outside text")
object Edge:
  def shown(r: External.Reported): String = r.value // assert: GuidelineInventory (no G60external: a render file; G45boundary)

package com.histmersion
package util.problem

// a render boundary: the one place outside text is shown
object Edge:
  def shown(r: External.Reported): String = r.value // assert: GuidelineInventory (no G60external: a render file; G45boundary)

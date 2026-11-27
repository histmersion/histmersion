package fixture

import fixture.lint.{tolerated, LintRule}

object Other:
  @tolerated(LintRule.G44, "pending G44")
  def only(n: Int): Int = n

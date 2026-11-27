package fixture

import fixture.lint.{tolerated, LintRule}

object Example:
  @tolerated(LintRule.G3, "pending G3")
  @tolerated(LintRule.G44, "pending G44")
  def kept(n: Int): Int = n

  @tolerated(LintRule.G44, "pending G44")
  def dropped(n: Int): Int = n

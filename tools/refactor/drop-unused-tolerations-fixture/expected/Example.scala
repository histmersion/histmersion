package fixture

import fixture.lint.{tolerated, LintRule}

object Example:
  @tolerated(LintRule.G3, "pending G3")
  def kept(n: Int): Int = n

  def dropped(n: Int): Int = n

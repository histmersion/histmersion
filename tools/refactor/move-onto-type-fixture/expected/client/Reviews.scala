package fixture.client

import fixture.api.Issue

object Reviews:
  val issue: Issue = Issue.of("rule")

  val heavy: Int = Issue("other", 3).severity

  val worst = Issue.worst(issue :: Issue.none)

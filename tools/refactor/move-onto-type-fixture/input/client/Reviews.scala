package fixture.client

import fixture.api.Review

object Reviews:
  val issue: Review.Issue = Review.Issue.of("rule")

  val heavy: Int = Review.severity(Review.Issue("other", 3))

  val worst = Review.worst(issue :: Review.none)

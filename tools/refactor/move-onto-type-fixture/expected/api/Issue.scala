package fixture.api

final case class Issue(rule: String, weight: Int)

object Issue:
  def of(rule: String): Issue = Issue(rule, 1)

  val none: List[Issue] = Nil

  extension (issue: Issue) def severity: Int = issue.weight * 2

  def worst(issues: List[Issue]): Option[Issue] = issues.sortBy(severity).lastOption

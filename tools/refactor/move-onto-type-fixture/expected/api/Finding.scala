package fixture.api

final case class Finding(rule: String, line: Int)

def worst(findings: List[Finding]): Option[Finding] = findings.sortBy(_.line).headOption

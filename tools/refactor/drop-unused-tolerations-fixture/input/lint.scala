package fixture.lint

enum LintRule:
  case G3, G44

final class tolerated(rule: LintRule, reason: String) extends scala.annotation.StaticAnnotation

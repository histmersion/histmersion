import Suppressions.*

class SuppressionsSuite extends munit.FunSuite:

  test("each @tolerated counts for its rule, @movesTo and @atBoundary for themselves, a main Unused call for acceptedOutcome"):
    val file =
      """@atBoundary(BoundaryKind.Sql, "reads rows")
        |@movesTo(FeaturePlace.LibrarySources)
        |object A:
        |  @tolerated(LintRule.G44, "pending G44")
        |  @tolerated(
        |    LintRule.Copy,
        |    "pending G19"
        |  )
        |  def f = Unused(write(), Unused.Why.Held)
        |  def g(u: Any) = u match
        |    case Unused(a, _) => a
        |""".stripMargin
    assertEquals(
      counts(List("modules/db/src/main/scala/A.scala" -> file)),
      Map("atBoundary" -> 1, "movesTo" -> 1, "G44" -> 1, "Copy" -> 1, "acceptedOutcome" -> 1)
    )
    assertEquals(counts(List("modules/db/src/test/scala/A.scala" -> "val u = Unused(a, b)")), Map.empty[String, Int])
    assertEquals(counts(List(UnusedFile -> "final case class Unused(a: Int)")), Map.empty[String, Int])

  test("the rules of the enum are its cases, several to a line"):
    val text = "enum LintRule:\n  case G44, G45Boundary,\n    Copy\n  case Direction\n"
    assertEquals(ruleCases(text), Set("G44", "G45Boundary", "Copy", "Direction"))

  test("a key rises when its head count is above the base's, unless the base does not know it"):
    val rows = compare(Map("G44" -> 3, "Copy" -> 2), Map("G44" -> 4, "Copy" -> 1, "Fresh" -> 9), k => k != "Fresh")
    assertEquals(rows.filter(_.rose).map(_.key), List("G44"))
    assertEquals(rows.find(_.key == "Fresh").map(_.introduced), Some(true))
    assertEquals(rows.find(_.key == "Copy").map(r => (r.base, r.head)), Some((2, 1)))

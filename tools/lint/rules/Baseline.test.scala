package com.histmersion.linting

import scala.meta.*

class BaselineSuite extends munit.FunSuite:

  private def parse(code: String): Source = dialects.Scala3(Input.VirtualFile("A.scala", code)).parse[Source].get

  test("an annotation whose import is missing asks for it and its enum; one already imported asks for nothing"):
    val tree = parse(
      """package com.histmersion
        |package x
        |
        |import com.histmersion.lint.{BoundaryKind, atBoundary}
        |
        |@atBoundary(BoundaryKind.Http, "calls the server")
        |final class A:
        |  @tolerated(LintRule.G44, "pending G44")
        |  def one = a.value
        |""".stripMargin
    )
    assertEquals(Baseline.missingImports(tree), Set("LintRule", "tolerated"))

  test("a file with every lint import, or with no lint annotation, asks for nothing"):
    val imported = parse(
      """package x
        |import com.histmersion.lint.{FeaturePlace, movesTo}
        |@movesTo(FeaturePlace.Api)
        |object A
        |""".stripMargin
    )
    val plain = parse("package x\n@deprecated(\"old\") object B\n")
    assertEquals(Baseline.missingImports(imported), Set.empty[String])
    assertEquals(Baseline.missingImports(plain), Set.empty[String])

package com.histmersion.linting

import scala.meta.*

class ToleranceSuite extends munit.FunSuite:

  private def parse(code: String): Source = dialects.Scala3(Input.VirtualFile("A.scala", code)).parse[Source].get

  private def at(tree: Tree, word: String, rule: String): Finding =
    val start = tree.pos.input.text.indexOf(word)
    Finding(rule, Position.Range(tree.pos.input, start, start + word.length), word)

  private val code =
    """package com.histmersion
      |package x
      |
      |import com.histmersion.lint.{LintRule, tolerated}
      |
      |object A:
      |  @tolerated(LintRule.G44, "pending G44 (newtypes)")
      |  def one = a.value
      |  def two = b.value
      |  @tolerated(LintRule.Copy, "pending G19")
      |  def three = c
      |""".stripMargin

  test("a finding inside a definition tolerated for its rule is dropped; one elsewhere, or of another rule, stays"):
    val tree = parse(code)
    val open = Tolerance.open(List(at(tree, "a.value", "G44"), at(tree, "b.value", "G44"), at(tree, "a.value", "Copy")), tree)
    assertEquals(open.map(f => (f.rule, f.message)), List(("G44", "b.value"), ("Copy", "a.value")))

  test("a tolerated definition with no finding of its rule left is reported as unused, for the rules a lint owns"):
    val tree = parse(code)
    val findings = List(at(tree, "a.value", "G44"))
    val reported = Tolerance.diagnostics(findings, tree, Set("G44", "Copy"))
    assertEquals(reported.map(_.rule), List("Copy"))
    assert(reported.head.message.startsWith("unused"))
    assertEquals(Tolerance.diagnostics(findings, tree, Set("G44")), Nil)

  test("a file's rule is tolerated once, on a top-level definition, and covers the whole file"):
    val file =
      """package com.histmersion.x
        |
        |import com.histmersion.domain.library.Source
        |
        |@tolerated(LintRule.Direction, "pending G12")
        |object A:
        |  def f(s: Source) = s
        |object B:
        |  @tolerated(LintRule.Direction, "pending G12")
        |  def g = 1
        |""".stripMargin
    val tree = parse(file)
    val findings = List(at(tree, "Source\n", "Direction"), at(tree, "Source)", "Direction"))
    val reported = Tolerance.diagnostics(findings, tree, Set("Direction"))
    assertEquals(reported.map(_.message), List("Direction is a file's rule: it is tolerated on a top-level definition"))

  test("a finding outside every definition is covered by its rule's annotation on a top-level definition"):
    val file = "import com.histmersion.util.problem.Legacy\n\n@tolerated(LintRule.Unclassified, \"pending G60\")\nobject A\n"
    val tree = parse(file)
    assertEquals(Tolerance.diagnostics(List(at(tree, "Legacy", "Unclassified")), tree, Set("Unclassified")), Nil)

  test("@movesTo is the placement's own toleration: on a top-level definition, used while the file moves"):
    val tree = parse("@movesTo(FeaturePlace.Media)\nobject A\n")
    assertEquals(Tolerance.marks(tree).map(m => (m.rule, m.topLevel)), List(("Placement", true)))
    assertEquals(Tolerance.diagnostics(Nil, tree, Set("Placement")).map(_.message), List("unused: no Placement finding in this file"))

  test("the shape of a suppression: a rule of the enum, a literal reason, on a definition, imported by name"):
    val bad =
      """import com.histmersion.lint.{tolerated as ok}
        |@tolerated(LintRule.G44, "")
        |@atBoundary(BoundaryKind.Sql, "reads rows")
        |object A:
        |  @atBoundary(BoundaryKind.Sql, "again")
        |  def f = (x: @tolerated(LintRule.G44, "why"))
        |  @tolerated(LintRule.G44, s"pending $x")
        |  def g = 1
        |  @SuppressWarnings(Array("scalafix:G44"))
        |  def h = 1
        |""".stripMargin
    val tree = parse(bad)
    val messages = Shape.findings(tree, tree.tokens).map(_.message)
    assertEquals(messages.count(_.startsWith("@tolerated(LintRule")), 2)
    assertEquals(messages.count(_.startsWith("@atBoundary(")), 1)
    assert(messages.exists(_.contains("never an expression")), messages)
    assert(messages.exists(_.contains("imported by their own names")), messages)
    assert(messages.exists(_.contains("once per file")), messages)
    assert(messages.count(_.startsWith("a lint finding is tolerated")) == 1, messages)
    val good = parse("@tolerated(LintRule.G44, \"pending G44\")\nobject A\n")
    assertEquals(Shape.findings(good, good.tokens), Nil)

  test("the baseline annotates the innermost member definition, a file's rule the first top-level one"):
    val tree = parse(code)
    val member = Baseline.target(at(tree, "b.value", "G44"), tree).collect { case d: Defn.Def => d.name.value }
    assertEquals(member, Some("two"))
    val file = Baseline.target(at(tree, "b.value", "Direction"), tree).collect { case d: Defn.Object => d.name.value }
    assertEquals(file, Some("A"))
    val nested = parse("object A:\n  def f =\n    def local = x.value\n    local\n")
    assertEquals(Baseline.target(at(nested, "x.value", "G44"), nested).collect { case d: Defn.Def => d.name.value }, Some("f"))

  test("the baseline's annotation keeps the definition's indentation and quotes its reason"):
    val tree = parse(code)
    val two = tree.collect { case d: Defn.Def if d.name.value == "two" => d }.head
    assertEquals(Baseline.indentOf(two), "  ")
    assertEquals(Baseline.quoted("a \"b\" \\c"), "\"a \\\"b\\\" \\\\c\"")
    assertEquals(Baseline.kindCase("cli-args"), "CliArgs")
    assertEquals(Baseline.kindCase("sql"), "Sql")

package com.histmersion
package util.problem

import util.problem.Text.sentence
import ProblemLog.line
import com.histmersion.lint.{LintRule, tolerated}

/** The log line of every case: its group and case, each of its fields by name, then its English sentence. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ProblemLinesSuite extends munit.FunSuite:

  Problem.examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: its log line"):
      val logged = p.line
      assert(logged.startsWith(s"${p.group.productPrefix}.${p.productPrefix}"), logged)
      p.productElementNames.foreach(name => assert(logged.contains(s" $name="), s"$name in $logged"))
      assert(logged.endsWith(s": ${p.sentence}"), logged)
  }

  test("a value with a space is quoted, so the line splits back into its fields"):
    val p = Problem.Upstream.Unreachable.here(
      Problem.Upstream.Name("core"),
      Problem.Upstream.Url("https://core.ac.uk"),
      External.Reported("java.io.IOException: reset \"by peer\"")
    )
    assertEquals(
      p.line,
      "Upstream.Unreachable upstream=core url=https://core.ac.uk reported=\"java.io.IOException: reset \\\"by peer\\\"\" at=Vector(): " +
        "cannot reach core at https://core.ac.uk — java.io.IOException: reset \"by peer\""
    )

  test("an unexpected exception gets a problem id, which its log line and its sentence both name"):
    ProblemLog.unexpected(IllegalStateException("pg_dump vanished"), Problem.Where("backup")) match
      case p @ Problem.Internal.Unexpected(id, _, _, _) =>
        assert(p.line.contains(s" id=$id "), p.line)
        assert(p.sentence.contains(s"problem $id"), p.sentence)
      case other => fail(s"not an unexpected problem: ${other.sentence}")

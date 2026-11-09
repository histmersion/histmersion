package com.histmersion
package util.problem

import api.problem.Text.sentence
import ProblemLog.line

/** The log line of every case: its group and case, each of its fields by name, then its English sentence. */
class ProblemLinesSuite extends munit.FunSuite:

  Problem.examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: its log line"):
      val logged = p.line
      assert(logged.startsWith(s"${p.group.productPrefix}.${p.productPrefix}"), logged)
      p.productElementNames.foreach(name => assert(logged.contains(s" $name="), s"$name in $logged"))
      assert(logged.endsWith(s": ${p.sentence}"), logged)
  }

  test("a value with a space is quoted, so the line splits back into its fields"):
    val p = Problem.Upstream.Unreachable(
      Problem.Upstream.Name("core"),
      Problem.Upstream.Url("https://core.ac.uk"),
      External.Reported("java.io.IOException: reset \"by peer\"")
    )
    assertEquals(
      p.line,
      "Upstream.Unreachable upstream=core url=https://core.ac.uk reported=\"java.io.IOException: reset \\\"by peer\\\"\": " +
        "cannot reach core at https://core.ac.uk — java.io.IOException: reset \"by peer\""
    )

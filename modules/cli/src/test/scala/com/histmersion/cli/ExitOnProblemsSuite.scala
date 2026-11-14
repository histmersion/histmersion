package com.histmersion
package cli

import util.problem.{Problem, Problems}
import util.problem.Problem.*

/** The exit code of every case, met by the CLI itself and answered by the server. */
class ExitOnProblemsSuite extends munit.FunSuite:

  examples.foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: its exit code"):
      val local = p match
        case _: Upstream => ExitCode.Unreachable
        case _           => ExitCode.Usage
      assertEquals(ExitCode.of(p, ExitCode.Origin.Local), local)
      assertEquals(ExitCode.of(p, ExitCode.Origin.Server), ExitCode.Refused)
  }

  test("the server out of reach outweighs a usage error among several problems"):
    val unreachable = examples.collect { case u: Upstream.Unreachable => u }.head
    assertEquals(
      ExitCode.of(Problems.of(Invalid.Missing.here(Field("x")), unreachable), ExitCode.Origin.Local),
      ExitCode.Unreachable
    )
    assertEquals(ExitCode.of(Problems.of(Invalid.Missing.here(Field("x"))), ExitCode.Origin.Local), ExitCode.Usage)

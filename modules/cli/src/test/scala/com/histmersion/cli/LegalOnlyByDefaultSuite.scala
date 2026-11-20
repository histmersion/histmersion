package com.histmersion
package cli

import com.histmersion.util.problem.Problem

/** `histmersion ingest <doi>`: legal-only unless the person asks for more (`--allow-unauthorised`). */
class LegalOnlyByDefaultSuite extends munit.FunSuite:

  private def legalOnly(args: String*): Either[CommandLine.Outcome, Boolean] =
    Parsing.parse("ingest" +: "10.1038/x" +: args*).map {
      case i: AcquireCmd.Command.Ingest => i.legalOnly
      case other                        => fail(s"not an ingest: $other")
    }

  test("an ingest by identifier is legal-only by default"):
    assertEquals(legalOnly(), Right(true))
    assertEquals(legalOnly("--legal-only"), Right(true))

  test("only --allow-unauthorised turns the legal-only default off"):
    assertEquals(legalOnly("--allow-unauthorised"), Right(false))

  test("both together are refused"):
    assert(legalOnly("--legal-only", "--allow-unauthorised").isLeft)
    assertEquals(
      AcquireCmd.legalOnly(legalOnly = true, allowUnauthorised = true).left.map(_.productPrefix),
      Left("Inconsistent")
    )

  test("the pure rule"):
    assertEquals(AcquireCmd.legalOnly(legalOnly = false, allowUnauthorised = false), Right(true))
    assertEquals(AcquireCmd.legalOnly(legalOnly = true, allowUnauthorised = false), Right(true))
    assertEquals(AcquireCmd.legalOnly(legalOnly = false, allowUnauthorised = true), Right(false))
    assert(
      AcquireCmd
        .legalOnly(legalOnly = true, allowUnauthorised = true)
        .left
        .exists(_.isInstanceOf[Problem.Invalid.Inconsistent])
    )

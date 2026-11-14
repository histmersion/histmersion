package com.histmersion
package values

import com.histmersion.values.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.AttestationTarget
import com.histmersion.util.problem.Problem

class AttestationTargetSuite extends munit.FunSuite:

  test("#3770: a target type is one of the five, trimmed and in any case; anything else is named with them"):
    assertEquals(AttestationTarget.check(AttestationTarget.Wire(" Event ")), Right("event"))
    assertEquals(AttestationTarget.check(AttestationTarget.Wire("event_dating")), Right("event_dating"))
    assertEquals(
      AttestationTarget.check(AttestationTarget.Wire("bogus")),
      Left(
        Problem.Invalid.Unknown.here(
          Problem.TypeName("com.histmersion.domain.catalogue.AttestationTarget.Wire"),
          Problem.Raw("bogus"),
          Vector("finding", "event", "event_dating", "place", "edge").map(r => Problem.Raw(s"$r"))
        )
      )
    )
    assert(AttestationTarget.check(AttestationTarget.Wire("")).isLeft)

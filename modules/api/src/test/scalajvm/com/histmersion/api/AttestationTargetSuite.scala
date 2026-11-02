package com.histmersion.api

import com.histmersion.domain.catalogue.AttestationTarget

class AttestationTargetSuite extends munit.FunSuite:

  test("#3770: a target type is one of the five, trimmed and in any case; anything else is named with them"):
    assertEquals(AttestationTarget.check(" Event "), Right("event"))
    assertEquals(AttestationTarget.check("event_dating"), Right("event_dating"))
    assertEquals(
      AttestationTarget.check("bogus"),
      Left("unknown target type 'bogus' (finding, event, event_dating, place, edge)")
    )
    assert(AttestationTarget.check("").isLeft)

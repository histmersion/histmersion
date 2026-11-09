package com.histmersion
package values

import com.histmersion.values.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.AttestationTarget

class AttestationTargetSuite extends munit.FunSuite:

  test("#3770: a target type is one of the five, trimmed and in any case; anything else is named with them"):
    assertEquals(AttestationTarget.check(AttestationTarget.Wire(" Event ")), Right("event"))
    assertEquals(AttestationTarget.check(AttestationTarget.Wire("event_dating")), Right("event_dating"))
    assertEquals(
      AttestationTarget.check(AttestationTarget.Wire("bogus")).left.map(_.legacyText),
      Left("unknown target type 'bogus' (finding, event, event_dating, place, edge)")
    )
    assert(AttestationTarget.check(AttestationTarget.Wire("")).isLeft)

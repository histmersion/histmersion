package com.histmersion
package testsupport

import com.histmersion.api.library.Facet
import ComparedByValue.given

class StateComparisonSuite extends munit.FunSuite:

  test("a two-state enum equals the Boolean it replaced: its first case true, its second false, nested too"):
    assertEquals(Facet.Contested.Contested, true)
    assertEquals(Facet.Contested.Settled, false)
    assertEquals((Some(Facet.Contested.Settled), 3), (Some(false), 3))
    assertEquals(List(Facet.Contested.Contested, Facet.Contested.Settled), List(true, false))
    assertEquals(Right(Map("a" -> Facet.Contested.Contested)), Right(Map("a" -> true)))

  test("a mismatch is still one"):
    assert(!ComparedByValue.sameValue(Facet.Contested.Contested, false))
    assert(!ComparedByValue.sameValue((Facet.Contested.Settled, 1), (false, 2)))
    assert(!ComparedByValue.sameValue(List(Facet.Contested.Settled), List(false, true)))

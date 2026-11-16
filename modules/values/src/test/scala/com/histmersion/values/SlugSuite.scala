package com.histmersion
package values

import com.histmersion.values.testsupport.ComparedByValue.given

/** An article's slug as a person or a model writes it: trimmed and lower-cased, or why it is none. */

import com.histmersion.domain.research.Slug
import com.histmersion.util.problem.Problem
class SlugSuite extends munit.FunSuite:

  test("a slug is trimmed and lower-cased; '.', '-' and '_' are kept"):
    assertEquals(Slug.parse("  Sea_Peoples ").map(s => s.value: String), Right("sea_peoples"))
    assertEquals(Slug.parse("uluburun.ship-1").map(s => s.value: String), Right("uluburun.ship-1"))

  test("what is no slug is Malformed, naming the type and what was written"):
    def refused(raw: String) = Slug.parse(raw) match
      case Left(Problem.Invalid.Malformed(t, _, r, _)) => Some((s"$t", s"$r"))
      case _                                           => None
    assertEquals(refused("Bad Slug"), Some(("Slug", "Bad Slug")))
    assertEquals(refused(""), Some(("Slug", "")))
    assertEquals(refused("_lead"), Some(("Slug", "_lead")))
    assert(Slug.parse("a" * 121).isRight)
    assert(Slug.parse("a" * 122).isLeft)

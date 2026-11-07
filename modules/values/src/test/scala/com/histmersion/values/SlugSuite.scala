package com.histmersion.values

import com.histmersion.values.testsupport.ComparedByValue.given

/** An article's slug as a person or a model writes it: trimmed and lower-cased, or why it is none. */

import com.histmersion.domain.research.Slug
class SlugSuite extends munit.FunSuite:

  test("a slug is trimmed and lower-cased; '.', '-' and '_' are kept"):
    assertEquals(Slug.parse("  Sea_Peoples ").map(s => s.value: String), Right("sea_peoples"))
    assertEquals(Slug.parse("uluburun.ship-1").map(s => s.value: String), Right("uluburun.ship-1"))

  test("what is no slug says so, naming what was written"):
    val why = "use lower-case letters, digits, '_', '-' or '.' (e.g. sea_peoples)"
    assertEquals(Slug.parse("Bad Slug"), Left(s"invalid slug 'Bad Slug' — $why"))
    assertEquals(Slug.parse(""), Left(s"invalid slug '' — $why"))
    assertEquals(Slug.parse("_lead"), Left(s"invalid slug '_lead' — $why"))
    assert(Slug.parse("a" * 121).isRight)
    assert(Slug.parse("a" * 122).isLeft)

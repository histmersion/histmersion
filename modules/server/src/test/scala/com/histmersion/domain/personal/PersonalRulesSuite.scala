package com.histmersion.domain.personal

import com.histmersion.api.personal.*

import java.time.Instant

class PersonalRulesSuite extends munit.FunSuite:

  test("times are kept and written to the microsecond, as Postgres keeps them"):
    assertEquals(PersonalRules.iso(Instant.parse("2026-10-30T12:00:00.123456789Z")), "2026-10-30T12:00:00.123456Z")
    assertEquals(PersonalRules.iso(Instant.parse("2026-10-30T12:00:00Z")), "2026-10-30T12:00:00.000000Z")

  test("a title: the item's now, else the one kept, else its key or its kind"):
    val src = Target.item(ItemKind.Source, "zz")
    assertEquals(PersonalRules.title(src, Some(" Now "), Some("then")), "Now")
    assertEquals(PersonalRules.title(src, None, Some("then  and\nnow")), "then and now")
    assertEquals(PersonalRules.title(src, Some(" "), None), "zz")
    assertEquals(PersonalRules.title(Target(ItemKind.View), None, None), "Saved view")

  test("labels and texts tidied and bounded"):
    assertEquals(PersonalRules.label(Some(BookmarkLabel("  "))), Right(None))
    assert(PersonalRules.label(Some(BookmarkLabel("x" * 201))).isLeft)
    assertEquals(PersonalRules.text(NoteText("a\r\nb  \n\n")).map(_.value.toString), Right("a\nb"))
    assert(PersonalRules.text(NoteText("x" * (NoteText.MaxChars + 1))).isLeft)

  test("search: every word, case ignored"):
    val ws = PersonalRules.words(Some("  Bronze  AGE "))
    assertEquals(ws, List("bronze", "age"))
    assert(PersonalRules.matches(ws, "The Bronze", "Age"))
    assert(!PersonalRules.matches(ws, "bronze only"))
    assert(PersonalRules.matches(Nil, ""))

  test("newest first, then the higher id"):
    val t = Instant.parse("2026-10-30T12:00:00Z")
    val rows = List(("a", t), ("c", t), ("b", t.plusSeconds(1)))
    assertEquals(PersonalRules.newestFirst(rows)(_._2, _._1).map(_._1), List("b", "c", "a"))

  test("a stored row read back: unknown kinds and out-of-range values are dropped, not guessed"):
    assertEquals(PersonalRules.target("nonsense", None, None, None, None, None, None, None), None)
    val p = PersonalRules.target("point", None, Some(91.0), Some(22.0), None, Some(-1300L), None, None).get
    assertEquals((p.lat, p.lon.map(_.value.toDouble), p.from.map(_.value.toInt)), (None, Some(22.0), Some(-1300)))

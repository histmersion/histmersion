package com.histmersion.api

import com.histmersion.values.BundleKind


/** The one alias rule's words (#1799): every kind's rename says the same thing, and an answer to a
  * call that named an old identifier says where it went. */
class AliasesSuite extends munit.FunSuite:

  test("the rule names the kind's identifier"):
    assert(Aliases.rule(AliasKind.Source).startsWith("The old citekey stays an alias"), Aliases.rule(AliasKind.Source))
    assert(Aliases.rule(AliasKind.Goal).contains("under its new slug"), Aliases.rule(AliasKind.Goal))
    AliasKind.values.foreach(k => assert(Aliases.rule(k).contains("every write"), k.toString))

  test("followed: a note only when the identifier asked for was an old one"):
    assertEquals(Aliases.followed(AliasKind.Event, "zz_old", "zz_new"),
      Some("_`zz_old` is an old id of this event — it went to `zz_new`._"))
    assertEquals(Aliases.followed(AliasKind.Event, " zz_new ", "zz_new"), None)
    assertEquals(Aliases.note(AliasKind.Goal, "a", "a")("md"), "md")
    assert(Aliases.note(AliasKind.Goal, "a", "b")("md").endsWith("\n\nmd"))

  test("every label kind of a bundle that can be renamed has an alias kind"):
    assertEquals(List(BundleKind.Article, BundleKind.Event, BundleKind.Goal, BundleKind.Place).map(AliasKind.of),
      List(Some(AliasKind.Article), Some(AliasKind.Event), Some(AliasKind.Goal), Some(AliasKind.Place)))
    assertEquals(AliasKind.of(BundleKind.Finding), None)

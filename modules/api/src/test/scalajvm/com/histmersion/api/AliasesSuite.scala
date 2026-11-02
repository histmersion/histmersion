package com.histmersion.api

import com.histmersion.util.identity.AliasKinds

import com.histmersion.values.BundleKind

/** The one alias rule's words (#1799): every kind's rename says the same thing, and an answer to a call that named an
  * old identifier says where it went.
  */
class AliasesSuite extends munit.FunSuite:

  test("the rule names the kind's identifier"):
    assert(
      Aliases.rule(AliasKinds.SourceRecords).startsWith("The old citekey stays an alias"),
      Aliases.rule(AliasKinds.SourceRecords)
    )
    assert(
      Aliases.rule(AliasKinds.GoalRecords).contains("under its new slug"),
      Aliases.rule(AliasKinds.GoalRecords)
    )
    AliasKinds.all.foreach(k => assert(Aliases.rule(k).contains("every write"), k.toString))

  test("followed: a note only when the identifier asked for was an old one"):
    assertEquals(
      Aliases.followed(AliasKinds.EventRecords, "zz_old", "zz_new"),
      Some("_`zz_old` is an old id of this event — it went to `zz_new`._")
    )
    assertEquals(Aliases.followed(AliasKinds.EventRecords, " zz_new ", "zz_new"), None)
    assertEquals(Aliases.note(AliasKinds.GoalRecords, "a", "a")("md"), "md")
    assert(Aliases.note(AliasKinds.GoalRecords, "a", "b")("md").endsWith("\n\nmd"))

  test("every label kind of a bundle that can be renamed has an alias kind"):
    assertEquals(
      List(BundleKind.Article, BundleKind.Event, BundleKind.Goal, BundleKind.Place).map(AliasKinds.of),
      List(
        Some(AliasKinds.ArticleRecords),
        Some(AliasKinds.EventRecords),
        Some(AliasKinds.GoalRecords),
        Some(AliasKinds.PlaceRecords)
      )
    )
    assertEquals(AliasKinds.of(BundleKind.Finding), None)

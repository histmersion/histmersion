package com.histmersion.api

import com.histmersion.values.BundleKind

/** The one alias rule's words (#1799): every kind's rename says the same thing, and an answer to a call that named an
  * old identifier says where it went.
  */
class AliasesSuite extends munit.FunSuite:

  test("the rule names the kind's identifier"):
    assert(
      Aliases.rule(AliasKindsAdapter.SourceRecords).startsWith("The old citekey stays an alias"),
      Aliases.rule(AliasKindsAdapter.SourceRecords)
    )
    assert(
      Aliases.rule(AliasKindsAdapter.GoalRecords).contains("under its new slug"),
      Aliases.rule(AliasKindsAdapter.GoalRecords)
    )
    AliasKindsAdapter.all.foreach(k => assert(Aliases.rule(k).contains("every write"), k.toString))

  test("followed: a note only when the identifier asked for was an old one"):
    assertEquals(
      Aliases.followed(AliasKindsAdapter.EventRecords, "zz_old", "zz_new"),
      Some("_`zz_old` is an old id of this event — it went to `zz_new`._")
    )
    assertEquals(Aliases.followed(AliasKindsAdapter.EventRecords, " zz_new ", "zz_new"), None)
    assertEquals(Aliases.note(AliasKindsAdapter.GoalRecords, "a", "a")("md"), "md")
    assert(Aliases.note(AliasKindsAdapter.GoalRecords, "a", "b")("md").endsWith("\n\nmd"))

  test("every label kind of a bundle that can be renamed has an alias kind"):
    assertEquals(
      List(BundleKind.Article, BundleKind.Event, BundleKind.Goal, BundleKind.Place).map(AliasKindsAdapter.of),
      List(
        Some(AliasKindsAdapter.ArticleRecords),
        Some(AliasKindsAdapter.EventRecords),
        Some(AliasKindsAdapter.GoalRecords),
        Some(AliasKindsAdapter.PlaceRecords)
      )
    )
    assertEquals(AliasKindsAdapter.of(BundleKind.Finding), None)

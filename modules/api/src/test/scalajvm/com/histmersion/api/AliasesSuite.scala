package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.aliases.Aliases
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

/** The one alias rule's words (#1799): every kind's rename says the same thing, and an answer to a call that named an
  * old identifier says where it went.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AliasesSuite extends munit.FunSuite:

  test("the rule names the kind's identifier"):
    assert(
      Aliases.rule(AliasKinds.SourceRecords).value.startsWith("The old citekey stays an alias"),
      Aliases.rule(AliasKinds.SourceRecords)
    )
    assert(
      Aliases.rule(AliasKinds.GoalRecords).value.contains("under its new slug"),
      Aliases.rule(AliasKinds.GoalRecords)
    )
    AliasKinds.all.foreach(k => assert(Aliases.rule(k).value.contains("every write"), k.toString))

  test("followed: a note only when the identifier asked for was an old one"):
    assertEquals(
      Aliases.followed(AliasKinds.EventRecords, Aliases.Id("zz_old"), Aliases.Id("zz_new")),
      Some("_`zz_old` is an old id of this event — it went to `zz_new`._")
    )
    assertEquals(Aliases.followed(AliasKinds.EventRecords, Aliases.Id(" zz_new "), Aliases.Id("zz_new")), None)
    assertEquals(Aliases.note(AliasKinds.GoalRecords, Aliases.Id("a"), Aliases.Id("a"))(Aliases.Note("md")), "md")
    assert(
      Aliases
        .note(AliasKinds.GoalRecords, Aliases.Id("a"), Aliases.Id("b"))(Aliases.Note("md"))
        .value
        .endsWith("\n\nmd")
    )

  test("every label kind of a bundle that can be renamed has an alias kind"):
    assertEquals(
      List(Bundle.Kind.Article, Bundle.Kind.Event, Bundle.Kind.Goal, Bundle.Kind.Place).map(AliasKinds.of),
      List(
        Some(AliasKinds.ArticleRecords),
        Some(AliasKinds.EventRecords),
        Some(AliasKinds.GoalRecords),
        Some(AliasKinds.PlaceRecords)
      )
    )
    assertEquals(AliasKinds.of(Bundle.Kind.Finding), None)

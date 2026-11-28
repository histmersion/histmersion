package com.histmersion
package api

import com.histmersion.api.aliases.AliasKind

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

/** The one alias rule's words (#1799): every kind's rename says the same thing, and an answer to a call that named an
  * old identifier says where it went.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AliasesSuite extends munit.FunSuite:

  test("the rule names the kind's identifier"):
    assert(
      AliasKinds.SourceRecords.rule.value.startsWith("The old citekey stays an alias"),
      AliasKinds.SourceRecords.rule
    )
    assert(
      AliasKinds.GoalRecords.rule.value.contains("under its new slug"),
      AliasKinds.GoalRecords.rule
    )
    AliasKinds.all.foreach(k => assert(k.rule.value.contains("every write"), k.toString))

  test("followed: a note only when the identifier asked for was an old one"):
    assertEquals(
      AliasKinds.EventRecords.followed(AliasKind.Id("zz_old"), AliasKind.Id("zz_new")),
      Some("_`zz_old` is an old id of this event — it went to `zz_new`._")
    )
    assertEquals(AliasKinds.EventRecords.followed(AliasKind.Id(" zz_new "), AliasKind.Id("zz_new")), None)
    assertEquals(AliasKinds.GoalRecords.note(AliasKind.Id("a"), AliasKind.Id("a"))(AliasKind.Note("md")), "md")
    assert(
      AliasKinds.GoalRecords
        .note(AliasKind.Id("a"), AliasKind.Id("b"))(AliasKind.Note("md"))
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

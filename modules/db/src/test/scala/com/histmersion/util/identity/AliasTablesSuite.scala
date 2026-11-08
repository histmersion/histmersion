package com.histmersion
package util.identity

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.automation.goals.GoalAliasTable
import com.histmersion.domain.catalogue.events.EventAliasTable
import com.histmersion.domain.catalogue.places.PlaceAliasTable
import com.histmersion.domain.library.sources.SourceAliasTable
import com.histmersion.domain.research.articles.ArticleAliasTable
import com.histmersion.api.aliases.AliasKinds

class AliasTablesSuite extends munit.FunSuite:

  test("each renamed kind has one alias table, found by its kind"):
    val all = AliasTables(
      List(
        SourceAliasTable.table,
        ArticleAliasTable.table,
        EventAliasTable.table,
        GoalAliasTable.table,
        PlaceAliasTable.table
      )
    )
    assertEquals(all.tables.map(_.kind), AliasKinds.all)
    assertEquals(all.get(AliasKinds.GoalRecords).map(_.aliasTable), Some("goal_aliases"))
    assertEquals(all.tables.map(_.kind).distinct.size, all.tables.size)

  test("an identifier of a kind nobody registered is answered as it is, trimmed"):
    val none = AliasTables(Nil)
    assertEquals(none.current(null, AliasKinds.SourceRecords, AliasTable.Id(" ck ")), "ck")
    assertEquals(none.renamedTo(null, AliasKinds.SourceRecords, AliasTable.Id("ck")), None)

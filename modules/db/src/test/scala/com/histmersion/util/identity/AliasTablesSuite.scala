package com.histmersion.util.identity

import com.histmersion.api.AliasKindsAdapter

class AliasTablesSuite extends munit.FunSuite:

  test("each renamed kind has one alias table, found by its kind"):
    val all = AliasTablesAdapter.all
    assertEquals(all.tables.map(_.kind), AliasKindsAdapter.all)
    assertEquals(all.get(AliasKindsAdapter.GoalRecords).map(_.aliasTable), Some("goal_aliases"))
    assertEquals(all.tables.map(_.kind).distinct.size, all.tables.size)

  test("an identifier of a kind nobody registered is answered as it is, trimmed"):
    val none = AliasTables(Nil)
    assertEquals(none.current(null, AliasKindsAdapter.SourceRecords, " ck "), "ck")
    assertEquals(none.renamedTo(null, AliasKindsAdapter.SourceRecords, "ck"), None)

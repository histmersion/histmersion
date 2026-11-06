package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.ItemStatus
import com.histmersion.api.automation.Managers.ItemCounts

class ItemCountsSuite extends munit.FunSuite:

  test("item counts sum the status rows, a status repeated adding up; a status that is no item status is dropped"):
    val c = ItemCounts.of(
      List(
        ItemStatus.Wire("pending") -> ItemCounts.Count(2),
        ItemStatus.Wire("held") -> ItemCounts.Count(1),
        ItemStatus.Wire("active") -> ItemCounts.Count(3),
        ItemStatus.Wire("done") -> ItemCounts.Count(40),
        ItemStatus.Wire("stopped") -> ItemCounts.Count(5),
        ItemStatus.Wire("failed") -> ItemCounts.Count(6),
        ItemStatus.Wire("cancelled") -> ItemCounts.Count(7),
        ItemStatus.Wire("pending") -> ItemCounts.Count(10),
        ItemStatus.Wire("weird") -> ItemCounts.Count(9)
      )
    )
    assertEquals(c, ItemCounts(com.histmersion.api.automation.Managers.ItemCounts.Pending(12), com.histmersion.api.automation.Managers.ItemCounts.Held(1), com.histmersion.api.automation.Managers.ItemCounts.Active(3), com.histmersion.api.automation.Managers.ItemCounts.Done(40), com.histmersion.api.automation.Managers.ItemCounts.Stopped(5), com.histmersion.api.automation.Managers.ItemCounts.Failed(6), com.histmersion.api.automation.Managers.ItemCounts.Cancelled(7)))
    assertEquals(c.open, 16)
    assertEquals(ItemCounts.of(Nil), ItemCounts())

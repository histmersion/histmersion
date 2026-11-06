package com.histmersion.api

import com.histmersion.api.automation.Managers.ItemCounts

class ItemCountsSuite extends munit.FunSuite:

  test("item counts sum the status rows, a status repeated adding up; a status that is no item status is dropped"):
    val c = ItemCounts.of(
      List(
        "pending" -> 2,
        "held" -> 1,
        "active" -> 3,
        "done" -> 40,
        "stopped" -> 5,
        "failed" -> 6,
        "cancelled" -> 7,
        "pending" -> 10,
        "weird" -> 9
      )
    )
    assertEquals(c, ItemCounts(com.histmersion.api.automation.Managers.ItemCounts.Pending(12), com.histmersion.api.automation.Managers.ItemCounts.Held(1), com.histmersion.api.automation.Managers.ItemCounts.Active(3), com.histmersion.api.automation.Managers.ItemCounts.Done(40), com.histmersion.api.automation.Managers.ItemCounts.Stopped(5), com.histmersion.api.automation.Managers.ItemCounts.Failed(6), com.histmersion.api.automation.Managers.ItemCounts.Cancelled(7)))
    assertEquals(c.open, 16)
    assertEquals(ItemCounts.of(Nil), ItemCounts())

package com.histmersion
package util.data.maintenance

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.data.DataOwner
import com.histmersion.server.Deps
import com.histmersion.util.storage.Location
import com.histmersion.lint.{tolerated, LintRule}

/** What today's owners register for backup, maintenance and the storage figures. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MaintenanceRegistrySuite extends munit.FunSuite:

  test("the reindex is the library's, found by name; nothing else is"):
    assertEquals(Deps.data.task(MaintenanceTask.Name("reindex")).map(_.owner), Some(DataOwner("library")))
    assertEquals(Deps.data.task(MaintenanceTask.Name("prune")), None)
    assertEquals(Deps.data.maintenance.map(_.name.value: String), List("reindex"))

  test("each store and each cache has one owner; a restore clears only the map's tiles"):
    val stores = Deps.data.stores.map(_._2)
    assertEquals(stores.distinct, stores)
    assertEquals(Deps.data.stores.collect { case (o, Location.Backups) => o }, List(DataOwner("util.data")))
    val names = Deps.data.caches.map(_.name)
    assertEquals(names.distinct, names)
    assertEquals(
      Deps.data.caches.filter(_.staleAfter.contains(CacheEvent.Restored)).map(_.name.value: String),
      List("map-tiles")
    )

  test("a backup takes no store's files, and a restore guards the library's sources alone"):
    assertEquals(Deps.data.backups.flatMap(_._2.stores), Nil)
    assertEquals(
      Deps.data.backups.flatMap((o, b) => b.guarded.map(g => (o, g.table.value: String, g.noun))),
      List((DataOwner("library"), "sources", "source"))
    )

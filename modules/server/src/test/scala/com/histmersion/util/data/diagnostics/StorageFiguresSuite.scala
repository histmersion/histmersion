package com.histmersion
package util.data.diagnostics

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.server.Deps
import com.histmersion.util.storage.{ByteSource, Location, MeasureBudget, Storage}
import com.histmersion.util.storage.Store

/** About › Storage over stores in memory: an entry per registered store (the install's only when named), in About's
  * order, the base map's files and its registered caches in its detail.
  */
class StorageFiguresSuite extends munit.FunSuite:

  private val figures = StorageFigures(Deps.data)
  private val cfg = AppConfig.load()

  test("an entry for every registered store, in About's order; the install's places only when given"):
    val storage = Storage.InMemory()
    assertEquals(
      figures.spots(storage, Nil).map(_.key),
      List(
        "database-cluster",
        "backups",
        "originals",
        "acquire-spool",
        "corpus",
        "bundles",
        "gazetteer",
        "gazetteer-data",
        "gazetteer-lpf",
        "basemap",
        "agents",
        "codex-home"
      )
    )
    assertEquals(
      figures
        .spots(storage, List(Location.Logs, Location.Install))
        .map(_.key)
        .filter(k => k == ShownStore.Key("logs") || k == ShownStore.Key("install")),
      List("install", "logs")
    )

  test("the base map's detail: its layer files, an unfinished download and the caches registered in its store"):
    val storage = Storage.InMemory()
    val basemap = storage.store(Location.Basemap)
    basemap.put(Store.Key.applyUnsafe("vector.pmtiles"), ByteSource.Content(Array.fill[Byte](2000)(1))): Unit
    basemap.put(Store.Key.applyUnsafe(".terrain.part/0001"), ByteSource.Content(Array.fill[Byte](1000)(1))): Unit
    basemap.put(Store.Key.applyUnsafe("vector-cache/tiles/3/0/0.mvt"), ByteSource.Content(Array.fill[Byte](3000)(1))): Unit
    val r = figures.measure(storage, Nil, cfg, None, Right(Nil), MeasureBudget.unlimited)
    val entry = r.entries.find(_.key == com.histmersion.api.maintenance.Storage.Entry.Key("basemap")).getOrElse(fail(r.entries.map(_.key).toString))
    assertEquals(entry.detail, Some("vector 2.0 kB · terrain download unfinished 1.0 kB · map cache 3.0 kB"))
    assertEquals((entry.bytes, entry.files), (Some(6000L), Some(3L)))
    assertEquals(
      r.entries.find(_.key == com.histmersion.api.maintenance.Storage.Entry.Key("backups")).map(_.shrink),
      Some(com.histmersion.api.maintenance.Storage.Advice.shrink(com.histmersion.api.maintenance.Storage.Entry.Key("backups"), com.histmersion.api.maintenance.Storage.Advice.Keep(cfg.backups.keep.value)))
    )
    assertEquals(r.problems, Nil)

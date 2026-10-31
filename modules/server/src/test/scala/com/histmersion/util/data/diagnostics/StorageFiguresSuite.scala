package com.histmersion.util.data.diagnostics

import com.histmersion.config.AppConfig
import com.histmersion.server.Deps
import com.histmersion.util.storage.{Location, MeasureBudget, Storage, StoreKey}

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
        "database-cluster", "backups", "originals", "acquire-spool", "corpus", "bundles", "gazetteer",
        "gazetteer-data", "gazetteer-lpf", "basemap", "agents", "codex-home"
      )
    )
    assertEquals(
      figures.spots(storage, List(Location.Logs, Location.Install)).map(_.key).filter(k => k == "logs" || k == "install"),
      List("install", "logs")
    )

  test("the base map's detail: its layer files, an unfinished download and the caches registered in its store"):
    val storage = Storage.InMemory()
    val basemap = storage.store(Location.Basemap)
    basemap.put(StoreKey.applyUnsafe("vector.pmtiles"), Array.fill[Byte](2000)(1)): Unit
    basemap.put(StoreKey.applyUnsafe(".terrain.part/0001"), Array.fill[Byte](1000)(1)): Unit
    basemap.put(StoreKey.applyUnsafe("vector-cache/tiles/3/0/0.mvt"), Array.fill[Byte](3000)(1)): Unit
    val r = figures.measure(storage, Nil, cfg, None, Right(Nil), MeasureBudget.unlimited)
    val entry = r.entries.find(_.key == "basemap").getOrElse(fail(r.entries.map(_.key).toString))
    assertEquals(entry.detail, Some("vector 2.0 kB · terrain download unfinished 1.0 kB · map cache 3.0 kB"))
    assertEquals((entry.bytes, entry.files), (Some(6000L), Some(3L)))
    assertEquals(r.entries.find(_.key == "backups").map(_.shrink), Some(com.histmersion.api.StorageAdvice.shrink("backups", cfg.backups.keep)))
    assertEquals(r.problems, Nil)

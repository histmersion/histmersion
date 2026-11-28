package com.histmersion
package server

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.data.diagnostics.{StorageCache, StorageFigures}
import com.histmersion.util.storage.{Location, MeasureBudget}

import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import com.histmersion.api.backup.BackupsUsage
import com.histmersion.api.maintenance.Storage
import com.histmersion.config.RuntimeConfig
import com.histmersion.config.BackupsConfig
import com.histmersion.config.ConverterConfig
import com.histmersion.config.BundlesConfig
import com.histmersion.config.AcquireConfig
import com.histmersion.config.CorpusConfig
import com.histmersion.config.BasemapConfig
import com.histmersion.config.PlaceSourcesConfig
import com.histmersion.config.AgentRuntimesConfig
import com.histmersion.config.UserPaths
import com.histmersion.util.data.diagnostics.ShownStore
import com.histmersion.api.ByteCount
import com.histmersion.lint.{tolerated, LintRule}

/** The Storage part of About: the locations come from the config (the same resolvers the writers use), are measured
  * within the caps, and the cache measures once per TTL, in the background. All of it in a temp directory: no live data
  * directory, cache or database is read.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class StorageProbeSuite extends munit.FunSuite:

  private val figures = StorageFigures(Deps.data)

  private val tmp = FunFixture[Path](
    _ => Files.createTempDirectory("histmersion-storage"),
    d => {
      val s = Files.walk(d);
      try
        s.sorted(Comparator.reverseOrder()).forEach { p =>
          val _ = Files.deleteIfExists(p)
        }
      finally s.close()
    }
  )

  private def file(p: Path, bytes: Int): Path =
    Files.createDirectories(p.getParent)
    Files.write(p, Array.fill[Byte](bytes)(1))

  /** A config whose every directory is under `d` (the defaults left empty resolve under `d` too). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def config(d: Path, bundled: Boolean = false): AppConfig =
    val base = AppConfig.load()
    base
      .modify(_.runtime)
      .setTo(
        base.runtime
          .modify(_.enabled)
          .setTo(RuntimeConfig.Enabled(bundled))
          .modify(_.home)
          .setTo(RuntimeConfig.Home(d.resolve("install").toString))
          .modify(_.dataDir)
          .setTo(RuntimeConfig.DataDir(d.resolve("data").toString))
      )
      .modify(_.backups)
      .setTo(
        base.backups
          .modify(_.dir)
          .setTo(BackupsConfig.Dir(d.resolve("data/backups").toString))
          .modify(_.keep)
          .setTo(BackupsConfig.Keep(3))
      )
      .modify(_.converter)
      .setTo(base.converter.modify(_.spoolDir).setTo(ConverterConfig.SpoolDir(d.resolve("data/uploads").toString)))
      .modify(_.bundles)
      .setTo(base.bundles.modify(_.dir).setTo(BundlesConfig.Dir(d.resolve("data/bundles").toString)))
      .modify(_.acquire)
      .setTo(base.acquire.modify(_.spoolDir).setTo(AcquireConfig.SpoolDir("")))
      .modify(_.corpus)
      .setTo(base.corpus.modify(_.dir).setTo(CorpusConfig.Dir("")))
      .modify(_.basemap)
      .setTo(base.basemap.modify(_.dir).setTo(BasemapConfig.Dir("")))
      .modify(_.places)
      .setTo(
        base.places
          .modify(_.sources)
          .setTo(
            base.places.sources
              .modify(_.dir)
              .setTo(PlaceSourcesConfig.Dir(""))
              .modify(_.lpfDir)
              .setTo(PlaceSourcesConfig.LpfDir(""))
          )
      )
      .modify(_.agents)
      .setTo(
        base.agents.modify(_.runtimes).setTo(base.agents.runtimes.modify(_.dir).setTo(AgentRuntimesConfig.Dir("")))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def env(d: Path): UserPaths.Lookup =
    Map(
      Environment.VariablesKey("HOME") -> Environment.VariablesValue(d.resolve("home").toString),
      Environment.VariablesKey("XDG_CACHE_HOME") -> Environment.VariablesValue(d.resolve("cache").toString)
    ).get

  tmp.test("every location comes from the config: data dir, cache dir, backups, originals, bundle"): d =>
    val cfg = config(d, bundled = true)
    val p = StorageLocations.measured(cfg, env(d), UserPaths.OsName("Mac OS X"), None)
    assertEquals(
      p.find(Location.Install).map(s => Path.of(s.where().value)),
      Some(d.resolve("install").toAbsolutePath.normalize)
    )
    val locs = figures
      .spots(p, StorageLocations.install)
      .map(l => l.key -> l.path.map(x => d.relativize(Path.of(x.value)).toString))
      .toMap
    assertEquals(locs(ShownStore.Key("database-cluster")), Some("data/pg"))
    assertEquals(locs(ShownStore.Key("backups")), Some("data/backups"))
    assertEquals(locs(ShownStore.Key("originals")), Some("data/uploads"))
    assertEquals(locs(ShownStore.Key("bundles")), Some("data/bundles"))
    assertEquals(locs(ShownStore.Key("gazetteer")), Some("cache/histmersion/gazetteer")) // XDG cache (#1403)
    assertEquals(locs(ShownStore.Key("gazetteer-data")), Some("data/gazetteer"))
    assertEquals(locs(ShownStore.Key("gazetteer-lpf")), Some("data/gazetteer/lpf"))
    assertEquals(locs(ShownStore.Key("basemap")), Some("cache/histmersion/basemap"))
    assertEquals(locs(ShownStore.Key("install")), Some("install"))
    assertEquals(locs(ShownStore.Key("converter")), Some("install/converter"))
    assertEquals(locs(ShownStore.Key("relink-kit")), Some("install/kit"))
    assertEquals(locs(ShownStore.Key("toolchain")), Some("data/toolchain"))
    assertEquals(locs(ShownStore.Key("plugins")), Some("data/plugins"))
    assertEquals(locs(ShownStore.Key("agents")), Some("data/agents"))
    assertEquals(locs(ShownStore.Key("logs")), Some("data/logs"))
    assert(
      !locs.contains(ShownStore.Key("acquire-spool")) && !locs.contains(ShownStore.Key("corpus")),
      locs
    ) // not configured: nothing of ours
    // not bundled: no cluster, no install
    val dev =
      figures
        .spots(StorageLocations.measured(config(d), env(d), UserPaths.OsName("Linux"), None), StorageLocations.install)
        .map(_.key.value)
    assert(!dev.contains("database-cluster") && !dev.contains("install") && !dev.contains("relink-kit"), dev)

  tmp.test("the leftovers: other cache folders are listed one by one, the rest of the data dir together"): d =>
    file(d.resolve("cache/histmersion/thumbs/a.png"), 10)
    file(d.resolve("cache/histmersion/basemap/vector.pmtiles"), 10)
    file(d.resolve("data/signing/gpg-key"), 5)
    file(d.resolve("data/relink.conf"), 5)
    file(d.resolve("data/logs/server.log"), 5)
    val locs = figures.spots(
      StorageLocations.measured(config(d), env(d), UserPaths.OsName("Linux"), None),
      StorageLocations.install
    )
    assertEquals(locs.filter(_.key.value.startsWith("cache:")).map(_.key), List("cache:thumbs"))
    val rest = locs.find(_.key.value == "data-other").getOrElse(fail(s"no data-other: $locs"))
    assertEquals(rest.stores.map(s => Path.of(s.where().value).getFileName.toString), List("relink.conf", "signing"))
    assertEquals(rest.shown, None)

  tmp.test("measure: sizes, nesting, base-map files, backups usage, advice, volumes — and no database without one"):
    d =>
      file(d.resolve("data/backups/histmersion-20261001T100000Z.dump"), 3000)
      file(d.resolve("data/backups/histmersion-20261002T100000Z.dump"), 4000)
      file(d.resolve("data/pg/base/1/123"), 7000)
      file(d.resolve("install/kit/lib/a.jar"), 500)
      file(d.resolve("install/bin/histmersion-server"), 1500)
      file(d.resolve("cache/histmersion/basemap/vector.pmtiles"), 2_000_000)
      file(d.resolve("cache/histmersion/basemap/.terrain.part/0001"), 1000)
      // the tiles read on demand: the map outside the region (#3760)
      file(d.resolve("cache/histmersion/basemap/vector-cache/tiles/3/0/0.mvt"), 3000)
      val cfg = config(d, bundled = true)
      val p = StorageLocations.measured(cfg, env(d), UserPaths.OsName("Linux"), None)
      val bs = BackupFixtures.service(cfg)
      val r =
        figures.measure(
          p,
          StorageLocations.install,
          cfg,
          None,
          bs.list.left.map(_.problems.sentences.mkString("; ")),
          MeasureBudget.unlimited
        )
      def entry(k: String) = r.entries.find(_.key.value == k).getOrElse(fail(s"no $k: ${r.entries.map(_.key)}"))
      assertEquals(entry("backups").bytes, Some(7000L))
      assertEquals(
        entry("backups").shrink.map(_.value: String),
        Storage.Advice.shrink(Storage.Entry.Key("backups"), Storage.Advice.Keep(3)).map(_.value: String)
      )
      assertEquals(
        r.backups,
        Some(
          BackupsUsage(
            BackupsUsage.Count(2),
            ByteCount(7000L),
            Some(BackupsUsage.Newest("histmersion-20261002T100000Z.dump")),
            BackupsUsage.Keep(3)
          )
        )
      )
      assertEquals(entry("database-cluster").bytes, Some(7000L))
      assertEquals(entry("install").bytes, Some(2000L))
      assertEquals(entry("relink-kit").within, Some("install"))
      assertEquals(entry("toolchain").exists, false)
      assertEquals(entry("basemap").bytes, Some(2_004_000L))
      assertEquals(
        entry("basemap").detail,
        Some("vector 2.0 MB · terrain download unfinished 1.0 kB · map cache 3.0 kB")
      )
      assert(!r.entries.exists(_.key.value == "database"), r.entries)
      assert(r.volumes.nonEmpty && r.entries.filter(_.exists.exists).forall(_.volume.isDefined), r)
      assertEquals(Storage.total(r.entries), 7000L + 7000L + 2000L + 2_004_000L)
      assert(r.measuredAt.isDefined && r.tookMillis.isDefined)
      assertEquals(r.problems, Nil)

  tmp.test("measure: a budget that runs out says so, and marks what it stopped"): d =>
    (1 to 30).foreach(i => file(d.resolve(s"data/logs/l$i.log"), 10))
    val cfg = config(d)
    val r = figures.measure(
      StorageLocations.measured(cfg, env(d), UserPaths.OsName("Linux"), None),
      StorageLocations.install,
      cfg,
      None,
      Right(Nil),
      MeasureBudget(MeasureBudget.MaxEntries(5), MeasureBudget.DeadlineNanos(Long.MaxValue))
    )
    assert(r.entries.exists(_.partial.partial), r.entries)
    assert(r.problems.exists(_.value.contains("storage.max-entries")), r.problems)

  // ── the cache ────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def report(n: Int) = Storage.Report(measuredAt = Some(Storage.Report.MeasuredAt(s"m$n")))

  test("the cache: the first request waits for the first measurement; then it is reused until the TTL"):
    val runs = AtomicInteger(0)
    var now = 0L
    val c = StorageCache(
      () => () => report(runs.incrementAndGet()),
      () => StorageCache.TimeToLive(60000L),
      () => StorageCache.Instant(now),
      r => r()
    )
    assertEquals(c.get(StorageCache.WaitMillis(1000)).measuredAt, Some("m1"))
    now = 59999
    assertEquals(c.get(StorageCache.WaitMillis(1000)).measuredAt, Some("m1"))
    assertEquals(runs.get, 1)
    // stale: answered at once, marked measuring, while the next runs (inline here, so it is done after)
    now = 60001
    val stale = c.get(StorageCache.WaitMillis(1000))
    assertEquals((stale.measuredAt, stale.measuring), (Some("m1"), true))
    assertEquals(c.get(StorageCache.WaitMillis(1000)).measuredAt, Some("m2"))

  test("the cache: a request that cannot wait says measuring; many requests start ONE measurement"):
    val runs = AtomicInteger(0)
    val pending = scala.collection.mutable.ListBuffer.empty[() => Unit]
    val c = StorageCache(
      () => () => report(runs.incrementAndGet()),
      () => StorageCache.TimeToLive(60000L),
      () => StorageCache.Instant(0L),
      r => pending += r
    )
    assertEquals(c.get(StorageCache.WaitMillis(0)), Storage.Report(measuring = Storage.Report.Measuring.Measuring))
    assertEquals(c.get(StorageCache.WaitMillis(5)), Storage.Report(measuring = Storage.Report.Measuring.Measuring))
    assertEquals(pending.size, 1)
    pending.head()
    assertEquals(c.get(StorageCache.WaitMillis(0)).measuredAt, Some("m1"))
    assertEquals(runs.get, 1)

  test("the cache: a failed measurement is a problem, and the next request tries again"):
    var fail = true
    val c = StorageCache(
      () => () => if fail then throw RuntimeException("disk gone") else report(1),
      () => StorageCache.TimeToLive(60000L),
      () => StorageCache.Instant(0L),
      r => r()
    )
    assertEquals(c.get(StorageCache.WaitMillis(100)).problems, List("storage: disk gone"))
    fail = false
    assertEquals(c.get(StorageCache.WaitMillis(100)).measuredAt, Some("m1"))

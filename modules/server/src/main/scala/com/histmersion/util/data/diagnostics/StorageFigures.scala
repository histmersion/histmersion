package com.histmersion.util.data.diagnostics

import com.histmersion.api.{Plural, TableSize}
import com.histmersion.api.backup.{Backup, BackupsUsage}
import com.histmersion.api.maintenance.Storage as StorageReports
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataContributions
import com.histmersion.util.data.maintenance.{CacheHolding, CacheSpec}
import com.histmersion.util.storage.{Location, MeasureBudget, Storage, Store}

import java.time.Instant
import com.histmersion.api.basemap.Basemap

/** The Storage part of About, from the registry: an entry for every store an owner registered and every place of the
  * install, where `storage` puts them, each measured with a bounded walk (`MeasureBudget`); the database's size and its
  * largest registered tables; the backups' usage. The leftovers of the data and cache directories — what no entry
  * covers — are listed too. [[StorageCache]] runs it in the background, so About itself never walks a disk.
  */
final class StorageFigures(registry: DataContributions):

  /** Every entry, in the order About lists them: the shown stores, the cache directory's other folders one by one, then
    * the rest of the data directory together. Reads the disk only to find those leftovers.
    */
  def spots(storage: Storage, installed: List[Location]): List[StorageSpot] =
    val included = (registry.stores.map(_._2) ++ installed).toSet
    val named = ShownStore.values.toList
      .filter(s => included(s.location))
      .flatMap(s =>
        storage.find(s.location).map(store => StorageSpot(s.key, s.label, s.group, List(store), Some(store)))
      )
    val shown = named.flatMap(_.path)
    def covered(child: com.histmersion.util.data.diagnostics.StorageSpot.Key) =
      shown.exists(s => StorageReports.under(s, child) || StorageReports.under(child, s))
    def children(location: Location): List[(com.histmersion.util.data.diagnostics.StorageSpot.Key, Store)] =
      storage.find(location).toList.flatMap { dir =>
        dir
          .names()
          .flatMap(n => Store.Key.of(n).toOption.map(k => n -> dir.sub(k)))
          .filterNot((_, s) => covered(s.where()))
      }
    val caches = children(Location.CacheDirectory).map((n, s) =>
      StorageSpot(s"cache:$n", s"cache: $n", "caches", List(s), Some(s))
    )
    val rest = storage
      .find(Location.DataDirectory)
      .toList
      .flatMap(data =>
        children(Location.DataDirectory) match
          case Nil  => Nil
          case some =>
            val names = some.map(_._1)
            List(
              StorageSpot(
                "data-other",
                "other files in the data directory",
                "other",
                some.map(_._2),
                None,
                detail = Some(
                  s"${data.where()}: ${names.take(8).mkString(", ")}${
                      if names.size > 8 then s" and ${names.size - 8} more" else ""
                    }"
                )
              )
            )
      )
    named ++ caches ++ rest

  /** The base-map files, one by one, an unfinished download (`.<layer>.part`), and the registered caches it holds. */
  private def basemapDetail(store: Store, budget: MeasureBudget): Option[com.histmersion.util.data.diagnostics.StorageSpot.Detail] =
    val parts = Basemap.Layer.values.toList.flatMap { l =>
      val file = Store.Key.applyUnsafe(l.file)
      val part = Store.Key.applyUnsafe(s".${l.wire}.part")
      Option.when(store.isFile(file))(
        s"${l.wire} ${Backup.humanBytes(scala.util.Try(store.get(file).fold(0L)(_.size.value)).getOrElse(0L))}"
      ) ++
        Option.when(store.isDirectory(part))(
          s"${l.wire} download unfinished ${Backup.humanBytes(store.sub(part).measure(budget).bytes.value)}"
        )
    }
    val caches = registry.caches.collect {
      case CacheSpec(_, _, label, CacheHolding.InStore(Location.Basemap, key), _) if store.isDirectory(key) =>
        s"$label ${Backup.humanBytes(store.sub(key).measure(budget).bytes.value)}"
    }
    Option.when(parts.nonEmpty || caches.nonEmpty)((parts ++ caches).mkString(" · "))

  /** Measure every entry within `budget`; the database through `database` when there is one. */
  def measure(
      storage: Storage,
      installed: List[Location],
      settings: AppConfig,
      database: Option[DiagnosticsService],
      backups: => Either[String, List[Backup.Info]],
      budget: MeasureBudget,
      now: () => Instant = () => Instant.now()
  ): com.histmersion.api.maintenance.Storage.Report =
    val started = System.nanoTime()
    val spotted = spots(storage, installed)
    val problems = List.newBuilder[String]
    val measured = spotted.map { spot =>
      val results = spot.stores.map(_.measure(budget))
      val exists = results.exists(_.exists)
      val unread = results.map(_.unreadable).sum
      if unread > 0 then problems += s"${spot.label}: ${Plural.count(unread, "entry", "entries")} could not be read"
      val extra = if spot.key == ShownStore.Basemap.key then spot.shown.flatMap(basemapDetail(_, budget)) else None
      com.histmersion.api.maintenance.Storage.Entry(
        key = spot.key,
        label = spot.label,
        group = spot.group,
        path = spot.path,
        bytes = (Option.when(exists)(results.map(_.bytes.value).sum)).map(com.histmersion.api.ByteCount.apply),
        files = (Option.when(exists)(results.map(_.files).sum)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
        exists = exists,
        partial = results.exists(_.partial),
        detail = (spot.detail.toList ++ extra.toList).reduceOption(_ + " · " + _),
        shrink = com.histmersion.api.maintenance.Storage.Advice.shrink(spot.key, settings.backups.keep),
        volume = spot.stores.headOption.flatMap(_.volume).map(_.id)
      )
    }
    val volumes = spotted
      .flatMap(_.stores.headOption)
      .flatMap(_.volume)
      .groupBy(_.id)
      .values
      .map(_.head)
      .toList
      .sortBy(_.id)
      .map(v => com.histmersion.api.maintenance.Storage.Volume(v.id, com.histmersion.api.ByteCount(v.total.value), com.histmersion.api.ByteCount(v.usable.value)))
    val (dbEntry, tables) =
      database.fold((Option.empty[com.histmersion.api.maintenance.Storage.Entry], List.empty[TableSize])) { d =>
        val size = scala.util.Try(d.databaseSize).toEither.left.map(e => s"database size: ${e.getMessage}")
        val top = scala.util
          .Try(d.largestTables(settings.storage.topTables))
          .toEither
          .left
          .map(e => s"largest tables: ${e.getMessage}")
        size.left.foreach(problems += _)
        top.left.foreach(problems += _)
        (
          Some(
            com.histmersion.api.maintenance.Storage.Entry(
              "database",
              "database (Postgres)",
              "database",
              bytes = (size.toOption.map(_._2.value)).map(com.histmersion.api.ByteCount.apply),
              exists = size.isRight,
              within = Option.when(measured.exists(e => e.key == ShownStore.DatabaseCluster.key && e.exists))(
                ShownStore.DatabaseCluster.key
              ),
              detail = size.toOption.map(_._1),
              shrink = com.histmersion.api.maintenance.Storage.Advice.shrink("database", settings.backups.keep)
            )
          ),
          top.getOrElse(Nil)
        )
      }
    val listed = backups
    listed.left.foreach(e => problems += s"backups: $e")
    val usage =
      listed.toOption.map(bs =>
        BackupsUsage(bs.size, bs.map(_.bytes).sum, bs.headOption.map(_.file), settings.backups.keep)
      )
    val report = com.histmersion.api.maintenance.Storage.Report(
      measuredAt = Some(now().toString),
      tookMillis = (Some((System.nanoTime() - started) / 1_000_000L)).map(com.histmersion.api.maintenance.Storage.Report.DurationMillis.apply),
      entries = StorageReports.nest(dbEntry.toList ++ measured),
      tables = tables,
      volumes = volumes,
      backups = usage,
      lowFreeBytes = com.histmersion.api.ByteCount(settings.storage.lowFreeBytes),
      problems = problems.result() ++
        Option
          .when(budget.exhausted)(
            s"the measurement stopped at its cap (storage.max-millis ${settings.storage.maxMillis}, " +
              s"storage.max-entries ${settings.storage.maxEntries}): sizes marked \"at least\" are lower bounds"
          )
          .toList
    )
    StorageReports.withHints(report)



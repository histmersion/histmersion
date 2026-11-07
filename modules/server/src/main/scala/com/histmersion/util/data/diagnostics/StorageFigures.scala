package com.histmersion.util.data.diagnostics

import com.histmersion.api.{Plural, TableSize}
import com.histmersion.api.backup.{Backup, BackupsUsage}
import com.histmersion.api.maintenance.Storage as StorageReports
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataContributions
import com.histmersion.util.data.maintenance.{CacheHolding, CacheSpec}
import com.histmersion.util.storage.{Blob, Location, MeasureBudget, Storage, Store}

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
    def under(child: Blob.Location, parent: Blob.Location): Boolean =
      StorageReports.under(StorageReports.Entry.Path(child.value), StorageReports.Entry.Path(parent.value))
    def covered(child: Blob.Location) =
      shown.exists(s => under(s, child) || under(child, s))
    def children(location: Location): List[(Store.EntryName, Store)] =
      storage.find(location).toList.flatMap { dir =>
        dir
          .names()
          .flatMap(n => Store.Key.of(n.value).toOption.map(k => n -> dir.sub(k)))
          .filterNot((_, s) => covered(s.where()))
      }
    val caches = children(Location.CacheDirectory).map((n, s) =>
      StorageSpot(ShownStore.Key(s"cache:$n"), ShownStore.Label(s"cache: $n"), ShownStore.Group("caches"), List(s), Some(s))
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
                ShownStore.Key("data-other"),
                ShownStore.Label("other files in the data directory"),
                ShownStore.Group("other"),
                some.map(_._2),
                None,
                detail = Some(
                  StorageSpot.Detail(
                    s"${data.where()}: ${names.take(8).mkString(", ")}${
                        if names.size > 8 then s" and ${names.size - 8} more" else ""
                      }"
                  )
                )
              )
            )
      )
    named ++ caches ++ rest

  /** The base-map files, one by one, an unfinished download (`.<layer>.part`), and the registered caches it holds. */
  private def basemapDetail(store: Store, budget: MeasureBudget): Option[com.histmersion.util.data.diagnostics.StorageSpot.Detail] =
    val parts = Basemap.Layer.values.toList.flatMap { l =>
      val file = Store.Key.applyUnsafe(l.file.value)
      val part = Store.Key.applyUnsafe(s".${l.wire}.part")
      Option.when(store.isFile(file))(
        s"${l.wire} ${Backup.humanBytes(com.histmersion.api.ByteCount(scala.util.Try(store.get(file).fold(0L)(_.size.value)).getOrElse(0L)))}"
      ) ++
        Option.when(store.isDirectory(part))(
          s"${l.wire} download unfinished ${Backup.humanBytes(com.histmersion.api.ByteCount(store.sub(part).measure(budget).bytes.value))}"
        )
    }
    val caches = registry.caches.collect {
      case CacheSpec(_, _, label, CacheHolding.InStore(Location.Basemap, key), _) if store.isDirectory(key) =>
        s"$label ${Backup.humanBytes(com.histmersion.api.ByteCount(store.sub(key).measure(budget).bytes.value))}"
    }
    Option.when(parts.nonEmpty || caches.nonEmpty)(StorageSpot.Detail((parts ++ caches).mkString(" · ")))

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
      val exists = results.exists(_.exists.value)
      val unread = results.map(_.unreadable.value).sum
      if unread > 0 then problems += s"${spot.label}: ${Plural.count(Plural.CountN(unread.toLong), Plural.CountSingular("entry"), Plural.CountPlural("entries"))} could not be read"
      val extra = if spot.key == ShownStore.Basemap.key then spot.shown.flatMap(basemapDetail(_, budget)) else None
      com.histmersion.api.maintenance.Storage.Entry(
        key = StorageReports.Entry.Key(spot.key.value),
        label = StorageReports.Entry.Label(spot.label.value),
        group = StorageReports.Entry.Group(spot.group.value),
        path = spot.path.map(p => StorageReports.Entry.Path(p.value)),
        bytes = (Option.when(exists)(results.map(_.bytes.value).sum)).map(com.histmersion.api.ByteCount.apply),
        files = (Option.when(exists)(results.map(_.files.value).sum)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
        exists = StorageReports.Entry.Exists(exists),
        partial = StorageReports.Entry.Partial(results.exists(_.partial.value)),
        detail = (spot.detail.toList ++ extra.toList).map(_.value).reduceOption(_ + " · " + _).map(StorageReports.Entry.Detail.apply),
        shrink = StorageReports.Advice
          .shrink(StorageReports.Entry.Key(spot.key.value), StorageReports.Advice.Keep(settings.backups.keep.value))
          .map(h => StorageReports.Entry.Shrink(h.value)),
        volume = spot.stores.headOption.flatMap(_.volume).map(v => StorageReports.Entry.Volume(v.id.value))
      )
    }
    val volumes = spotted
      .flatMap(_.stores.headOption)
      .flatMap(_.volume)
      .groupBy(_.id)
      .values
      .map(_.head)
      .toList
      .sortBy(_.id.value)
      .map(v => com.histmersion.api.maintenance.Storage.Volume(com.histmersion.api.maintenance.Storage.Volume.Id(v.id.value), com.histmersion.api.ByteCount(v.total.value), com.histmersion.api.ByteCount(v.usable.value)))
    val (dbEntry, tables) =
      database.fold((Option.empty[com.histmersion.api.maintenance.Storage.Entry], List.empty[TableSize])) { d =>
        val size = scala.util.Try(d.databaseSize).toEither.left.map(e => s"database size: ${e.getMessage}")
        val top = scala.util
          .Try(d.largestTables(DiagnosticsService.TableLimit(settings.storage.topTables.value)))
          .toEither
          .left
          .map(e => s"largest tables: ${e.getMessage}")
        size.left.foreach(problems += _)
        top.left.foreach(problems += _)
        (
          Some(
            com.histmersion.api.maintenance.Storage.Entry(
              StorageReports.Entry.Key("database"),
              StorageReports.Entry.Label("database (Postgres)"),
              StorageReports.Entry.Group("database"),
              bytes = (size.toOption.map(_._2.value)).map(com.histmersion.api.ByteCount.apply),
              exists = StorageReports.Entry.Exists(size.isRight),
              within = Option.when(measured.exists(e => e.key.value == ShownStore.DatabaseCluster.key.value && e.exists.value))(
                StorageReports.Entry.Key(ShownStore.DatabaseCluster.key.value)
              ),
              detail = size.toOption.map(s => StorageReports.Entry.Detail(s._1.value)),
              shrink = StorageReports.Advice
                .shrink(StorageReports.Entry.Key("database"), StorageReports.Advice.Keep(settings.backups.keep.value))
                .map(h => StorageReports.Entry.Shrink(h.value))
            )
          ),
          top.getOrElse(Nil)
        )
      }
    val listed = backups
    listed.left.foreach(e => problems += s"backups: $e")
    val usage =
      listed.toOption.map(bs =>
        BackupsUsage(
          BackupsUsage.Count(bs.size),
          com.histmersion.api.ByteCount(bs.map(_.bytes.value).sum),
          bs.headOption.map(b => BackupsUsage.Newest(b.file.value)),
          BackupsUsage.Keep(settings.backups.keep.value)
        )
      )
    val report = com.histmersion.api.maintenance.Storage.Report(
      measuredAt = Some(StorageReports.Report.MeasuredAt(now().toString)),
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



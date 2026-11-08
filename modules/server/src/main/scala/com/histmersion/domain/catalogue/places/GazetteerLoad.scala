package com.histmersion
package domain.catalogue.places

import com.histmersion.api.{LpfDataset, Span}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Transactor
import com.histmersion.util.storage.{Blob, Reservation, Store}

import io.circe.{Json, JsonObject}
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import scala.annotation.tailrec
import scala.util.Using
import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.JobInfo
import com.histmersion.domain.catalogue.places.GazetteerImports.Problem
import com.histmersion.server.{GazetteerPaths, HttpDownload}
import com.histmersion.api.catalogue.{Gazetteer, GazRecord}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.catalogue.Year

/** The gazetteer's external sources (#1064) — the `geonames`, `wikidata`, `tgn` (#1403) and `lpf` steps of the one-time
  * gazetteer import job (the init job, the same that imports the bundled seed and fetches Cliopatria or PeriodO).
  *
  *   - `geonames`, `wikidata`, `tgn`: a PINNED extract (`db/gazetteer/extracts.json`: file, version, sha256), Linked
  *     Places Format with one feature per line, gzip. Looked for in the extracts store (`places.sources.dir`); when
  *     missing and its pin names a `url`, downloaded there first. Its sha256 is checked BEFORE anything is read — a
  *     mismatch is refused.
  *   - `lpf`: every `*.json` / `*.jsonld` / `*.geojson` (a FeatureCollection, read whole up to `max-lpf-mb`),
  *     `*.ndjson` / `*.jsonl` (a feature per line, optionally `.gz`) and `*.tsv` (LP-TSV) in the drop folder
  *     (`places.sources.lpf-dir`) — the World Historical Gazetteer downloads a person puts there. Each file is a
  *     dataset (`lpf:<file name>`), its version the file's sha256; its title, citation, licence and time span come from
  *     the file or a `<file>.meta.json` beside it.
  *
  * Each source is read in batches into [[GazetteerImport.Session]] on one connection; `apply = false` is a dry run that
  * rolls every batch back and reports what it would have done. After a real run the source is recorded in
  * `gazetteer_sources` and `finding_places` is re-materialised.
  */
object GazetteerLoad:

  /** Where things are: the extracts' store, the store they lived in before they moved to the cache directory (`<data
    * dir>/gazetteer`, until 2026-10-17: a pinned extract found there and not in `extracts` is moved over once,
    * [[extract]]), the drop folder, and the manifest's text (`manifestAt` names it in messages).
    */

  type LoadApply = LoadApply.T
  object LoadApply extends Newtype[Boolean, Pure]

  final case class Stores(
      extracts: Store,
      legacy: Option[Store],
      lpf: Store,
      manifestAt: Stores.ManifestAt,
      manifestText: () => Either[GazetteerPaths.Problem, GazetteerPaths.ManifestText]
  )

  object Stores:
    type ManifestAt = ManifestAt.T
    object ManifestAt extends Newtype[String, Pure]

  /** Where a pinned file is: in `extracts`, else at the legacy place, else `None`. */
  def locate(p: Stores, file: Gazetteer.SourceInfo.File): Option[(Store, Store.Key)] =
    Store.Key
      .of(file.value)
      .toOption
      .flatMap(k => (p.extracts :: p.legacy.toList).find(_.isFile(k)).map(_ -> k))

  /** Every name `histmersion places load` and `init.fetch-sources` accept for these sources. */
  val names: List[Gazetteer.Kind.Wire] = Gazetteer.Kind.values.toList.map(_.wire)

  // ── the manifest ───────────────────────────────────────────────────────────────────────────────

  def manifest(p: Stores): Either[Problem, Gazetteer.Manifest] =
    p.manifestText()
      .left
      .map(e => Problem(e.value))
      .flatMap(text =>
        io.circe.parser
          .decode[Gazetteer.Manifest](text.value)
          .left
          .map(e => Problem(s"${p.manifestAt}: ${e.getMessage}"))
      )

  private def pin(p: Stores, kind: Gazetteer.Kind): Either[Problem, Gazetteer.Extract] =
    manifest(p).flatMap(
      _.extracts
        .find(_.name.value == kind.wire.value)
        .toRight(Problem(s"${p.manifestAt} pins no '${kind.wire}' extract"))
    )

  // ── checksums and downloads ────────────────────────────────────────────────────────────────────

  /** Is the file at `key` the pinned one? `Left` says why not — a missing file or a checksum mismatch, which is
    * REFUSED: an extract that is not the pinned one is never imported.
    */
  def verify(store: Store, key: Store.Key, x: Gazetteer.Extract): Either[Problem, Blob] =
    store.get(key) match
      case None    => Left(Problem(s"${store.where(key)} is missing"))
      case Some(f) =>
        val got = f.sha256().value
        if got.equalsIgnoreCase(x.sha256.value) then Right(f)
        else
          Left(
            Problem(
              s"${store.where(key)} has sha256 $got, but ${x.name} ${x.version} is pinned at ${x.sha256} — refused " +
                "(rebuild it with db/gazetteer/build.py and re-pin, or fetch the pinned file)"
            )
          )

  /** The download seam: url → the file reserved for it (committed by the caller). A generic User-Agent, never a
    * personal one; the composition root gives the client.
    */
  type Download = (HttpDownload.Url, Reservation, Settings.DownloadTimeoutSeconds) => Either[HttpDownload.Problem, Unit]

  /** Why a pinned extract cannot be read, and what to do: a pin with no checksum is not built yet; a missing file whose
    * pin has no download url (the extracts are not hosted) must be PUT at the exact path the import reads — named here
    * with its checksum and size, and the setting that moves it.
    */
  def missing(p: Stores, x: Gazetteer.Extract): Problem =
    if x.sha256.value.trim.isEmpty || x.file.value.trim.isEmpty then
      Problem(
        s"${x.name} is not pinned yet (${p.manifestAt} names no built file or sha256 for it) — build it with " +
          s"db/gazetteer/build.py and pin it (db/gazetteer/README.md, \"Rebuilding and re-pinning\")"
      )
    else
      val f = Store.Key.of(x.file.value).toOption.fold(p.extracts.where())(p.extracts.where(_))
      Problem(
        s"${x.name} ${x.version}: ${x.file} is not at $f, and its pin has no download url (the extracts are not " +
          s"hosted). Put the file there — sha256 ${x.sha256}, ${Backup.humanBytes(ByteCount(x.bytes.value))} — copied from " +
          s"another install or rebuilt with db/gazetteer/build.py (db/gazetteer/README.md); the folder is " +
          s"places.sources.dir (HISTMERSION_GAZETTEER_DIR; by default the XDG cache directory, " +
          s"$$XDG_CACHE_HOME/histmersion/gazetteer or ~/.cache/histmersion/gazetteer). Then run the load again."
      )

  /** A pinned extract at the legacy place (not in `extracts`), VERIFIED, moved over once and logged — so an install
    * that kept its extracts in `<data dir>/gazetteer` keeps them. When the move fails it is read where it is. Importing
    * is idempotent either way: what was imported stays imported.
    */
  private def adopt(p: Stores, x: Gazetteer.Extract, old: Store, key: Store.Key): Either[Problem, Blob] =
    verify(old, key, x).map { found =>
      val moved =
        for
          _ <- p.extracts.makeDirectory().left.map(_.toString)
          r <- p.extracts.reserve(key).left.map(_.toString)
          _ <- Using(found.open())(in => Using.resource(r.output())(out => in.transferTo(out))).toEither.left
            .map { e => r.discard(); e.getMessage }
          _ <- r.commit().left.map(_.toString)
          f <- p.extracts.get(key).toRight(s"${p.extracts.where(key)} is missing")
        yield f
      moved match
        case Right(f) =>
          val _ = old.delete(key)
          scribe.info(
            s"gazetteer: moved the ${x.name} extract ${old.where(key)} → ${p.extracts.where(key)} (the extracts live in the cache directory now)"
          )
          f
        case Left(e) =>
          scribe.warn(
            s"gazetteer: could not move ${old.where(key)} to ${p.extracts.where(key)} ($e); reading it where it is"
          )
          found
    }

  /** The pinned extract (moved over from the legacy place once), fetched first when it is missing and its pin names a
    * url.
    */
  def extract(
      p: Stores,
      x: Gazetteer.Extract,
      download: Download,
      timeoutSeconds: Settings.DownloadTimeoutSeconds
  ): Either[Problem, Blob] =
    if x.sha256.value.trim.isEmpty || x.file.value.trim.isEmpty then Left(missing(p, x))
    else
      Store.Key.of(x.file.value) match
        case Left(e)                              => Left(Problem(s"${x.file}: $e"))
        case Right(key) if p.extracts.isFile(key) => verify(p.extracts, key, x)
        case Right(key)                           =>
          p.legacy.filter(_.isFile(key)) match
            case Some(old) => adopt(p, x, old, key)
            case None      =>
              x.url.map(_.value.trim).filter(_.nonEmpty) match
                case None      => Left(missing(p, x))
                case Some(url) =>
                  for
                    _ <- p.extracts.makeDirectory().left.map(e => Problem(e.toString))
                    r <- p.extracts.reserve(key).left.map(e => Problem(e.toString))
                    _ <- download(HttpDownload.Url(url), r, timeoutSeconds).left.map { e =>
                      r.discard(); Problem(e.value)
                    }
                    _ <- r.commit().left.map(e => Problem(e.toString))
                    f <- verify(p.extracts, key, x).left.map { e =>
                      val _ = p.extracts.delete(key)
                      e
                    }
                  yield f

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  private def open(f: Blob): InputStream =
    val raw = f.open()
    if f.key.name.value.endsWith(".gz") then new GZIPInputStream(raw, 1 << 16) else raw

  /** Lines of a (gzip) text file, handed to `each` with their 1-based number. */
  private def lines(f: Blob)(each: (String, Int) => Unit): Unit =
    Using.resource(new BufferedReader(new InputStreamReader(open(f), StandardCharsets.UTF_8), 1 << 16)) { r =>
      @tailrec def next(n: Int): Unit =
        val line = r.readLine()
        if line != null then
          if line.trim.nonEmpty then each(line, n)
          next(n + 1)
      next(1)
    }

  /** Feed the records of one file to `sink` in batches; a malformed record is skipped and named. */
  final private class Batcher(size: Int, sink: List[GazRecord] => Gazetteer.LoadCounts):
    private val buf = List.newBuilder[GazRecord] // the batch being filled
    private var n = 0 // its size
    var total: Gazetteer.LoadCounts =
      Gazetteer.LoadCounts(Gazetteer.LoadCounts.Source(""), Gazetteer.LoadCounts.Version("")) // the running sum
    var span: Span = Span.always // the widest known years of the records, for an LPF dataset that states none

    def add(r: Either[Gazetteer.Problem, GazRecord]): Unit = r match
      case Left(e)    => total = total.copy(skipped = (total.skipped :+ Gazetteer.LoadCounts.Skipped(e.value)).take(20))
      case Right(rec) =>
        span = Span(
          (span.from ++ rec.span.from).minByOption(_.value: Long),
          (span.to ++ rec.span.to).maxByOption(_.value: Long)
        )
        buf += rec
        n += 1
        if n >= size then flush()

    def flush(): Unit =
      if n > 0 then
        total = total + sink(buf.result())
        buf.clear()
        n = 0

  // ── a run ──────────────────────────────────────────────────────────────────────────────────────

  /** `rebuild`: re-materialise `finding_places` after a real run that changed something (a test turns it off: a full
    * rebuild touches every other suite's findings).
    */
  final case class Settings(
      stores: Stores,
      batch: Settings.Batch,
      maxLpfBytes: Settings.MaxLpfBytes,
      downloadTimeoutSeconds: Settings.DownloadTimeoutSeconds,
      toleranceKm: GeocodeSettings.ToleranceKm,
      rebuild: Settings.Rebuild = Settings.Rebuild(true),
      openStartWindowYears: Gazetteer.ValidityWindowYears =
        Gazetteer.ValidityWindowYears(Gazetteer.DefaultOpenStartWindowYears.value),
      pointAreaKinds: Set[Gazetteer.PointAreaKinds] = Gazetteer.DefaultPointAreaKinds.toSet
  )

  object Settings:
    type Batch = Batch.T
    object Batch extends Newtype[Int, Pure]
    type MaxLpfBytes = MaxLpfBytes.T
    object MaxLpfBytes extends Newtype[Long, Pure]
    type DownloadTimeoutSeconds = DownloadTimeoutSeconds.T
    object DownloadTimeoutSeconds extends Newtype[Int, Pure]
    type Rebuild = Rebuild.T
    object Rebuild extends Newtype[Boolean, Pure]

  def settings(cfg: AppConfig, stores: Stores): Settings =
    Settings(
      stores,
      Settings.Batch(math.max(1, cfg.places.sources.batch.value)),
      Settings.MaxLpfBytes(cfg.places.sources.maxLpfMb.value.toLong * 1024 * 1024),
      Settings.DownloadTimeoutSeconds(cfg.places.sources.downloadTimeoutSeconds.value),
      GeocodeSettings.ToleranceKm(cfg.places.pointToleranceKm.value),
      openStartWindowYears = Gazetteer.ValidityWindowYears(cfg.places.sources.openStartWindowYears.value),
      pointAreaKinds = cfg.places.sources.pointAreaKindSet.map(k => Gazetteer.PointAreaKinds(k.value))
    )

  /** Import (or, `apply = false`, dry-run) one source kind. `note` reports progress on the job. */
  def load(
      db: Transactor,
      s: Settings,
      kind: Gazetteer.Kind,
      apply: LoadApply,
      note: JobInfo.Progress => Unit,
      download: Download
  ): Either[Problem, List[Gazetteer.LoadCounts]] =
    val result: Either[Problem, List[Gazetteer.LoadCounts]] = kind match
      case Gazetteer.Kind.Lpf => loadLpf(db, s, apply.value, note)
      case k                  =>
        for
          x <- pin(s.stores, k)
          f <- extract(s.stores, x, download, s.downloadTimeoutSeconds)
        yield
          val (c, _) = run(
            db,
            s,
            Gazetteer.sourceKey(k),
            Gazetteer.LoadCounts.Version(x.version.value),
            apply.value,
            note,
            f.key.name.value
          ) { b =>
            lines(f)((line, n) => b.add(parseLine(line, n, Gazetteer.DatasetName(""), None)))
          }
          if apply.value then
            db.read(
              GazetteerRepo.recordSource(
                GazetteerSourceRow.Key(Gazetteer.sourceKey(k).value),
                k,
                GazetteerSourceRow.Title(x.title.value),
                GazetteerSourceRow.Version(x.version.value),
                f.sha256(),
                GazetteerSourceRow.File(x.file.value),
                x.url.map(u => GazetteerSourceRow.Url(u.value)),
                Some(GazetteerSourceRow.Licence(x.licence.value)),
                x.attribution.map(a => GazetteerSourceRow.Citation(a.value)),
                Span.always
              )
            )
          List(c)
    result.map { cs =>
      if apply.value && s.rebuild.value && cs.exists(!_.unchanged) then
        note(JobInfo.Progress("matching findings to places"))
        val _ = db.transact(FindingPlaceRepo.rebuildFindingPlaces(s.toleranceKm))
      cs
    }

  private def parseLine(
      line: String,
      n: Int,
      dataset: Gazetteer.DatasetName,
      source: Option[Gazetteer.SourceInfo.Key]
  ): Either[Gazetteer.Problem, GazRecord] =
    io.circe.parser
      .parse(line)
      .left
      .map(e => Gazetteer.Problem(s"line $n: ${e.getMessage}"))
      .flatMap(Gazetteer.fromFeature(_, dataset, n, source).left.map(e => Gazetteer.Problem(s"line $n: $e")))

  /** One source through one session: batches, then (when every line was read) the sweep. */
  private def run(
      db: Transactor,
      s: Settings,
      key: Gazetteer.SourceInfo.Key,
      version: Gazetteer.LoadCounts.Version,
      apply: Boolean,
      note: JobInfo.Progress => Unit,
      label: String
  )(feed: Batcher => Unit): (Gazetteer.LoadCounts, Span) =
    db.read:
      val session = GazetteerImport.session(key, version, apply, s.openStartWindowYears, s.pointAreaKinds)
      val pruned = session.prune(s.batch.value)
      if pruned.value > 0 then note(JobInfo.Progress(s"$key: $pruned point-only area place(s) removed"))
      var batches = 0 // progress
      val b = Batcher(
        s.batch.value,
        rs => {
          batches += 1
          if batches % 10 == 1 then note(JobInfo.Progress(s"$key: $label, batch $batches"))
          session.batch(rs)
        }
      )
      feed(b)
      b.flush()
      val swept = session.sweep()
      (
        (b.total + swept + Gazetteer.LoadCounts(
          key,
          version,
          placesRemoved = Gazetteer.LoadCounts.PlacesRemoved(pruned.value)
        ))
          .copy(source = key, version = version),
        b.span
      )

  // ── LPF ────────────────────────────────────────────────────────────────────────────────────────

  private val LpfFile = """(?i).+\.(json|jsonld|geojson|ndjson|jsonl|tsv)(\.gz)?$""".r

  /** The data files of the drop folder, by name (a `*.meta.json` sidecar is not one). */
  def lpfFiles(dir: Store): List[Blob] =
    dir
      .list()
      .filter(f =>
        f.isFile && LpfFile.matches(f.key.name.value) && !f.key.name.value.toLowerCase.endsWith(".meta.json")
      )
      .sortBy(_.key.name.value)
      .flatMap(f => dir.get(f.key))

  private def sidecar(dir: Store, f: Blob): Option[JsonObject] =
    val name = f.key.name.value.replaceAll("(?i)\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
    dir
      .get(f.key.parent.child(Store.EntryName(name + ".meta.json")))
      .flatMap(m => io.circe.parser.parse(m.text().value).toOption.flatMap(_.asObject))

  private def loadLpf(
      db: Transactor,
      s: Settings,
      apply: Boolean,
      note: JobInfo.Progress => Unit
  ): Either[Problem, List[Gazetteer.LoadCounts]] =
    val files = lpfFiles(s.stores.lpf)
    if files.isEmpty then
      Left(Problem(s"no LPF files in ${s.stores.lpf.where()} (put World Historical Gazetteer downloads there)"))
    else
      files.foldLeft[Either[Problem, List[Gazetteer.LoadCounts]]](Right(Nil)) { (acc, f) =>
        acc.flatMap(done => loadLpfFile(db, s, f, apply, note).map(done :+ _))
      }

  private def loadLpfFile(
      db: Transactor,
      s: Settings,
      f: Blob,
      apply: Boolean,
      note: JobInfo.Progress => Unit
  ): Either[Problem, Gazetteer.LoadCounts] =
    val name = f.key.name.value
    val slug = Gazetteer.datasetSlug(Gazetteer.SourceInfo.File(name))
    val key = Gazetteer.sourceKey(Gazetteer.Kind.Lpf, Some(slug))
    val sha = f.sha256()
    val version = sha.value.take(12)
    val side = sidecar(s.stores.lpf, f)
    val lower = name.toLowerCase
    val isTsv = lower.endsWith(".tsv") || lower.endsWith(".tsv.gz")
    val isLines = lower.matches(".*\\.(ndjson|jsonl)(\\.gz)?$")
    val parsed: Either[Problem, (LpfDataset, Batcher => Unit)] =
      if isTsv then
        val meta = Gazetteer.dataset(JsonObject.empty, side)
        Right(meta -> { (b: Batcher) =>
          var header: List[String] = Nil // the first line
          lines(f) { (line, n) =>
            if header.isEmpty then header = Gazetteer.tsvCells(line)
            else
              b.add(
                Gazetteer
                  .fromTsvRow(
                    header,
                    Gazetteer.tsvCells(line),
                    slug,
                    n,
                    meta.citation.map(c => Gazetteer.SourceInfo.Key(c.value))
                  )
                  .map(Gazetteer.inDataset(_, meta))
              )
          }
        })
      else if isLines then
        val meta = Gazetteer.dataset(JsonObject.empty, side)
        Right(
          meta -> ((b: Batcher) =>
            lines(f)((line, n) =>
              b.add(
                parseLine(line, n, slug, meta.citation.map(c => Gazetteer.SourceInfo.Key(c.value)))
                  .map(Gazetteer.inDataset(_, meta))
              )
            )
          )
        )
      else if f.size.value > s.maxLpfBytes.value then
        Left(
          Problem(
            s"$name is ${f.size.value / (1024 * 1024)} MB, over places.sources.max-lpf-mb — convert it to one feature per line (.ndjson)"
          )
        )
      else
        io.circe.parser
          .parse(Using.resource(open(f))(in => new String(in.readAllBytes(), StandardCharsets.UTF_8)))
          .left
          .map(e => Problem(s"$name: invalid JSON: ${e.getMessage}"))
          .flatMap { doc =>
            val top = doc.asObject.getOrElse(JsonObject.empty)
            val feats = doc.asArray.map(_.toList).orElse(top("features").flatMap(_.asArray).map(_.toList))
            feats.toRight(Problem(s"$name: not a FeatureCollection")).map { fs =>
              val meta = Gazetteer.dataset(top, side)
              meta -> ((b: Batcher) =>
                fs.zipWithIndex.foreach((j, i) =>
                  b.add(
                    Gazetteer
                      .fromFeature(j, slug, i, meta.citation.map(c => Gazetteer.SourceInfo.Key(c.value)))
                      .map(Gazetteer.inDataset(_, meta))
                  )
                )
              )
            }
          }
    parsed.map { (meta, feed) =>
      val (c, seen) = run(db, s, key, Gazetteer.LoadCounts.Version(version), apply, note, name)(feed)
      if apply then
        val span = if !meta.span.isEmpty then meta.span else seen
        db.read(
          GazetteerRepo.recordSource(
            GazetteerSourceRow.Key(key.value),
            Gazetteer.Kind.Lpf,
            GazetteerSourceRow.Title(meta.title.fold(name)(_.value)),
            GazetteerSourceRow.Version(version),
            sha,
            GazetteerSourceRow.File(name),
            None,
            meta.licence.map(l => GazetteerSourceRow.Licence(l.value)),
            meta.citation.map(q => GazetteerSourceRow.Citation(q.value)),
            span
          )
        )
      c
    }

  // ── what is there ──────────────────────────────────────────────────────────────────────────────

  /** `GET /api/places/sources`: the pinned extracts, the LPF files in the drop folder, and what the store last imported
    * from each.
    */
  def list(db: Transactor, s: Settings): List[Gazetteer.SourceInfo] =
    val stored = db.read(GazetteerRepo.sources()).map(r => Gazetteer.SourceInfo.Key(r.key.value) -> r).toMap
    def withStored(i: Gazetteer.SourceInfo): Gazetteer.SourceInfo =
      stored
        .get(i.key)
        .fold(i)(r =>
          i.copy(
            importedVersion = Some(Gazetteer.SourceInfo.ImportedVersion(r.version.value)),
            importedAt = Some(Gazetteer.SourceInfo.ImportedAt(r.importedAt.value)),
            places = Some(Gazetteer.SourceInfo.Places(r.places.value)),
            names = Some(Gazetteer.SourceInfo.Names(r.names.value)),
            links = Some(Gazetteer.SourceInfo.Links(r.links.value)),
            licence = i.licence.orElse(r.licence.map(l => Gazetteer.SourceInfo.Licence(l.value))),
            citation = i.citation.orElse(r.citation.map(q => Gazetteer.SourceInfo.Citation(q.value))),
            periodStart = r.periodStart.map(y => Year(y.value.toLong)),
            periodEnd = r.periodEnd.map(y => Year(y.value.toLong))
          )
        )
    val pinned = manifest(s.stores).toOption.toList.flatMap(_.extracts).flatMap { x =>
      Gazetteer.Kind.parse(Gazetteer.Kind.Wire(x.name.value)).filter(_ != Gazetteer.Kind.Lpf).map { k =>
        val built = x.file.value.trim.nonEmpty && x.sha256.value.trim.nonEmpty // a pin not built yet names no file
        val at = Option.when(built)(locate(s.stores, x.file)).flatten
        def expected = Store.Key.of(x.file.value).toOption.fold(s.stores.extracts.where())(s.stores.extracts.where(_))
        Gazetteer.SourceInfo(
          Gazetteer.sourceKey(k),
          k,
          x.title,
          Option.when(built)(Gazetteer.SourceInfo.File(at.fold(expected)((store, key) => store.where(key)).value)),
          Gazetteer.SourceInfo.Present(at.isDefined),
          Option.when(built)(Gazetteer.SourceInfo.PinnedVersion(x.version.value)),
          licence = Some(x.licence),
          citation = x.attribution.map(a => Gazetteer.SourceInfo.Citation(a.value))
        )
      }
    }
    val lpf = lpfFiles(s.stores.lpf).map { f =>
      val key = Gazetteer.sourceKey(
        Gazetteer.Kind.Lpf,
        Some(Gazetteer.datasetSlug(Gazetteer.SourceInfo.File(f.key.name.value)))
      )
      Gazetteer.SourceInfo(
        key,
        Gazetteer.Kind.Lpf,
        Gazetteer.SourceInfo.Title(f.key.name.value),
        Some(Gazetteer.SourceInfo.File(f.where.value)),
        present = Gazetteer.SourceInfo.Present(true),
        None
      )
    }
    val onDisk = (pinned ++ lpf).map(_.key).toSet
    val gone = stored.filterNot((k, _) => onDisk.contains(k)).values.toList.sortBy(_.key.value).flatMap { r =>
      Gazetteer.Kind
        .parse(Gazetteer.Kind.Wire(r.kind.value))
        .map(k =>
          Gazetteer.SourceInfo(
            Gazetteer.SourceInfo.Key(r.key.value),
            k,
            Gazetteer.SourceInfo.Title(r.title.value),
            Some(Gazetteer.SourceInfo.File(r.file.value)),
            present = Gazetteer.SourceInfo.Present(false),
            None
          )
        )
    }
    (pinned ++ lpf ++ gone).map(withStored)

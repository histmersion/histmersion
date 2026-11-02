package com.histmersion.domain.catalogue.places

import com.histmersion.api.{LpfDataset, Span}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Transactor
import com.histmersion.util.storage.{Blob, Reservation, Store, StoreKey}

import io.circe.{Json, JsonObject}
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import scala.annotation.tailrec
import scala.util.Using
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.{Gazetteer, GazRecord}

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
  * Each source is read in batches into [[GazetteerRepo.Session]] on one connection; `apply = false` is a dry run that
  * rolls every batch back and reports what it would have done. After a real run the source is recorded in
  * `gazetteer_sources` and `finding_places` is re-materialised.
  */
object GazetteerLoad:

  /** Where things are: the extracts' store, the store they lived in before they moved to the cache directory (`<data
    * dir>/gazetteer`, until 2026-10-17: a pinned extract found there and not in `extracts` is moved over once,
    * [[extract]]), the drop folder, and the manifest's text (`manifestAt` names it in messages).
    */
  final case class Stores(
      extracts: Store,
      legacy: Option[Store],
      lpf: Store,
      manifestAt: String,
      manifestText: () => Either[String, String]
  )

  /** Where a pinned file is: in `extracts`, else at the legacy place, else `None`. */
  def locate(p: Stores, file: String): Option[(Store, StoreKey)] =
    StoreKey
      .of(file)
      .toOption
      .flatMap(k => (p.extracts :: p.legacy.toList).find(_.isFile(k)).map(_ -> k))

  /** Every name `histmersion places load` and `init.fetch-sources` accept for these sources. */
  val names: List[String] = Gazetteer.Kind.values.toList.map(_.wire)

  // ── the manifest ───────────────────────────────────────────────────────────────────────────────

  def manifest(p: Stores): Either[String, Gazetteer.Manifest] =
    p.manifestText()
      .flatMap(text =>
        io.circe.parser
          .decode[Gazetteer.Manifest](text)
          .left
          .map(e => s"${p.manifestAt}: ${e.getMessage}")
      )

  private def pin(p: Stores, kind: Gazetteer.Kind): Either[String, Gazetteer.Extract] =
    manifest(p).flatMap(
      _.extracts
        .find(_.name == kind.wire)
        .toRight(s"${p.manifestAt} pins no '${kind.wire}' extract")
    )

  // ── checksums and downloads ────────────────────────────────────────────────────────────────────

  /** Is the file at `key` the pinned one? `Left` says why not — a missing file or a checksum mismatch, which is
    * REFUSED: an extract that is not the pinned one is never imported.
    */
  def verify(store: Store, key: StoreKey, x: Gazetteer.Extract): Either[String, Blob] =
    store.get(key) match
      case None    => Left(s"${store.where(key)} is missing")
      case Some(f) =>
        val got = f.sha256().toString
        if got.equalsIgnoreCase(x.sha256) then Right(f)
        else
          Left(
            s"${store.where(key)} has sha256 $got, but ${x.name} ${x.version} is pinned at ${x.sha256} — refused " +
              "(rebuild it with db/gazetteer/build.py and re-pin, or fetch the pinned file)"
          )

  /** The download seam: url → the file reserved for it (committed by the caller). A generic User-Agent, never a
    * personal one; the composition root gives the client.
    */
  type Download = (String, Reservation, Int) => Either[String, Unit]

  /** Why a pinned extract cannot be read, and what to do: a pin with no checksum is not built yet; a missing file whose
    * pin has no download url (the extracts are not hosted) must be PUT at the exact path the import reads — named here
    * with its checksum and size, and the setting that moves it.
    */
  def missing(p: Stores, x: Gazetteer.Extract): String =
    if x.sha256.trim.isEmpty || x.file.trim.isEmpty then
      s"${x.name} is not pinned yet (${p.manifestAt} names no built file or sha256 for it) — build it with " +
        s"db/gazetteer/build.py and pin it (db/gazetteer/README.md, \"Rebuilding and re-pinning\")"
    else
      val f = StoreKey.of(x.file).toOption.fold(p.extracts.where())(p.extracts.where(_))
      s"${x.name} ${x.version}: ${x.file} is not at $f, and its pin has no download url (the extracts are not " +
        s"hosted). Put the file there — sha256 ${x.sha256}, ${Backup.humanBytes(x.bytes)} — copied from " +
        s"another install or rebuilt with db/gazetteer/build.py (db/gazetteer/README.md); the folder is " +
        s"places.sources.dir (HISTMERSION_GAZETTEER_DIR; by default the XDG cache directory, " +
        s"$$XDG_CACHE_HOME/histmersion/gazetteer or ~/.cache/histmersion/gazetteer). Then run the load again."

  /** A pinned extract at the legacy place (not in `extracts`), VERIFIED, moved over once and logged — so an install
    * that kept its extracts in `<data dir>/gazetteer` keeps them. When the move fails it is read where it is. Importing
    * is idempotent either way: what was imported stays imported.
    */
  private def adopt(p: Stores, x: Gazetteer.Extract, old: Store, key: StoreKey): Either[String, Blob] =
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
  def extract(p: Stores, x: Gazetteer.Extract, download: Download, timeoutSeconds: Int): Either[String, Blob] =
    if x.sha256.trim.isEmpty || x.file.trim.isEmpty then Left(missing(p, x))
    else
      StoreKey.of(x.file) match
        case Left(e)                              => Left(s"${x.file}: $e")
        case Right(key) if p.extracts.isFile(key) => verify(p.extracts, key, x)
        case Right(key)                           =>
          p.legacy.filter(_.isFile(key)) match
            case Some(old) => adopt(p, x, old, key)
            case None      =>
              x.url.map(_.trim).filter(_.nonEmpty) match
                case None      => Left(missing(p, x))
                case Some(url) =>
                  for
                    _ <- p.extracts.makeDirectory().left.map(_.toString)
                    r <- p.extracts.reserve(key).left.map(_.toString)
                    _ <- download(url, r, timeoutSeconds).left.map { e => r.discard(); e }
                    _ <- r.commit().left.map(_.toString)
                    f <- verify(p.extracts, key, x).left.map { e =>
                      val _ = p.extracts.delete(key)
                      e
                    }
                  yield f

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  private def open(f: Blob): InputStream =
    val raw = f.open()
    if f.key.name.endsWith(".gz") then new GZIPInputStream(raw, 1 << 16) else raw

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
    var total: Gazetteer.LoadCounts = Gazetteer.LoadCounts("", "") // the running sum
    var span: Span = Span.always // the widest known years of the records, for an LPF dataset that states none

    def add(r: Either[String, GazRecord]): Unit = r match
      case Left(e)    => total = total.copy(skipped = (total.skipped :+ e).take(20))
      case Right(rec) =>
        span = Span((span.from ++ rec.span.from).minOption, (span.to ++ rec.span.to).maxOption)
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
      batch: Int,
      maxLpfBytes: Long,
      downloadTimeoutSeconds: Int,
      toleranceKm: Double,
      rebuild: Boolean = true,
      openStartWindowYears: Int = Gazetteer.DefaultOpenStartWindowYears,
      pointAreaKinds: Set[String] = Gazetteer.DefaultPointAreaKinds.toSet
  )

  def settings(cfg: AppConfig, stores: Stores): Settings =
    Settings(
      stores,
      math.max(1, cfg.places.sources.batch),
      cfg.places.sources.maxLpfMb.toLong * 1024 * 1024,
      cfg.places.sources.downloadTimeoutSeconds,
      cfg.places.pointToleranceKm,
      openStartWindowYears = cfg.places.sources.openStartWindowYears,
      pointAreaKinds = cfg.places.sources.pointAreaKindSet
    )

  /** Import (or, `apply = false`, dry-run) one source kind. `note` reports progress on the job. */
  def load(
      db: Transactor,
      s: Settings,
      kind: Gazetteer.Kind,
      apply: Boolean,
      note: String => Unit,
      download: Download
  ): Either[String, List[Gazetteer.LoadCounts]] =
    val result = kind match
      case Gazetteer.Kind.Lpf => loadLpf(db, s, apply, note)
      case k                  =>
        for
          x <- pin(s.stores, k)
          f <- extract(s.stores, x, download, s.downloadTimeoutSeconds)
        yield
          val (c, _) = run(db, s, Gazetteer.sourceKey(k), x.version, apply, note, f.key.name) { b =>
            lines(f)((line, n) => b.add(parseLine(line, n, "", None)))
          }
          if apply then
            db.read(
              GazetteerRepo.recordSource(
                Gazetteer.sourceKey(k),
                k,
                x.title,
                x.version,
                x.sha256,
                x.file,
                x.url,
                Some(x.licence),
                x.attribution,
                Span.always
              )
            )
          List(c)
    result.map { cs =>
      if apply && s.rebuild && cs.exists(!_.unchanged) then
        note("matching findings to places")
        val _ = db.transact(FindingPlaceRepo.rebuildFindingPlaces(s.toleranceKm))
      cs
    }

  private def parseLine(line: String, n: Int, dataset: String, source: Option[String]): Either[String, GazRecord] =
    io.circe.parser
      .parse(line)
      .left
      .map(e => s"line $n: ${e.getMessage}")
      .flatMap(Gazetteer.fromFeature(_, dataset, n, source).left.map(e => s"line $n: $e"))

  /** One source through one session: batches, then (when every line was read) the sweep. */
  private def run(
      db: Transactor,
      s: Settings,
      key: String,
      version: String,
      apply: Boolean,
      note: String => Unit,
      label: String
  )(feed: Batcher => Unit): (Gazetteer.LoadCounts, Span) =
    db.read:
      val session = GazetteerRepo.session(key, version, apply, s.openStartWindowYears, s.pointAreaKinds)
      val pruned = session.prune(s.batch)
      if pruned > 0 then note(s"$key: $pruned point-only area place(s) removed")
      var batches = 0 // progress
      val b = Batcher(
        s.batch,
        rs => {
          batches += 1
          if batches % 10 == 1 then note(s"$key: $label, batch $batches")
          session.batch(rs)
        }
      )
      feed(b)
      b.flush()
      val swept = session.sweep()
      (
        (b.total + swept + Gazetteer.LoadCounts(key, version, placesRemoved = pruned))
          .copy(source = key, version = version),
        b.span
      )

  // ── LPF ────────────────────────────────────────────────────────────────────────────────────────

  private val LpfFile = """(?i).+\.(json|jsonld|geojson|ndjson|jsonl|tsv)(\.gz)?$""".r

  /** The data files of the drop folder, by name (a `*.meta.json` sidecar is not one). */
  def lpfFiles(dir: Store): List[Blob] =
    dir
      .list()
      .filter(f => f.isFile && LpfFile.matches(f.key.name) && !f.key.name.toLowerCase.endsWith(".meta.json"))
      .sortBy(_.key.name)
      .flatMap(f => dir.get(f.key))

  private def sidecar(dir: Store, f: Blob): Option[JsonObject] =
    val name = f.key.name.replaceAll("(?i)\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
    dir
      .get(f.key.parent.child(name + ".meta.json"))
      .flatMap(m => io.circe.parser.parse(m.text()).toOption.flatMap(_.asObject))

  private def loadLpf(
      db: Transactor,
      s: Settings,
      apply: Boolean,
      note: String => Unit
  ): Either[String, List[Gazetteer.LoadCounts]] =
    val files = lpfFiles(s.stores.lpf)
    if files.isEmpty then
      Left(s"no LPF files in ${s.stores.lpf.where()} (put World Historical Gazetteer downloads there)")
    else
      files.foldLeft[Either[String, List[Gazetteer.LoadCounts]]](Right(Nil)) { (acc, f) =>
        acc.flatMap(done => loadLpfFile(db, s, f, apply, note).map(done :+ _))
      }

  private def loadLpfFile(
      db: Transactor,
      s: Settings,
      f: Blob,
      apply: Boolean,
      note: String => Unit
  ): Either[String, Gazetteer.LoadCounts] =
    val name = f.key.name
    val slug = Gazetteer.datasetSlug(name)
    val key = Gazetteer.sourceKey(Gazetteer.Kind.Lpf, Some(slug))
    val sha = f.sha256().toString
    val version = sha.take(12)
    val side = sidecar(s.stores.lpf, f)
    val lower = name.toLowerCase
    val isTsv = lower.endsWith(".tsv") || lower.endsWith(".tsv.gz")
    val isLines = lower.matches(".*\\.(ndjson|jsonl)(\\.gz)?$")
    val parsed: Either[String, (LpfDataset, Batcher => Unit)] =
      if isTsv then
        val meta = Gazetteer.dataset(JsonObject.empty, side)
        Right(meta -> { (b: Batcher) =>
          var header: List[String] = Nil // the first line
          lines(f) { (line, n) =>
            if header.isEmpty then header = Gazetteer.tsvCells(line)
            else
              b.add(
                Gazetteer
                  .fromTsvRow(header, Gazetteer.tsvCells(line), slug, n, meta.citation)
                  .map(Gazetteer.inDataset(_, meta))
              )
          }
        })
      else if isLines then
        val meta = Gazetteer.dataset(JsonObject.empty, side)
        Right(
          meta -> ((b: Batcher) =>
            lines(f)((line, n) => b.add(parseLine(line, n, slug, meta.citation).map(Gazetteer.inDataset(_, meta))))
          )
        )
      else if f.size.value > s.maxLpfBytes then
        Left(
          s"$name is ${f.size.value / (1024 * 1024)} MB, over places.sources.max-lpf-mb — convert it to one feature per line (.ndjson)"
        )
      else
        io.circe.parser
          .parse(Using.resource(open(f))(in => new String(in.readAllBytes(), StandardCharsets.UTF_8)))
          .left
          .map(e => s"$name: invalid JSON: ${e.getMessage}")
          .flatMap { doc =>
            val top = doc.asObject.getOrElse(JsonObject.empty)
            val feats = doc.asArray.map(_.toList).orElse(top("features").flatMap(_.asArray).map(_.toList))
            feats.toRight(s"$name: not a FeatureCollection").map { fs =>
              val meta = Gazetteer.dataset(top, side)
              meta -> ((b: Batcher) =>
                fs.zipWithIndex.foreach((j, i) =>
                  b.add(Gazetteer.fromFeature(j, slug, i, meta.citation).map(Gazetteer.inDataset(_, meta)))
                )
              )
            }
          }
    parsed.map { (meta, feed) =>
      val (c, seen) = run(db, s, key, version, apply, note, name)(feed)
      if apply then
        val span = if !meta.span.isEmpty then meta.span else seen
        db.read(
          GazetteerRepo.recordSource(
            key,
            Gazetteer.Kind.Lpf,
            meta.title.getOrElse(name),
            version,
            sha,
            name,
            None,
            meta.licence,
            meta.citation,
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
    val stored = db.read(GazetteerRepo.sources()).map(r => r.key -> r).toMap
    def withStored(i: Gazetteer.SourceInfo): Gazetteer.SourceInfo =
      stored
        .get(i.key)
        .fold(i)(r =>
          i.copy(
            importedVersion = Some(r.version),
            importedAt = Some(r.importedAt),
            places = Some(r.places),
            names = Some(r.names),
            links = Some(r.links),
            licence = i.licence.orElse(r.licence),
            citation = i.citation.orElse(r.citation),
            periodStart = r.periodStart,
            periodEnd = r.periodEnd
          )
        )
    val pinned = manifest(s.stores).toOption.toList.flatMap(_.extracts).flatMap { x =>
      Gazetteer.Kind.parse(x.name).filter(_ != Gazetteer.Kind.Lpf).map { k =>
        val built = x.file.trim.nonEmpty && x.sha256.trim.nonEmpty // a pin not built yet names no file
        val at = Option.when(built)(locate(s.stores, x.file)).flatten
        def expected = StoreKey.of(x.file).toOption.fold(s.stores.extracts.where())(s.stores.extracts.where(_))
        Gazetteer.SourceInfo(
          Gazetteer.sourceKey(k),
          k,
          x.title,
          Option.when(built)(at.fold(expected)((store, key) => store.where(key))),
          at.isDefined,
          Option.when(built)(x.version),
          licence = Some(x.licence),
          citation = x.attribution
        )
      }
    }
    val lpf = lpfFiles(s.stores.lpf).map { f =>
      val key = Gazetteer.sourceKey(Gazetteer.Kind.Lpf, Some(Gazetteer.datasetSlug(f.key.name)))
      Gazetteer.SourceInfo(key, Gazetteer.Kind.Lpf, f.key.name, Some(f.where), present = true, None)
    }
    val onDisk = (pinned ++ lpf).map(_.key).toSet
    val gone = stored.values.filterNot(r => onDisk.contains(r.key)).toList.sortBy(_.key).flatMap { r =>
      Gazetteer.Kind
        .parse(r.kind)
        .map(k => Gazetteer.SourceInfo(r.key, k, r.title, Some(r.file), present = false, None))
    }
    (pinned ++ lpf ++ gone).map(withStored)

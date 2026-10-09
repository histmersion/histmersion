package com.histmersion.server.importers

import com.histmersion.core.*
import com.histmersion.db.{GazetteerRepo, PlaceRepo}

import com.augustnagro.magnum.connect
import io.circe.{Json, JsonObject}
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.net.{HttpURLConnection, URI}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import scala.util.Using

/** The gazetteer's external sources (#1064) — the `geonames`, `wikidata` and `lpf` steps of the
  * one-time gazetteer import job ([[com.histmersion.server.Bootstrap]]'s init job, the same that
  * imports the bundled seed and fetches Cliopatria or PeriodO).
  *
  *   - `geonames`, `wikidata`: a PINNED extract (`db/gazetteer/extracts.json`: file, version,
  *     sha256), Linked Places Format with one feature per line, gzip. Looked for in
  *     `places.sources.dir`; when missing and its pin names a `url`, downloaded there first. Its
  *     sha256 is checked BEFORE anything is read — a mismatch is refused.
  *   - `lpf`: every `*.json` / `*.jsonld` / `*.geojson` (a FeatureCollection, read whole up to
  *     `max-lpf-mb`), `*.ndjson` / `*.jsonl` (a feature per line, optionally `.gz`) and `*.tsv`
  *     (LP-TSV) in `places.sources.lpf-dir` — the World Historical Gazetteer downloads a person puts
  *     there. Each file is a dataset (`lpf:<file name>`), its version the file's sha256; its title,
  *     citation, licence and time span come from the file or a `<file>.meta.json` beside it.
  *
  * Each source is read in batches into [[GazetteerRepo.Session]] on one connection; `apply = false`
  * is a dry run that rolls every batch back and reports what it would have done. After a real run the
  * source is recorded in `gazetteer_sources` and `finding_places` is re-materialised. */
object GazetteerLoad:

  /** Where things are, resolved once from the config. */
  final case class Paths(extracts: Path, lpf: Path, manifest: Path)

  def paths(cfg: AppConfig, env: String => Option[String] = sys.env.get): Paths =
    val data = UserPaths.dataDir(cfg.runtime.dataDir, env, System.getProperty("os.name", ""))
      .getOrElse(Path.of(cfg.init.dataDir)).resolve("gazetteer")
    def or(configured: String, fallback: Path): Path =
      Option(configured).map(_.trim).filter(_.nonEmpty)
        .map(p => Path.of(p.replaceFirst("^~", System.getProperty("user.home")))).getOrElse(fallback)
    Paths(or(cfg.places.sources.dir, data), or(cfg.places.sources.lpfDir, data.resolve("lpf")),
      Path.of(cfg.init.dataDir).resolve(cfg.places.sources.manifest))

  /** Every name `histmersion places load` and `init.fetch-sources` accept for these sources. */
  val names: List[String] = GazetteerKind.values.toList.map(_.wire)

  // ── the manifest ───────────────────────────────────────────────────────────────────────────────

  def manifest(p: Paths): Either[String, GazetteerManifest] =
    if !Files.isRegularFile(p.manifest) then Left(s"no gazetteer manifest at ${p.manifest} (set places.sources.manifest)")
    else io.circe.parser.decode[GazetteerManifest](Files.readString(p.manifest)).left.map(e => s"${p.manifest}: ${e.getMessage}")

  private def pin(p: Paths, kind: GazetteerKind): Either[String, GazetteerExtract] =
    manifest(p).flatMap(_.extracts.find(_.name == kind.wire)
      .toRight(s"${p.manifest} pins no '${kind.wire}' extract"))

  // ── checksums and downloads ────────────────────────────────────────────────────────────────────

  /** The sha256 of a file, streamed. */
  def sha256(f: Path): String =
    val md = MessageDigest.getInstance("SHA-256")
    Using.resource(Files.newInputStream(f)) { in =>
      val buf = new Array[Byte](1 << 16)
      var n = in.read(buf) // a read loop
      while n >= 0 do
        md.update(buf, 0, n)
        n = in.read(buf)
    }
    md.digest().map(b => f"$b%02x").mkString

  /** Is `f` the pinned file? `Left` says why not — a missing file or a checksum mismatch, which is
    * REFUSED: an extract that is not the pinned one is never imported. */
  def verify(f: Path, x: GazetteerExtract): Either[String, Unit] =
    if !Files.isRegularFile(f) then Left(s"$f is missing")
    else
      val got = sha256(f)
      if got.equalsIgnoreCase(x.sha256) then Right(())
      else Left(s"$f has sha256 $got, but ${x.name} ${x.version} is pinned at ${x.sha256} — refused " +
        "(rebuild it with db/gazetteer/build.py and re-pin, or fetch the pinned file)")

  /** The download seam: url → file. A generic User-Agent, never a personal one. */
  type Download = (String, Path, Int) => Either[String, Unit]

  val httpDownload: Download = (url, to, timeoutSeconds) =>
    try
      val conn = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
      conn.setInstanceFollowRedirects(true)
      conn.setRequestProperty("User-Agent", s"Histmersion/${Version.version} (gazetteer import)")
      conn.setConnectTimeout(30000)
      conn.setReadTimeout(timeoutSeconds * 1000)
      val code = conn.getResponseCode
      if code != 200 then Left(s"HTTP $code from $url")
      else
        val part = to.resolveSibling(to.getFileName.toString + ".part")
        Using.resource(conn.getInputStream)(in => Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING))
        Files.move(part, to, StandardCopyOption.REPLACE_EXISTING)
        Right(())
    catch case e: Exception => Left(s"download of $url failed: ${e.getMessage}")

  /** The pinned extract on disk, fetched first when it is missing and its pin names a url. */
  def extract(p: Paths, x: GazetteerExtract, download: Download, timeoutSeconds: Int): Either[String, Path] =
    val f = p.extracts.resolve(x.file)
    if Files.isRegularFile(f) then verify(f, x).map(_ => f)
    else x.url.map(_.trim).filter(_.nonEmpty) match
      case None =>
        Left(s"${x.name} extract ${x.file} not found in ${p.extracts} — build it with db/gazetteer/build.py " +
          s"(see db/gazetteer/README.md) and copy it there")
      case Some(url) =>
        Files.createDirectories(p.extracts)
        download(url, f, timeoutSeconds).flatMap { _ =>
          verify(f, x).left.map { e =>
            val _ = Files.deleteIfExists(f)
            e
          }.map(_ => f)
        }

  // ── reading ────────────────────────────────────────────────────────────────────────────────────

  private def open(f: Path): InputStream =
    val raw = Files.newInputStream(f)
    if f.getFileName.toString.endsWith(".gz") then new GZIPInputStream(raw, 1 << 16) else raw

  /** Lines of a (gzip) text file, handed to `each` with their 1-based number. */
  private def lines(f: Path)(each: (String, Int) => Unit): Unit =
    Using.resource(new BufferedReader(new InputStreamReader(open(f), StandardCharsets.UTF_8), 1 << 16)) { r =>
      var n = 0 // a read loop
      var line = r.readLine()
      while line != null do
        n += 1
        if line.trim.nonEmpty then each(line, n)
        line = r.readLine()
    }

  /** Feed the records of one file to `sink` in batches; a malformed record is skipped and named. */
  private final class Batcher(size: Int, sink: List[GazRecord] => GazetteerLoadCounts):
    private val buf  = List.newBuilder[GazRecord] // the batch being filled
    private var n    = 0 // its size
    var total: GazetteerLoadCounts = GazetteerLoadCounts("", "") // the running sum
    var span: Span = Span.always // the widest known years of the records, for an LPF dataset that states none

    def add(r: Either[String, GazRecord]): Unit = r match
      case Left(e) => total = total.copy(skipped = (total.skipped :+ e).take(20))
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

  /** `rebuild`: re-materialise `finding_places` after a real run that changed something (a test
    * turns it off: a full rebuild touches every other suite's findings). */
  final case class Settings(paths: Paths, batch: Int, maxLpfBytes: Long, downloadTimeoutSeconds: Int, toleranceKm: Double,
      rebuild: Boolean = true, openStartWindowYears: Int = Gazetteer.DefaultOpenStartWindowYears)

  def settings(cfg: AppConfig): Settings =
    Settings(paths(cfg), math.max(1, cfg.places.sources.batch), cfg.places.sources.maxLpfMb.toLong * 1024 * 1024,
      cfg.places.sources.downloadTimeoutSeconds, cfg.places.pointToleranceKm,
      openStartWindowYears = cfg.places.sources.openStartWindowYears)

  /** Import (or, `apply = false`, dry-run) one source kind. `note` reports progress on the job. */
  def load(ds: DataSource, s: Settings, kind: GazetteerKind, apply: Boolean, note: String => Unit,
      download: Download = httpDownload): Either[String, List[GazetteerLoadCounts]] =
    val result = kind match
      case GazetteerKind.Lpf => loadLpf(ds, s, apply, note)
      case k =>
        for
          x <- pin(s.paths, k)
          f <- extract(s.paths, x, download, s.downloadTimeoutSeconds)
        yield
          val (c, _) = run(ds, s, Gazetteer.sourceKey(k), x.version, apply, note, f.getFileName.toString) { b =>
            lines(f)((line, n) => b.add(parseLine(line, n, "", None)))
          }
          if apply then
            connect(ds)(GazetteerRepo.recordSource(Gazetteer.sourceKey(k), k, x.title, x.version, x.sha256, x.file,
              x.url, Some(x.licence), x.attribution, Span.always))
          List(c)
    result.map { cs =>
      if apply && s.rebuild && cs.exists(!_.unchanged) then
        note("matching findings to places")
        val _ = PlaceRepo.rebuildFindingPlaces(ds, s.toleranceKm)
      cs
    }

  private def parseLine(line: String, n: Int, dataset: String, source: Option[String]): Either[String, GazRecord] =
    io.circe.parser.parse(line).left.map(e => s"line $n: ${e.getMessage}")
      .flatMap(Gazetteer.fromFeature(_, dataset, n, source).left.map(e => s"line $n: $e"))

  /** One source through one session: batches, then (when every line was read) the sweep. */
  private def run(ds: DataSource, s: Settings, key: String, version: String, apply: Boolean, note: String => Unit,
      label: String)(feed: Batcher => Unit): (GazetteerLoadCounts, Span) =
    connect(ds):
      val session = GazetteerRepo.session(key, version, apply, s.openStartWindowYears)
      var batches = 0 // progress
      val b = Batcher(s.batch, rs => {
        batches += 1
        if batches % 10 == 1 then note(s"$key: $label, batch $batches")
        session.batch(rs)
      })
      feed(b)
      b.flush()
      val swept = session.sweep()
      ((b.total + swept).copy(source = key, version = version), b.span)

  // ── LPF ────────────────────────────────────────────────────────────────────────────────────────

  private val LpfFile = """(?i).+\.(json|jsonld|geojson|ndjson|jsonl|tsv)(\.gz)?$""".r

  /** The data files of the drop folder, by name (a `*.meta.json` sidecar is not one). */
  def lpfFiles(dir: Path): List[Path] =
    if !Files.isDirectory(dir) then Nil
    else
      Using.resource(Files.list(dir))(_.iterator.asScala.toList)
        .filter(f => Files.isRegularFile(f) && LpfFile.matches(f.getFileName.toString)
          && !f.getFileName.toString.toLowerCase.endsWith(".meta.json"))
        .sortBy(_.getFileName.toString)

  private def sidecar(f: Path): Option[JsonObject] =
    val name = f.getFileName.toString.replaceAll("(?i)\\.(jsonld|json|geojson|ndjson|jsonl|tsv)(\\.gz)?$", "")
    val meta = f.resolveSibling(name + ".meta.json")
    Option.when(Files.isRegularFile(meta))(meta)
      .flatMap(m => io.circe.parser.parse(Files.readString(m)).toOption.flatMap(_.asObject))

  private def loadLpf(ds: DataSource, s: Settings, apply: Boolean, note: String => Unit): Either[String, List[GazetteerLoadCounts]] =
    val files = lpfFiles(s.paths.lpf)
    if files.isEmpty then Left(s"no LPF files in ${s.paths.lpf} (put World Historical Gazetteer downloads there)")
    else
      files.foldLeft[Either[String, List[GazetteerLoadCounts]]](Right(Nil)) { (acc, f) =>
        acc.flatMap(done => loadLpfFile(ds, s, f, apply, note).map(done :+ _))
      }

  private def loadLpfFile(ds: DataSource, s: Settings, f: Path, apply: Boolean, note: String => Unit): Either[String, GazetteerLoadCounts] =
    val name    = f.getFileName.toString
    val slug    = Gazetteer.datasetSlug(name)
    val key     = Gazetteer.sourceKey(GazetteerKind.Lpf, Some(slug))
    val sha     = sha256(f)
    val version = sha.take(12)
    val side    = sidecar(f)
    val lower   = name.toLowerCase
    val isTsv   = lower.endsWith(".tsv") || lower.endsWith(".tsv.gz")
    val isLines = lower.matches(".*\\.(ndjson|jsonl)(\\.gz)?$")
    val parsed: Either[String, (LpfDataset, Batcher => Unit)] =
      if isTsv then
        val meta = Gazetteer.dataset(JsonObject.empty, side)
        Right(meta -> { (b: Batcher) =>
          var header: List[String] = Nil // the first line
          lines(f) { (line, n) =>
            if header.isEmpty then header = Gazetteer.tsvCells(line)
            else b.add(Gazetteer.fromTsvRow(header, Gazetteer.tsvCells(line), slug, n, meta.citation)
              .map(Gazetteer.inDataset(_, meta)))
          }
        })
      else if isLines then
        val meta = Gazetteer.dataset(JsonObject.empty, side)
        Right(meta -> ((b: Batcher) => lines(f)((line, n) =>
          b.add(parseLine(line, n, slug, meta.citation).map(Gazetteer.inDataset(_, meta))))))
      else if Files.size(f) > s.maxLpfBytes then
        Left(s"$name is ${Files.size(f) / (1024 * 1024)} MB, over places.sources.max-lpf-mb — convert it to one feature per line (.ndjson)")
      else
        io.circe.parser.parse(Using.resource(open(f))(in => new String(in.readAllBytes(), StandardCharsets.UTF_8)))
          .left.map(e => s"$name: invalid JSON: ${e.getMessage}")
          .flatMap { doc =>
            val top = doc.asObject.getOrElse(JsonObject.empty)
            val feats = doc.asArray.map(_.toList).orElse(top("features").flatMap(_.asArray).map(_.toList))
            feats.toRight(s"$name: not a FeatureCollection").map { fs =>
              val meta = Gazetteer.dataset(top, side)
              meta -> ((b: Batcher) => fs.zipWithIndex.foreach((j, i) =>
                b.add(Gazetteer.fromFeature(j, slug, i, meta.citation).map(Gazetteer.inDataset(_, meta)))))
            }
          }
    parsed.map { (meta, feed) =>
      val (c, seen) = run(ds, s, key, version, apply, note, name)(feed)
      if apply then
        val span = if !meta.span.isEmpty then meta.span else seen
        connect(ds)(GazetteerRepo.recordSource(key, GazetteerKind.Lpf, meta.title.getOrElse(name), version, sha, name,
          None, meta.licence, meta.citation, span))
      c
    }

  // ── what is there ──────────────────────────────────────────────────────────────────────────────

  /** `GET /api/places/sources`: the pinned extracts, the LPF files in the drop folder, and what the
    * store last imported from each. */
  def list(ds: DataSource, s: Settings): List[GazetteerSourceInfo] =
    val stored = GazetteerRepo.sources(ds).map(r => r.key -> r).toMap
    def withStored(i: GazetteerSourceInfo): GazetteerSourceInfo =
      stored.get(i.key).fold(i)(r => i.copy(importedVersion = Some(r.version), importedAt = Some(r.importedAt),
        places = Some(r.places), names = Some(r.names), links = Some(r.links),
        licence = i.licence.orElse(r.licence), citation = i.citation.orElse(r.citation),
        periodStart = r.periodStart, periodEnd = r.periodEnd))
    val pinned = manifest(s.paths).toOption.toList.flatMap(_.extracts).flatMap { x =>
      GazetteerKind.parse(x.name).filter(_ != GazetteerKind.Lpf).map { k =>
        GazetteerSourceInfo(Gazetteer.sourceKey(k), k, x.title, Some(s.paths.extracts.resolve(x.file).toString),
          Files.isRegularFile(s.paths.extracts.resolve(x.file)), Some(x.version), licence = Some(x.licence),
          citation = x.attribution)
      }
    }
    val lpf = lpfFiles(s.paths.lpf).map { f =>
      val key = Gazetteer.sourceKey(GazetteerKind.Lpf, Some(Gazetteer.datasetSlug(f.getFileName.toString)))
      GazetteerSourceInfo(key, GazetteerKind.Lpf, f.getFileName.toString, Some(f.toString), present = true, None)
    }
    val onDisk = (pinned ++ lpf).map(_.key).toSet
    val gone = stored.values.filterNot(r => onDisk.contains(r.key)).toList.sortBy(_.key).flatMap { r =>
      GazetteerKind.parse(r.kind).map(k => GazetteerSourceInfo(r.key, k, r.title, Some(r.file), present = false, None))
    }
    (pinned ++ lpf ++ gone).map(withStored)

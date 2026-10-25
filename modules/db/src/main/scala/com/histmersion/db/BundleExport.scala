package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import java.io.OutputStream
import java.nio.file.Files
import java.util.UUID
import javax.sql.DataSource

/** `bundle export` (docs/research-merge-import.md §4, milestone M1): the database as a `.hmx`
  * bundle, written to a stream.
  *
  * Everything is read inside ONE `REPEATABLE READ` transaction, so the bundle is a snapshot even
  * while writes go on. The entity files are small and built in memory; the blobs are streamed one
  * at a time, straight from Postgres (an OCR body, a version body, one asset's bytes) or from the
  * stored-originals directory, so the size of the bundle never matters to the heap.
  *
  * Deterministic: the same database gives the same bytes (see [[BundleTar]] and
  * [[BundleManifest]]), which is the M1 acceptance test. */
object BundleExport:

  /** Where one blob's bytes come from. */
  private enum Blob:
    case SourceBody(uid: UUID)
    case VersionBody(uid: UUID)
    case AssetBytes(uid: UUID)
    case Original(path: java.nio.file.Path)

  def write(ds: DataSource, out: OutputStream, since: Option[String], originals: Originals): Either[String, BundleManifest] =
    transact(ds):
      sql"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY".update.run()
      val scope    = since.fold(BundleScope.All)(BundleScope.Since(_))
      val inst     = BundleRepo.instance
      val schema   = BundleRepo.schemaVersion
      val srcRows  = BundleRepo.sources(scope)
      val verRows  = BundleRepo.versions(scope)
      val assetRows = BundleRepo.assets(scope)
      val entities = BundleRepo.entities(scope, originals, srcRows, verRows, assetRows)

      // sha256 → (size, where from); the first use of a digest is the one read
      val blobs: Map[String, (Long, Blob)] =
        (srcRows.flatMap(r => r.bodySha.zip(r.bodyBytes).map((sha, n) => sha -> (n, Blob.SourceBody(r.uid)))) ++
          srcRows.flatMap(r => r.originalSha.flatMap(sha => originals.find(sha).map((p, _) =>
            sha -> (Files.size(p), Blob.Original(p))))) ++
          verRows.map(r => r.bodySha -> (r.bodyBytes, Blob.VersionBody(r.uid))) ++
          assetRows.map(r => r.sha -> (r.bytes, Blob.AssetBytes(r.uid))))
          .foldLeft(Map.empty[String, (Long, Blob)]) { case (m, (sha, b)) => if m.contains(sha) then m else m + (sha -> b) }
      val shas     = blobs.keys.toList.sorted
      val payloads = entities.payloads
      val files    = payloads.map((p, b) => BundleFormat.fileInfo(p, b))
      val manifest = BundleManifest(
        format = Bundle.Format,
        formatVersion = Bundle.FormatVersion,
        schemaVersion = schema.getOrElse("0"),
        bundleId = BundleFormat.bundleId(inst.id, since, files, shas),
        instance = BundleInstance(inst.id, inst.label),
        since = since,
        asOf = entities.asOf,
        files = files,
        blobs = BundleBlobs(shas.size, shas.map(blobs(_)._1).sum),
        reference = BundleRepo.reference)

      val tar = BundleTar.Writer(out)
      val written =
        for
          _ <- tar.bytes(Bundle.ManifestPath, BundleFormat.manifestBytes(manifest))
          _ <- payloads.foldLeft[Either[String, Unit]](Right(()))((acc, pb) => acc.flatMap(_ => tar.bytes(pb._1, pb._2)))
          _ <- shas.foldLeft[Either[String, Unit]](Right(())) { (acc, sha) =>
                 acc.flatMap { _ =>
                   val (size, from) = blobs(sha)
                   tar.entry(Bundle.blobPath(sha), size)(o => copy(from, o))
                 }
               }
        yield tar.finish()
      written.map(_ => manifest)

  /** One blob, from wherever it lives, into the tar entry. */
  private def copy(b: Blob, out: OutputStream)(using DbCon): Unit = b match
    case Blob.SourceBody(uid) =>
      out.write(sql"SELECT convert_to(ocr_markdown, 'UTF8') FROM sources WHERE uid = $uid".query[Array[Byte]].run().head)
    case Blob.VersionBody(uid) =>
      out.write(sql"SELECT convert_to(body, 'UTF8') FROM article_versions WHERE uid = $uid".query[Array[Byte]].run().head)
    case Blob.AssetBytes(uid) =>
      out.write(sql"SELECT bytes FROM assets WHERE uid = $uid".query[Array[Byte]].run().head)
    case Blob.Original(path) =>
      val in = Files.newInputStream(path)
      try { val _ = in.transferTo(out) } finally in.close()

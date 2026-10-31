package com.histmersion.util.data.exchange

import com.histmersion.api.*
import com.histmersion.db.BundleRepo
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.*
import java.io.OutputStream
import javax.sql.DataSource

/** `bundle export` (docs/research-merge-import.md §4, milestone M1): the database as a `.hmx` bundle, written to a
  * stream, from the carried kinds of the registry.
  *
  * Everything is read inside ONE `REPEATABLE READ` transaction, so the bundle is a snapshot even while writes go on.
  * The entity files are small and built in memory; the blobs are streamed one at a time from where their kind reads
  * them (an OCR body, a version body, one asset's bytes from Postgres; a stored original from `files`), so the size of
  * the bundle never matters to the heap.
  *
  * Deterministic: the same database gives the same bytes (see [[BundleTar]] and [[BundleManifest]]), which is the M1
  * acceptance test.
  */
object BundleExport:

  def write(
      ds: DataSource,
      out: OutputStream,
      since: Option[String],
      files: ContentStore,
      kinds: CarriedKinds = CarriedKinds.transition
  ): Either[String, BundleManifest] =
    transact(ds):
      sql"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY".update.run()
      val scope = since.fold(BundleScope.All)(BundleScope.Since(_))
      val inst = BundleRepo.instance
      val schema = BundleRepo.schemaVersion
      val carried = kinds.inBundleOrder.map(k => k.entry.value -> k.out(scope, files))

      // the first use of a digest is the one read
      val blobs: Map[String, BlobOut] =
        carried.flatMap(_._2.blobs).foldLeft(Map.empty[String, BlobOut]) { (m, b) =>
          if m.contains(b.sha256) then m else m + (b.sha256 -> b)
        }
      val shas = blobs.keys.toList.sorted
      val payloads = carried.map((entry, o) => entry -> o.payload)
      val listed = payloads.map((p, b) => BundleFormat.fileInfo(p, b))
      val manifest = BundleManifest(
        format = Bundle.Format,
        formatVersion = Bundle.FormatVersion,
        schemaVersion = schema.getOrElse("0"),
        bundleId = BundleFormat.bundleId(inst.id, since, listed, shas),
        instance = BundleInstance(inst.id, inst.label),
        since = since,
        asOf = carried.flatMap(_._2.asOf).maxOption,
        files = listed,
        blobs = BundleBlobs(shas.size, shas.map(blobs(_).size).sum),
        reference = BundleRepo.reference
      )

      val tar = BundleTar.Writer(out)
      val written =
        for
          _ <- tar.bytes(Bundle.ManifestPath, BundleFormat.manifestBytes(manifest))
          _ <- payloads.foldLeft[Either[String, Unit]](Right(()))((acc, pb) =>
            acc.flatMap(_ => tar.bytes(pb._1, pb._2))
          )
          _ <- shas.foldLeft[Either[String, Unit]](Right(())) { (acc, sha) =>
            acc.flatMap { _ =>
              val b = blobs(sha)
              tar.entry(Bundle.blobPath(sha), b.size)(b.copy)
            }
          }
        yield tar.finish()
      written.map(_ => manifest)

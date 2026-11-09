package com.histmersion
package util.data.exchange
import com.histmersion.api.problem.Text.sentence
import com.histmersion.util.problem.Problem

import com.histmersion.util.data.exchange.BundleRepo
import com.histmersion.util.storage.ContentStore

import com.augustnagro.magnum.*
import java.io.OutputStream
import javax.sql.DataSource
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount

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
      since: Option[BundleScope.Since.At],
      files: ContentStore,
      kinds: CarriedKinds
  ): Either[String, Bundle.Manifest] =
    transact(ds):
      sql"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY".update.run()
      val scope = since.fold(BundleScope.All)(BundleScope.Since(_))
      val inst = BundleRepo.instance
      val schema = BundleRepo.schemaVersion
      val carried = kinds.inBundleOrder.map(k => k.entry -> k.out(scope, files))

      // the first use of a digest is the one read
      val blobs: Map[Sha256, BlobOut] =
        carried.flatMap(_._2.blobs).foldLeft(Map.empty[Sha256, BlobOut]) { (m, b) =>
          if m.contains(b.sha256) then m else m + (b.sha256 -> b)
        }
      val shas = blobs.keys.toList.sorted
      val payloads = carried.map((entry, o) => entry -> o.payload)
      val listed = payloads.map((p, b) => BundleFormat.fileInfo(Bundle.FileInfo.Path(p.value), b.value))
      val manifestSince = since.map(at => Bundle.Manifest.Since(at.value))
      val manifest = Bundle.Manifest(
        format = Bundle.Manifest.Format(Bundle.Format),
        formatVersion = Bundle.Manifest.FormatVersion(Bundle.FormatVersion),
        schemaVersion = schema.getOrElse(Bundle.Manifest.SchemaVersion("0")),
        bundleId =
          BundleFormat.bundleId(inst.id, manifestSince, listed, shas.map(s => com.histmersion.api.Sha256(s.value))),
        instance = Bundle.Instance(inst.id, inst.label.map(l => Bundle.Instance.Label(l.value))),
        since = manifestSince,
        asOf = carried.flatMap(_._2.asOf.map(_.value)).maxOption.map(Bundle.Manifest.AsOf(_)),
        files = listed,
        blobs = Bundle.Blobs(
          Bundle.Blobs.Count(shas.size),
          ByteCount(shas.map(blobs(_).size.value).sum)
        ),
        reference = BundleRepo.reference
      )

      val tar = BundleTar.Writer(out)
      val written =
        for
          _ <- tar.bytes(BundleTar.Entry.Name(Bundle.ManifestPath), BundleFormat.manifestBytes(manifest))
          _ <- payloads.foldLeft[Either[Problem, Unit]](Right(()))((acc, pb) =>
            acc.flatMap(_ => tar.bytes(BundleTar.Entry.Name(pb._1.value), pb._2.value))
          )
          _ <- shas.foldLeft[Either[Problem, Unit]](Right(())) { (acc, sha) =>
            acc.flatMap { _ =>
              val b = blobs(sha)
              tar.entry(BundleTar.Entry.Name(Bundle.blobPath(com.histmersion.api.Sha256(sha.value))), b.size)(b.copy)
            }
          }
        yield tar.finish()
      written.left.map(_.sentence).map(_ => manifest)

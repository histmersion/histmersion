package com.histmersion
package server

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.ocr.ConvertedDoc

import java.nio.file.Path
import com.histmersion.util.storage.Blob
import com.histmersion.util.storage.Storage
import com.histmersion.util.storage.Location
import com.histmersion.util.storage.Store

/** A converter for HTTP tests that never leaves the process. `Composition.server(cfg, host, port)` builds the REAL
  * client from `converter.url` — `http://localhost:8100`, the live sidecar on this machine — so a test that accepted an
  * upload sent its bytes to the live converter, and a `/api/health` probed it (2026-10-09: `POST /inspect` +
  * `POST /convert` → 422 in the live converter.log, once per suite run). Every conversion is refused and recorded, so a
  * test can tell that a job reached the stub.
  */
final class OfflineConverter extends ConverterClient:
  private val seen = java.util.concurrent.ConcurrentLinkedQueue[ConverterClient.Filename]()

  /** The file names the jobs asked to convert, in order. */
  def converted: List[ConverterClient.Filename] =
    import scala.jdk.CollectionConverters.*
    seen.asScala.toList

  def health: Either[ConverterClient.Problem, ConverterClient.HealthSummary] = Right(ConverterClient.HealthSummary("ok"))
  def convert(
      file: Blob,
      filename: ConverterClient.Filename,
      quality: ConverterClient.HighQuality
  ): Either[ConverterClient.Problem, ConvertedDoc] =
    seen.add(filename)
    Left(ConverterClient.Problem("offline: no converter in tests"))

object OfflineConverter:
  /** The file `p` as a converter is handed it. */
  def blob(p: Path): Blob =
    Storage
      .at(Location.Scratch, p.toAbsolutePath.getParent)
      .get(Store.Key.applyUnsafe(p.getFileName.toString))
      .getOrElse(throw AssertionError(s"$p is not a file"))

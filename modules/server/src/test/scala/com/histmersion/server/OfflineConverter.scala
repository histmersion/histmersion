package com.histmersion.server

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.ConvertedDoc

import java.nio.file.Path

/** A converter for HTTP tests that never leaves the process. `Composition.server(cfg, host, port)` builds the REAL
  * client from `converter.url` — `http://localhost:8100`, the live sidecar on this machine — so a test that accepted an
  * upload sent its bytes to the live converter, and a `/api/health` probed it (2026-10-09: `POST /inspect` +
  * `POST /convert` → 422 in the live converter.log, once per suite run). Every conversion is refused and recorded, so a
  * test can tell that a job reached the stub.
  */
final class OfflineConverter extends ConverterClient:
  private val seen = java.util.concurrent.ConcurrentLinkedQueue[String]()

  /** The file names the jobs asked to convert, in order. */
  def converted: List[String] =
    import scala.jdk.CollectionConverters.*
    seen.asScala.toList

  def health: Either[String, String] = Right("ok")
  def convert(
      file: com.histmersion.util.storage.Blob,
      filename: String,
      quality: Boolean
  ): Either[String, ConvertedDoc] =
    seen.add(filename)
    Left("offline: no converter in tests")

object OfflineConverter:
  /** The file `p` as a converter is handed it. */
  def blob(p: Path): com.histmersion.util.storage.Blob =
    com.histmersion.util.storage.Storage
      .at(com.histmersion.util.storage.Location.Scratch, p.toAbsolutePath.getParent)
      .get(com.histmersion.util.storage.StoreKey.applyUnsafe(p.getFileName.toString))
      .getOrElse(throw AssertionError(s"$p is not a file"))

package com.histmersion.server

import com.histmersion.core.ConvertedDoc

import java.nio.file.Path

/** A converter for HTTP tests that never leaves the process. `Main.server(cfg, host, port)` builds the
  * REAL client from `converter.url` — `http://localhost:8100`, the live sidecar on this machine — so a
  * test that accepted an upload sent its bytes to the live converter, and a `/api/health` probed it
  * (2026-10-09: `POST /inspect` + `POST /convert` → 422 in the live converter.log, once per suite run).
  * Every conversion is refused and recorded, so a test can tell that a job reached the stub. */
final class OfflineConverter extends ConverterClient:
  private val seen = java.util.concurrent.ConcurrentLinkedQueue[String]()

  /** The file names the jobs asked to convert, in order. */
  def converted: List[String] =
    import scala.jdk.CollectionConverters.*
    seen.asScala.toList

  def health: Either[String, String] = Right("ok")
  def convert(file: Path, filename: String, quality: Boolean): Either[String, ConvertedDoc] =
    seen.add(filename)
    Left("offline: no converter in tests")

package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/reindex`: one resumable slice of the FTS chunk rebuild. Re-indexing the whole corpus costs minutes
  * (Postgres spends ~2 ms per chunk building the tsvector), far past any HTTP timeout, so the endpoint walks `sources`
  * in citekey order and does a bounded amount of work per call: `lastCitekey` is the cursor to pass back as `after`,
  * `remaining` is how many sources are still untouched. The CLI loops until `remaining` is 0.
  */
final case class ReindexResult(sources: Int, chunks: Int, lastCitekey: Option[String], remaining: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ReindexResult:
  given Schema[ReindexResult] = summon[KindlingsSchema[ReindexResult]].schema

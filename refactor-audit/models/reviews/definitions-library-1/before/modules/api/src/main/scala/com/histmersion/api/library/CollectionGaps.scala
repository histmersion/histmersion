package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The sources with a fetched file that are not in a collection: `count` gaps, `uncounted` records without a file,
  * `total` both, and every row.
  */
final case class CollectionGaps(
    count: Int,
    uncounted: Int,
    total: Int,
    gaps: List[CollectionGapRow]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a rename changed, as counted inside its transaction. */
final case class RenameCounts(
    /** Findings whose id moved from `<old>#<n>` to `<new>#<n>`. */
    findings: Int,
    /** Articles whose body or summary linked the old citekey (or a short form of it) and was rewritten — each through
      * the article write path, so each has a new entry in its history.
      */
    articles: List[String],
    /** Sources whose notes or summary linked the old citekey and were rewritten. */
    sources: List[String],
    /** `[[…]]` links rewritten across those articles and sources. */
    links: Int,
    /** Rows of the columns without a foreign key (events, jobs, place types, attestations…). */
    columns: List[RenameCount],
    /** Parked edges (`unresolved_edges`) that named the NEW citekey and became real edges. */
    edgesPromoted: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RenameCounts:
  given Schema[RenameCounts] = summon[KindlingsSchema[RenameCounts]].schema

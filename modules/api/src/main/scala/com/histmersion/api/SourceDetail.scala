package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** Wire DTOs — the contract between server, CLI and MCP. Circe codec AND tapir Schema are both derived by Kindlings
  * from the same case class, so JSON and OpenAPI/MCP schemas match by construction. The `db` module adds Magnum
  * `DbCodec`s for the same types.
  */

/** Full source record for `get_source`. */
final case class SourceDetail(
    citekey: String,
    title: String,
    authors: List[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    doi: Option[String],
    ocrMarkdown: Option[String],
    /** Agent-written article about the source (the note's Markdown body). */
    notes: Option[String] = None,
    /** Opaque last-write token; echo it back as `patch_source`'s `ifUpdatedAt` precondition. */
    updatedAt: Option[String] = None,
    /** How the source was acquired: manual|ill|author|scan|external-file|calibre|archive|oa. */
    provenance: Option[String] = None,
    /** The permanent identity (V030): unlike the citekey it survives a rename, and a bundle carries it to another
      * instance.
      */
    uid: Option[java.util.UUID] = None,
    /** The instance that created the source (this one, or the one a bundle import brought it from). */
    origin: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SourceDetail:
  given Schema[SourceDetail] = summon[KindlingsSchema[SourceDetail]].schema

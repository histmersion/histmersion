package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** Selective enrichment. Every top-level part is independently optional; a present part replaces only itself, an absent
  * part is untouched. `ifUpdatedAt` is an optimistic-concurrency precondition: supply the `updatedAt` you last read and
  * the write is rejected (HTTP 412) if the row moved on.
  */
final case class SourcePatch(
    metadata: Option[MetadataPatch] = None,
    summary: Option[String] = None,
    body: Option[String] = None,
    notes: Option[String] = None,
    topics: Option[List[String]] = None,
    regions: Option[List[String]] = None,
    period: Option[PeriodInput] = None,
    tags: Option[ListPatch] = None,
    findings: Option[FindingsPatch] = None,
    edges: Option[EdgesPatch] = None,
    ifUpdatedAt: Option[String] = None
) derives KindlingsSchema
object SourcePatch:
  given Schema[SourcePatch] = summon[KindlingsSchema[SourcePatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[SourcePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[SourcePatch])

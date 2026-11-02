package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.{ListPatch, StrictJson}
import com.histmersion.api.catalogue.PeriodInput
import com.histmersion.api.given

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

  given Codec.AsObject[SourcePatch] = StrictJson.codec(KindlingsCodecAsObject.derived[SourcePatch])

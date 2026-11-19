package com.histmersion
package api.library
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class MetadataPatch(
    title: Option[Title] = None,
    authors: Option[List[AuthorName]] = None,
    year: Option[Year] = None,
    venue: Option[Source.Venue] = None,
    doi: Option[Doi] = None,
    status: Option[Source.Status] = None,
    provenance: Option[MetadataPatch.Provenance] = None,
    calibreId: Option[Source.CalibreId] = None,
    clear: List[MetadataField] = Nil
) derives KindlingsSchema,
      api.json.Schema,
      CanEqual
object MetadataPatch:

  export Source.Provenance

  private val derived: JsonCodec[MetadataPatch] = JsonCodec.derived[MetadataPatch]

  given JsonCodec[MetadataPatch] = StrictJson.codec(
    JsonCodec.mapped[MetadataPatch, Json](
      m => m.asJson(using derived).dropNullValues,
      json =>
        json.asObject match
          case None    => json.as(using derived)
          case Some(o) =>
            val nulls = o.toList.collect { case (k, v) if v.isNull => k }
            val (refused, cleared) = nulls.partitionMap(k => MetadataField.fromWire(MetadataField.Wire(k)).toRight(k))
            if refused.nonEmpty then
              Left(
                s"metadata.${refused.mkString(", metadata.")} cannot be cleared — null is accepted only on " +
                  s"${MetadataField.values.map(MetadataField.wireOf).mkString(", ")}; omit a field to leave it alone"
              )
            else
              Json
                .fromJsonObject(o.filter((_, v) => !v.isNull))
                .as(using derived)
                .map(m => m.modify(_.clear).setTo((m.clear ++ cleared).distinct))
    )
  )

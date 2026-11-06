package com.histmersion.api.library
import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import sttp.tapir.Schema
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MetadataPatch(
    title: Option[com.histmersion.api.library.Title] = None,
    authors: Option[List[com.histmersion.api.library.AuthorName]] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    venue: Option[com.histmersion.api.library.Source.Venue] = None,
    doi: Option[com.histmersion.api.library.Doi] = None,
    status: Option[com.histmersion.api.library.Source.Status] = None,
    provenance: Option[MetadataPatch.Provenance] = None,
    calibreId: Option[com.histmersion.api.library.Source.CalibreId] = None,
    clear: List[MetadataField] = Nil
) derives KindlingsSchema,
      com.histmersion.api.json.Schema
object MetadataPatch:


  private val derived: Codec.AsObject[MetadataPatch] = KindlingsCodecAsObject.derived[MetadataPatch]

  given Codec.AsObject[MetadataPatch] = Codec.AsObject.from(
    StrictJson.decoder(Decoder.instance { c =>
      c.value.asObject match
        case None    => derived(c)
        case Some(o) =>
          val nulls = o.toList.collect { case (k, v) if v.isNull => k }
          val (refused, cleared) = nulls.partitionMap(k => MetadataField.fromWire(_root_.com.histmersion.api.library.MetadataField.Wire(k)).toRight(k))
          if refused.nonEmpty then
            Left(
              DecodingFailure(
                s"metadata.${refused.mkString(", metadata.")} cannot be cleared — null is accepted only on " +
                  s"${MetadataField.values.map(MetadataField.wireOf).mkString(", ")}; omit a field to leave it alone",
                c.history
              )
            )
          else
            derived
              .decodeJson(Json.fromJsonObject(o.filter((_, v) => !v.isNull)))
              .map(m => m.modify(_.clear).setTo((m.clear ++ cleared).distinct))
    }),
    Encoder.AsObject.instance(m => derived.encodeObject(m).filter((_, v) => !v.isNull))
  )

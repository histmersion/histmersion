package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** Scalar metadata. A present field replaces; an absent one is left alone.
  *
  * Clearing (#745): `clear` names the fields to store as NULL. An explicit JSON `null` on a clearable field means the
  * same thing — `{"doi":null}` decodes to `clear = [doi]` — so "absent" and "null" are no longer the same value. A
  * `null` on a field that cannot be cleared (title, authors, status) is refused instead of being silently ignored. `""`
  * on `venue`/`doi` still clears, as before.
  *
  * `calibreId` (#721) re-links the source to another Calibre entry (`histmersion calibre link` checks the entry exists
  * first); it must be the entry's numeric id.
  */
final case class MetadataPatch(
    title: Option[String] = None,
    authors: Option[List[String]] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    doi: Option[String] = None,
    status: Option[SourceStatus] = None,
    provenance: Option[String] = None,
    calibreId: Option[String] = None,
    clear: List[MetadataField] = Nil
) derives KindlingsSchema
object MetadataPatch:
  given Schema[MetadataPatch] = summon[KindlingsSchema[MetadataPatch]].schema

  private val derived: Codec.AsObject[MetadataPatch] = KindlingsCodecAsObject.derived[MetadataPatch]

  /** The derived codec, wrapped so that absent and `null` differ (see the class doc). The decoder moves every `null`
    * key into `clear` (or refuses it); the encoder drops `None` fields instead of writing them as `null`, so a patch
    * the CLI or the web UI sends means what its value says. An unknown key is refused before any of that (#813,
    * [[StrictJson]]).
    */
  given Codec.AsObject[MetadataPatch] = Codec.AsObject.from(
    StrictJson.decoder(Decoder.instance { c =>
      c.value.asObject match
        case None    => derived(c)
        case Some(o) =>
          val nulls = o.toList.collect { case (k, v) if v.isNull => k }
          val (refused, cleared) = nulls.partitionMap(k => MetadataField.fromWire(k).toRight(k))
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
              .map(m => m.copy(clear = (m.clear ++ cleared).distinct))
    }),
    Encoder.AsObject.instance(m => derived.encodeObject(m).filter((_, v) => !v.isNull))
  )

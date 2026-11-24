package com.histmersion
package api.library
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.optics.syntax.*

import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.{Schema, SchemaType, Validator}
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.MetadataPatch.absentOnly
import com.histmersion.lint.{LintRule, tolerated}

final case class MetadataPatch(
    @Schema.annotations.customise(absentOnly) title: Option[Title] = None,
    @Schema.annotations.customise(absentOnly) authors: Option[List[AuthorName]] = None,
    year: Option[Year] = None,
    venue: Option[Source.Venue] = None,
    doi: Option[Doi] = None,
    @Schema.annotations.customise(absentOnly) status: Option[Source.Status] = None,
    provenance: Option[MetadataPatch.Provenance] = None,
    calibreId: Option[Source.CalibreId] = None,
    clear: List[MetadataField] = Nil
) derives KindlingsSchema,
      api.json.Schema,
      CanEqual
object MetadataPatch:

  export Source.Provenance

  val absentOnly: Schema[?] => Schema[?] = field =>
    field.schemaType match
      case SchemaType.SOption(element) =>
        Schema[Any](
          element.schemaType.asInstanceOf[SchemaType[Any]],
          element.name,
          isOptional = true,
          element.description,
          None,
          element.format,
          element.encodedExample,
          element.deprecated,
          element.hidden,
          absentOr(element.validator),
          element.attributes
        )
      case _ => field

  private def absentOr(validator: Validator[?]): Validator[Any] = validator match
    case e: Validator.Enumeration[?] =>
      val values = e.possibleValues.asInstanceOf[List[Any]]
      val encode = e.encode.asInstanceOf[Option[Any => Option[Any]]]
      Validator.Enumeration[Any](
        None :: values.map(Some(_)),
        Some((value: Any) => value.asInstanceOf[Option[Any]].flatMap(v => encode.fold(Some(v))(_(v)))),
        e.name
      )
    case _ => Validator.pass[Any]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private val derived: JsonCodec[MetadataPatch] = JsonCodec.derived[MetadataPatch]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

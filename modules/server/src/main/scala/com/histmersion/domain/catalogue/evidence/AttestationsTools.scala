package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.catalogue.evidence.AttestationsMarkdown

import chimp.server.*
import com.histmersion.api.{AttestationPut, Markdown, StrictJson}
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** The attestations for agents: list or show, create, delete. */
object AttestationsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class AttestationsInput(
      id: Option[Long] = None,
      citekey: Option[String] = None,
      targetType: Option[String] = None,
      targetId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object AttestationsInput:
    given Schema[AttestationsInput] = KindlingsSchema
      .derived[AttestationsInput]
      .schema
      .modify(_.id)(_.description("Attestation id to look up directly (omit for listing)"))
      .modify(_.citekey)(_.description("Filter by source citekey"))
      .modify(_.targetType)(_.description("Filter: finding|event|event_dating|place|edge"))
      .modify(_.targetId)(_.description("Filter by target entity id"))

  final case class AttestationPutInput(
      citekey: String,
      targetType: String,
      targetId: String,
      quote: Option[String] = None,
      prefix: Option[String] = None,
      suffix: Option[String] = None,
      charStart: Option[Int] = None,
      charEnd: Option[Int] = None,
      chunkOrd: Option[Int] = None,
      page: Option[String] = None,
      canvas: Option[String] = None,
      xywh: Option[String] = None,
      ctsUrn: Option[String] = None,
      note: Option[String] = None
  )
  object AttestationPutInput:
    given Schema[AttestationPutInput] = KindlingsSchema
      .derived[AttestationPutInput]
      .schema
      .modify(_.citekey)(_.description("Source citekey this attestation references"))
      .modify(_.targetType)(_.description("Entity type: finding|event|event_dating|place|edge"))
      .modify(_.targetId)(_.description("Id of the target entity"))
      .modify(_.quote)(_.description("TextQuoteSelector: the exact quoted passage"))
      .modify(_.prefix)(_.description("TextQuoteSelector: text immediately before the quote"))
      .modify(_.suffix)(_.description("TextQuoteSelector: text immediately after the quote"))
      .modify(_.charStart)(_.description("TextPositionSelector: character offset start in OCR body"))
      .modify(_.charEnd)(_.description("TextPositionSelector: character offset end in OCR body"))
      .modify(_.chunkOrd)(_.description("source_chunks ordinal (existing FTS chunk index)"))
      .modify(_.page)(_.description("Page/folio label: '42', 'f.12r', 'pl.VII'"))
      .modify(_.canvas)(_.description("IIIF canvas URI"))
      .modify(_.xywh)(_.description("IIIF region 'x,y,w,h'"))
      .modify(_.ctsUrn)(_.description("CTS URN for classical texts"))
    given Codec.AsObject[AttestationPutInput] =
      StrictJson.codec(KindlingsCodecAsObject.derived[AttestationPutInput]) // #813

  final case class DeleteAttestationInput(id: Long) derives KindlingsCodecAsObject
  object DeleteAttestationInput:
    given Schema[DeleteAttestationInput] = KindlingsSchema
      .derived[DeleteAttestationInput]
      .schema
      .modify(_.id)(_.description("Id of the attestation to delete"))

  def apply(attestations: AttestationsControllers) =
    val listTool =
      tool("attestations")
        .description(
          "ATTESTATIONS: granular source references linking entities (findings, events, places, edges) " +
            "to specific passages in a source. W3C Web Annotation selectors: TextQuoteSelector (quote/" +
            "prefix/suffix), TextPositionSelector (charStart/charEnd), chunk ordinals, page labels, " +
            "IIIF canvas+xywh, CTS URNs. Without `id` it LISTS attestations filtered by citekey/" +
            "targetType/targetId; with `id` it SHOWS one attestation."
        )
        .input[AttestationsInput]
        .handle { in =>
          in.id match
            case Some(id) =>
              attestations.get(id) match
                case Right(a) => ToolResult.text(AttestationsMarkdown.attestation(a))
                case Left(e)  => McpOut.error(e)
            case None =>
              ToolResult.text(
                AttestationsMarkdown
                  .attestationList(attestations.list(in.citekey, in.targetType, in.targetId, in.limit, in.offset))
              )
        }

    val putTool =
      tool("put_attestation")
        .description(
          "Create a new attestation linking an entity to a specific passage in a source. Returns the " +
            "created attestation with its generated id. Target types: finding|event|event_dating|place|edge."
        )
        .input[AttestationPutInput]
        .handle { in =>
          val put = AttestationPut(
            in.citekey,
            in.targetType,
            in.targetId,
            in.quote,
            in.prefix,
            in.suffix,
            in.charStart,
            in.charEnd,
            in.chunkOrd,
            in.page,
            in.canvas,
            in.xywh,
            in.ctsUrn,
            in.note
          )
          attestations.create(Caller.Local, put) match
            case Right(a) => ToolResult.text(AttestationsMarkdown.attestation(a))
            case Left(e)  => McpOut.error(e)
        }

    val deleteTool =
      tool("delete_attestation")
        .description("Delete an attestation by its id.")
        .input[DeleteAttestationInput]
        .handle { in =>
          attestations.delete(Caller.Local, in.id) match
            case Right(d) => ToolResult.text(s"Deleted attestation #${d.id}.")
            case Left(e)  => McpOut.error(e)
        }
    List(listTool, putTool, deleteTool)

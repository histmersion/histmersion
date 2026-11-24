package com.histmersion
package domain.catalogue.evidence
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.AttestationsMarkdown

import chimp.server.*
import com.histmersion.api.{Markdown, StrictJson}
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.library.Citekey
import com.histmersion.domain.Creator
import com.histmersion.api.automation.Creation
import com.histmersion.domain.CreatorControllers

/** The attestations for agents: list or show, create, delete. */
object AttestationsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class AttestationsInput(
      id: Option[Attestation.Id] = None,
      citekey: Option[Citekey] = None,
      targetType: Option[Attestation.TargetType] = None,
      targetId: Option[Attestation.TargetId] = None,
      limit: Option[PageSize] = None,
      offset: Option[Offset] = None
  ) derives JsonCodec
  object AttestationsInput:
    given Schema[AttestationsInput] = KindlingsSchema
      .derived[AttestationsInput]
      .schema
      .modify(_.id)(_.description("Attestation id to look up directly (omit for listing)"))
      .modify(_.citekey)(_.description("Filter by source citekey"))
      .modify(_.targetType)(_.description("Filter: finding|event|event_dating|place|edge"))
      .modify(_.targetId)(_.description("Filter by target entity id"))

  final case class AttestationPutInput(
      citekey: Citekey,
      targetType: Attestation.TargetType,
      targetId: Attestation.TargetId,
      quote: Option[Attestation.Quote] = None,
      prefix: Option[Attestation.Prefix] = None,
      suffix: Option[Attestation.Suffix] = None,
      charStart: Option[Attestation.CharStart] = None,
      charEnd: Option[Attestation.CharEnd] = None,
      chunkOrd: Option[Attestation.ChunkOrd] = None,
      page: Option[Attestation.Page] = None,
      canvas: Option[Attestation.Canvas] = None,
      xywh: Option[Attestation.Xywh] = None,
      ctsUrn: Option[Attestation.CtsUrn] = None,
      note: Option[Attestation.Note] = None,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives StrictJson // #813
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

  final case class DeleteAttestationInput(id: Attestation.Id) derives JsonCodec
  object DeleteAttestationInput:
    given Schema[DeleteAttestationInput] = KindlingsSchema
      .derived[DeleteAttestationInput]
      .schema
      .modify(_.id)(_.description("Id of the attestation to delete"))

  def apply(attestations: AttestationsControllers) =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)
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
          CreatorControllers.mcp(in) {
            val put = Attestation.Put(
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

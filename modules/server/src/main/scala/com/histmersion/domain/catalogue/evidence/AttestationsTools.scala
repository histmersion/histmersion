package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.catalogue.AttestationsMarkdown

import chimp.server.*
import com.histmersion.api.{Markdown, StrictJson}
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.catalogue.Attestation

/** The attestations for agents: list or show, create, delete. */
object AttestationsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class AttestationsInput(
      id: Option[AttestationsInput.Id] = None,
      citekey: Option[com.histmersion.domain.library.Citekey] = None,
      targetType: Option[AttestationsInput.TargetType] = None,
      targetId: Option[AttestationsInput.TargetId] = None,
      limit: Option[AttestationsInput.Limit] = None,
      offset: Option[AttestationsInput.Offset] = None
  ) derives KindlingsCodecAsObject
  object AttestationsInput:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetType = TargetType.T
    object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetId = TargetId.T
    object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = com.histmersion.PageSize
    val Limit: _root_.com.histmersion.PageSize.type = _root_.com.histmersion.PageSize
    type Offset = com.histmersion.Offset
    val Offset: _root_.com.histmersion.Offset.type = _root_.com.histmersion.Offset

    given Schema[AttestationsInput] = KindlingsSchema
      .derived[AttestationsInput]
      .schema
      .modify(_.id)(_.description("Attestation id to look up directly (omit for listing)"))
      .modify(_.citekey)(_.description("Filter by source citekey"))
      .modify(_.targetType)(_.description("Filter: finding|event|event_dating|place|edge"))
      .modify(_.targetId)(_.description("Filter by target entity id"))

  final case class AttestationPutInput(
      citekey: com.histmersion.domain.library.Citekey,
      targetType: AttestationPutInput.TargetType,
      targetId: AttestationPutInput.TargetId,
      quote: Option[AttestationPutInput.Quote] = None,
      prefix: Option[AttestationPutInput.Prefix] = None,
      suffix: Option[AttestationPutInput.Suffix] = None,
      charStart: Option[AttestationPutInput.CharStart] = None,
      charEnd: Option[AttestationPutInput.CharEnd] = None,
      chunkOrd: Option[AttestationPutInput.ChunkOrd] = None,
      page: Option[AttestationPutInput.Page] = None,
      canvas: Option[AttestationPutInput.Canvas] = None,
      xywh: Option[AttestationPutInput.Xywh] = None,
      ctsUrn: Option[AttestationPutInput.CtsUrn] = None,
      note: Option[AttestationPutInput.Note] = None
  )
  object AttestationPutInput:
    type TargetType = com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetType
    val TargetType: _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetType.type = _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetType
    type TargetId = com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId
    val TargetId: _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId.type = _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.TargetId
    type Quote = com.histmersion.domain.catalogue.evidence.TextAnchor.Quote
    val Quote: _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Quote.type = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Quote
    type Prefix = com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix
    val Prefix: _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix.type = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Prefix
    type Suffix = com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix
    val Suffix: _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix.type = _root_.com.histmersion.domain.catalogue.evidence.TextAnchor.Suffix
    type CharStart = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CharStart
    val CharStart: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CharStart.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CharStart
    type CharEnd = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CharEnd
    val CharEnd: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CharEnd.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CharEnd
    type ChunkOrd = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.ChunkOrd
    val ChunkOrd: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.ChunkOrd.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.ChunkOrd
    type Page = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Page
    val Page: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Page.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Page
    type Canvas = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Canvas
    val Canvas: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Canvas.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Canvas
    type Xywh = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Xywh
    val Xywh: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Xywh.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Xywh
    type CtsUrn = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CtsUrn
    val CtsUrn: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CtsUrn.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.CtsUrn
    type Note = com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Note
    val Note: _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Note.type = _root_.com.histmersion.domain.catalogue.evidence.BAttestationFullRow.Note

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

  final case class DeleteAttestationInput(id: DeleteAttestationInput.Id) derives KindlingsCodecAsObject
  object DeleteAttestationInput:
    type Id = com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.Id
    val Id: _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.Id.type = _root_.com.histmersion.domain.catalogue.evidence.AttestationsTools.AttestationsInput.Id

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

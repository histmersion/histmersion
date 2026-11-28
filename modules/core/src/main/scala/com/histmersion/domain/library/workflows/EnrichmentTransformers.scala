package com.histmersion
package domain.library.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.scalaland.chimney.dsl.*
import com.histmersion.util.json.{JsonCodec, RawJson}
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.domain.automation.agents.Checkpointed
import com.histmersion.api.library.workflows as wire

object EnrichmentTransformers extends EnrichRecords:
  override given loaded: Checkpointed[Loaded] = stored[Loaded, wire.LoadedSource]
  override given metaChecked: Checkpointed[MetaChecked] = stored[MetaChecked, wire.MetaChecked]
  override given classified: Checkpointed[Classified] = stored[Classified, wire.Classified]
  override given findings: Checkpointed[Findings] = stored[Findings, wire.ProposedFindings]
  override given verifiedFindings: Checkpointed[VerifiedFindings] = stored[VerifiedFindings, wire.VerifiedFindings]
  override given geocoded: Checkpointed[Geocoded] = stored[Geocoded, wire.Geocoded]
  override given patched: Checkpointed[Patched] = stored[Patched, wire.Patched]
  override given upstreamed: Checkpointed[Upstreamed] = stored[Upstreamed, wire.Upstreamed]
  override given statusRouted: Checkpointed[StatusRouted] = stored[StatusRouted, wire.StatusRouted]
  override given closed: Checkpointed[Closed] = stored[Closed, wire.ClosedRun]
  override given notesText: Checkpointed[String] = new Checkpointed[String]:
    def write(text: String): Checkpointed.Json = Checkpointed.Json.of(text.asJson.noSpaces)
    def read(json: Checkpointed.Json): Either[Checkpointed.Unread, String] = json.parsed(RawJson.decode[String](_))

  val classificationAnswer: JsonCodec[Classification] = answer[Classification, wire.Classification]
  val findingsAnswer: JsonCodec[Findings] = answer[Findings, wire.ProposedFindings]
  val notesAnswer: JsonCodec[Notes] = answer[Notes, wire.Notes]

  private inline def stored[D, W](using codec: JsonValueCodec[W]): Checkpointed[D] =
    new Checkpointed[D]:
      def write(d: D): Checkpointed.Json = Checkpointed.Json.of(d.transformInto[W].asJson.noSpaces)
      def read(json: Checkpointed.Json): Either[Checkpointed.Unread, D] =
        json.parsed(RawJson.decode[W](_).flatMap(_.fromWire[D]))

  private inline def answer[D, W](using codec: JsonValueCodec[W]): JsonCodec[D] =
    JsonCodec.mapped[D, W](_.transformInto[W], _.fromWire[D])

  extension [W](w: W)
    private inline def fromWire[D]: Either[String, D] =
      w.transformIntoPartial[D]
        .asEither
        .left
        .map(_.asErrorPathMessageStrings.map((at, why) => s"$at: $why").mkString("; "))

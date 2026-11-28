package com.histmersion
package domain.library.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.scalaland.chimney.dsl.*
import com.histmersion.util.json.{JsonCodec, RawJson}
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.domain.automation.agents.Stored
import com.histmersion.api.library.workflows as wire

object EnrichSourceTransformers extends EnrichRecords:
  override given loaded: Stored[Loaded] = stored[Loaded, wire.LoadedSource]
  override given metaChecked: Stored[MetaChecked] = stored[MetaChecked, wire.MetaChecked]
  override given classified: Stored[Classified] = stored[Classified, wire.Classified]
  override given findings: Stored[Findings] = stored[Findings, wire.Findings]
  override given verifiedFindings: Stored[VerifiedFindings] = stored[VerifiedFindings, wire.VerifiedFindings]
  override given geocoded: Stored[Geocoded] = stored[Geocoded, wire.Geocoded]
  override given patched: Stored[Patched] = stored[Patched, wire.Patched]
  override given upstreamed: Stored[Upstreamed] = stored[Upstreamed, wire.Upstreamed]
  override given statusRouted: Stored[StatusRouted] = stored[StatusRouted, wire.StatusRouted]
  override given closed: Stored[Closed] = stored[Closed, wire.ClosedRun]
  override given notesText: Stored[String] = new Stored[String]:
    def write(text: String): Stored.Json = Stored.Json(text.asJson.noSpaces)
    def read(json: Stored.Json): Either[Stored.Unread, String] = RawJson.decode[String](json.value).left.map(Stored.Unread(_))

  val classificationAnswer: JsonCodec[Classification] = answer[Classification, wire.Classification]
  val findingsAnswer: JsonCodec[Findings] = answer[Findings, wire.Findings]
  val notesAnswer: JsonCodec[Notes] = answer[Notes, wire.Notes]

  private inline def stored[D, W](using codec: JsonValueCodec[W]): Stored[D] =
    new Stored[D]:
      def write(d: D): Stored.Json = Stored.Json(d.transformInto[W].asJson.noSpaces)
      def read(json: Stored.Json): Either[Stored.Unread, D] =
        RawJson.decode[W](json.value).flatMap(fromWire[D, W]).left.map(Stored.Unread(_))

  private inline def answer[D, W](using codec: JsonValueCodec[W]): JsonCodec[D] =
    JsonCodec.mapped[D, W](_.transformInto[W], fromWire[D, W])

  private inline def fromWire[D, W](w: W): Either[String, D] =
    w.transformIntoPartial[D].asEither.left.map(_.asErrorPathMessageStrings.map((at, why) => s"$at: $why").mkString("; "))

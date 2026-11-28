package com.histmersion
package domain.library.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.catalogue.Place as WirePlace
import com.histmersion.api.library.{Citekey as WireCitekey, Doi as WireDoi}
import com.histmersion.domain.automation.Run
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.{Citekey, Doi}
import com.histmersion.util.json.{JsonCodec, RawJson}
import com.histmersion.util.json.RawJson.*
import com.histmersion.domain.automation.agents.Checkpointed

trait TwinTransformers:
  protected given storedCitekey: Transformer[WireCitekey, Citekey] = ck => Citekey.assume(s"$ck")
  protected given storedDoi: Transformer[WireDoi, Doi] = doi => Doi.assume(s"$doi")
  protected given storedPlaceId: Transformer[WirePlace.Id, Place.Id] = id => Place.Id.assume(s"$id")
  protected given storedRunId: Transformer[RunInfo.Id, Run.Id] = id => Run.Id.assume(s"$id")

  protected inline def stored[D, W](using codec: JsonValueCodec[W]): Checkpointed[D] =
    new Checkpointed[D]:
      def write(d: D): Checkpointed.Json = Checkpointed.Json.of(d.transformInto[W].asJson.noSpaces)
      def read(json: Checkpointed.Json): Either[Checkpointed.Unread, D] =
        json.parsed(RawJson.decode[W](_).flatMap(_.fromWire[D]))

  protected inline def answer[D, W](using codec: JsonValueCodec[W]): JsonCodec[D] =
    JsonCodec.mapped[D, W](_.transformInto[W], _.fromWire[D])

  extension [W](w: W)
    protected inline def fromWire[D]: Either[String, D] =
      w.transformIntoPartial[D]
        .asEither
        .left
        .map(_.asErrorPathMessageStrings.map((at, why) => s"$at: $why").mkString("; "))

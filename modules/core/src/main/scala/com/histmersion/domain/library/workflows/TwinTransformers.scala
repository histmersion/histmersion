package com.histmersion
package domain.library.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import io.scalaland.chimney.dsl.*
import com.histmersion.util.json.{JsonCodec, RawJson}
import com.histmersion.util.json.RawJson.*
import com.histmersion.domain.automation.agents.Checkpointed

trait TwinTransformers:
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

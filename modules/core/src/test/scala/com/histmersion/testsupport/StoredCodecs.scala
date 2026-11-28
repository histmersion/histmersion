package com.histmersion
package testsupport

import java.nio.charset.StandardCharsets.UTF_8
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import com.histmersion.domain.automation.agents.Stored

object StoredCodecs:
  given viaPort: [A] => (port: Stored[A]) => JsonValueCodec[A]:
    def nullValue: A = null.asInstanceOf[A]
    def encodeValue(a: A, out: JsonWriter): Unit = out.writeRawVal(port.write(a).value.getBytes(UTF_8))
    def decodeValue(in: JsonReader, default: A): A =
      port.read(Stored.Json(String(in.readRawValAsBytes(), UTF_8))).fold(e => in.decodeError(e.value), identity)

package com.histmersion
package api.json

import com.github.plokhotnyuk.jsoniter_scala.core.{writeToString, JsonValueCodec}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.Schema
import sttp.tapir.json.jsoniter.TapirJsonJsoniter

import util.json.RawJson
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** Tapir's jsoniter-scala bodies (`jsonBody[A]`), with a refused body said as [[RawJson.decode]] says it: where in the
  * body and why (`findings[0].quote: missing required field`), not jsoniter's byte offset.
  */
@atBoundary(BoundaryKind.Json, "gives HTTP bodies their jsoniter codec, a refused body said by where and why")
object TapirJson extends TapirJsonJsoniter:
  override def jsoniterCodec[T](using JsonValueCodec[T], Schema[T]): JsonCodec[T] =
    sttp.tapir.Codec.json { s =>
      RawJson.decode[T](s) match
        case Right(v)  => Value(v)
        case Left(why) => Error(s, JsonDecodeException(List(JsonError(why, Nil)), IllegalArgumentException(why)))
    }(t => writeToString[T](t))

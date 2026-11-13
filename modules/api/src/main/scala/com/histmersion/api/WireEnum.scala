package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import sttp.tapir.{Schema, Validator}

import util.json.JsonCodec

object WireEnum:
  def codec[E <: reflect.Enum](using values: WireEnumValues[E]): JsonValueCodec[E] =
    JsonCodec.text[E](
      e => values.wire(e).value,
      s => values.fromWire(WireEnumValues.Wire(s)).toRight(s"unknown value: $s")
    )
  def key[E <: reflect.Enum](using values: WireEnumValues[E]): JsonKeyCodec[E] =
    JsonCodec.key[E](
      e => values.wire(e).value,
      s => values.fromWire(WireEnumValues.Wire(s)).toRight(s"unknown value: $s")
    )
  def schema[E](using values: WireEnumValues[E]): Schema[E] =
    Schema.string[E].validate(Validator.enumeration(values.all.toList))

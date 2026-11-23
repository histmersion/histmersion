package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import sttp.tapir.{Schema, Validator}

import util.json.JsonCodec

object WireEnum:
  private def spelled[E](using values: WireEnumValues[E]): E => String = e => values.wire(e).value

  def codec[E <: reflect.Enum](using values: WireEnumValues[E]): JsonValueCodec[E] =
    JsonCodec.text[E](
      spelled,
      s => values.fromWire(WireEnumValues.Wire(s)).toRight(s"unknown value: $s")
    )
  def key[E <: reflect.Enum](using values: WireEnumValues[E]): JsonKeyCodec[E] =
    JsonCodec.key[E](
      spelled,
      s => values.fromWire(WireEnumValues.Wire(s)).toRight(s"unknown value: $s")
    )
  def schema[E](using values: WireEnumValues[E]): Schema[E] =
    Schema.string[E].validate(Validator.enumeration(values.all.toList, spelled.andThen(Some(_))))

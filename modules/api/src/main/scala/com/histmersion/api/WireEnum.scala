package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.{Schema, Validator}
import com.histmersion.values.WireEnumValues

object WireEnum:
  def encoder[E <: reflect.Enum](using values: WireEnumValues[E]): Encoder[E] =
    Encoder.encodeString.contramap(values.wire)
  def decoder[E <: reflect.Enum](using values: WireEnumValues[E]): Decoder[E] =
    Decoder.decodeString.emap(s => values.fromWire(s).toRight(s"unknown value: $s"))
  def schema[E](using values: WireEnumValues[E]): Schema[E] =
    Schema.string[E].validate(Validator.enumeration(values.all.toList))

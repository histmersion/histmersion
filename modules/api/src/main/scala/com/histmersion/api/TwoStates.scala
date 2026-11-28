package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import com.histmersion.lint.{atBoundary, BoundaryKind}

@atBoundary(BoundaryKind.Json, "writes a two-state enum as its case's name and reads the Boolean it replaced")
trait TwoStates[E <: reflect.Enum]:
  def values: Array[E]

  given twoStatesOf: TwoStates[E] = this

  given twoStates: JsonValueCodec[E] = new JsonValueCodec[E]:
    def nullValue: E = null.asInstanceOf[E]
    def decodeValue(in: JsonReader, default: E): E =
      val quoted = in.isNextToken('"'.toByte)
      in.rollbackToken()
      if quoted then
        val name = in.readString(null)
        values.find(_.toString == name).getOrElse(in.decodeError(s"unknown value: $name"))
      else if in.readBoolean() then values(0)
      else values(1)
    def encodeValue(x: E, out: JsonWriter): Unit = out.writeVal(x.toString)

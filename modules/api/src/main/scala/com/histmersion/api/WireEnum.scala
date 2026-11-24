package com.histmersion
package api

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import sttp.tapir.{Schema, Validator}

import util.json.JsonCodec
import com.histmersion.lint.{BoundaryKind, FeaturePlace, atBoundary, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@atBoundary(BoundaryKind.Json, "encodes and decodes an enum as its wire string")
@movesTo(FeaturePlace.UtilCommon)
object WireEnum:
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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

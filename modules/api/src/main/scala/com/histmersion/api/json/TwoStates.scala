package com.histmersion
package api.json

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

trait TwoStates[E <: reflect.Enum]:
  def values: Array[E]

  given twoStatesOf: TwoStates[E] = this

  given twoStates: JsonValueCodec[E] = NewtypeCodecs.twoStates(values)

package com.histmersion

/** The companion of a two-case enum that replaced a Boolean newtype: its cases in order, the first the one a `true`
  * stood for. Codec-free, so every layer can use it: the api's `TwoStates` adds the JSON codec of a wire enum, the api's
  * `NewtypeCodecs.twoCases` lifts it at the JSON boundary for an enum of the layers below (the domain has no codecs),
  * and the database's `NewtypeColumns.twoStates` reads and writes it as the `boolean` column it was.
  */
trait TwoCases[E <: reflect.Enum]:
  def values: Array[E]

  given twoCasesOf: TwoCases[E] = this

package com.histmersion.values

/** How an enum vocabulary is spelled where it leaves the program — on the wire and in the store: every value, its
  * spelling, and the value a spelling names. No codec: api builds its circe and tapir instances from it (`WireEnum`),
  * db its column codecs.
  */
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): String
  def fromWire(s: String): Option[E]

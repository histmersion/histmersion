package com.histmersion
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): com.histmersion.WireEnumValues.WireResult
  def fromWire(s: com.histmersion.WireEnumValues.FromWireS): Option[E]

object WireEnumValues:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

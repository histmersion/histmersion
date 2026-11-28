package com.histmersion
package fixture.wire

trait TwoStates[E <: reflect.Enum]:
  def values: Array[E]
  def first: E = values(0)

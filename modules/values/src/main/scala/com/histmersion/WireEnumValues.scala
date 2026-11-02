package com.histmersion
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): String
  def fromWire(s: String): Option[E]

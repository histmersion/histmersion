package com.histmersion.api

/** A wire vocabulary's spellings: every value, how one is written, and how one is read back. The api's own copy of the
  * kernel's (the api names no package outside `com.histmersion.api`).
  */
trait WireEnumValues[E]:
  def all: Array[E]
  def wire(e: E): String
  def fromWire(s: String): Option[E]

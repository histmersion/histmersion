package com.histmersion.domain.research

import com.histmersion.WireEnumValues
enum DraftMode(val wire: String):
  case Create extends DraftMode("create")
  case Extend extends DraftMode("extend")

object DraftMode:
  def parse(s: String): Option[DraftMode] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[DraftMode]:
    def all: Array[DraftMode] = DraftMode.values
    def wire(e: DraftMode): String = e.wire
    def fromWire(s: String): Option[DraftMode] = parse(s)

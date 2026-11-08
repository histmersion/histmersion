package com.histmersion
package api.catalogue
import com.histmersion.api.given
object DatingMethod:

  val all: List[EventDating.Method] = List(
    EventDating.Method("dendrochronology"),
    EventDating.Method("radiocarbon"),
    EventDating.Method("stratigraphy"),
    EventDating.Method("textual"),
    EventDating.Method("astronomical")
  )

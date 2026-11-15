package com.histmersion
package util.time

import java.time.{Instant, LocalDate, ZoneOffset}

/** The JDK's time values compare with `==` and `!=`, also under `-language:strictEquality`: a day, an instant and a
  * zone offset are values (equal when they name the same day, instant or offset). The one instance of each, imported by
  * name (`TimeEquality.dayEquality`, …) where such values are compared.
  */
object TimeEquality:
  given dayEquality: CanEqual[LocalDate, LocalDate] = CanEqual.derived
  given instantEquality: CanEqual[Instant, Instant] = CanEqual.derived
  given offsetEquality: CanEqual[ZoneOffset, ZoneOffset] = CanEqual.derived

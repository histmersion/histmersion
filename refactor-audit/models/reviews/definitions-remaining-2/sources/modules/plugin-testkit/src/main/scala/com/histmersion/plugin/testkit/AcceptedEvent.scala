package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One event as the server would store it: the catalogue's record, the kind in the server's spelling, and its dating
  * after the server derived it (a stated year and its ±, held inside the present and the catalogue's floor).
  */
final case class AcceptedEvent(
    event: CatalogueEvent,
    kind: AcceptedEvent.Kind,
    notBefore: Option[Year],
    beginBy: Option[Year],
    endFrom: Option[Year],
    notAfter: Option[Year]
)

object AcceptedEvent:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

package com.histmersion
package plugin.testkit

import com.histmersion.plugin.*
import com.histmersion.api.catalogue.CatalogRecord

/** One event as the server would store it: the catalogue's record, the kind in the server's spelling, and its dating
  * after the server derived it (a stated year and its ±, held inside the present and the catalogue's floor).
  */
final case class AcceptedEvent(
    event: CatalogueEvent,
    kind: CatalogRecord.Kind,
    notBefore: Option[Year],
    beginBy: Option[Year],
    endFrom: Option[Year],
    notAfter: Option[Year]
)

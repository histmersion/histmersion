package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One search of a media collection for one place, as the server's import runs it: through the collection's polite
  * client, the records judged, each record the server would keep completed.
  *
  * @param failure
  *   why the search itself failed (an exception is the plugin's failure, as on the server)
  */
final case class MediaReport(
    verdicts: List[MediaVerdict],
    failure: Option[String],
    log: List[LogLine],
    requests: List[String]
):
  def kept: List[MediaVerdict] = verdicts.filter(_.refused.isEmpty)

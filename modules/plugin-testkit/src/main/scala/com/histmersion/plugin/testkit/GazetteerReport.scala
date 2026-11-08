package com.histmersion
package plugin.testkit

import com.histmersion.plugin.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** One import of a gazetteer source, read as the server reads it: the download through the server's gazetteer client,
  * each place checked as the server stores it.
  *
  * @param accepted
  *   the places the server would store, in the order it stores them
  * @param refused
  *   the places it would refuse, with the reason (no id, a shape that is not JSON, …)
  * @param failure
  *   why the source answered no dataset at all (its download failed, its body was not one)
  */
final case class GazetteerReport(
    accepted: List[AcceptedPlace],
    refused: List[GazetteerReport.Refused],
    failure: Option[Problem],
    log: List[LogLine],
    requests: List[PluginHarness.Request]
)

object GazetteerReport:
  type Refused = Refused.T
  object Refused extends Newtype[String, Pure]

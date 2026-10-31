package com.histmersion.plugin.testkit

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
    refused: List[String],
    failure: Option[String],
    log: List[LogLine],
    requests: List[String]
)

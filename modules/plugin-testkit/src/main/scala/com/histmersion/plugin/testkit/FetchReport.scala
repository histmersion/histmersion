package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One fetch of one work through the plugin's fetchers, run as the server runs its acquisition chain: the legal gate,
  * an exception read as a miss, a "PDF" that is not one read as no copy, one memo for the work.
  *
  * @param answers
  *   what each fetcher that ran answered, when none had a copy
  * @param log
  *   what the plugin and the chain logged during the fetch
  * @param requests
  *   every request the fetch made (`GET <url>`, secrets shown as `{secret}`)
  */
final case class FetchReport(
    outcome: FetchOutcome,
    answers: List[FetcherAnswer],
    log: List[LogLine],
    requests: List[PluginHarness.Request]
):
  /** The file found, if any. */
  def file: Option[FetchedFile] = outcome match
    case FetchOutcome.Found(_, f, _) => Some(f)
    case _                           => None

  /** The licence the copy is published under: what the fetcher stated on the file, else the first line it logged about
    * a licence (the server records the first and logs the second).
    */
  def licence: Option[com.histmersion.domain.Licence.Id] =
    file
      .flatMap(_.licence)
      .map(l => com.histmersion.domain.Licence.Id(l.value))
      .orElse(log.collectFirst {
        case l if !l.source.value.startsWith("acquire") && l.message.value.toLowerCase(java.util.Locale.ROOT).contains("licen") =>
          com.histmersion.domain.Licence.Id(l.message.value)
      })

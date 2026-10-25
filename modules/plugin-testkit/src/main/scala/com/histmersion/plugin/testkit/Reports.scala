package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

import java.nio.file.Path

/** One line the server would have logged.
  *
  * @param source the plugin's id for what the plugin logged through its `PluginLog`; `acquire: <fetcher>`
  *               lines are the acquisition chain's, `media`/`plugins` the host's own
  * @param warn   logged as a warning */
final case class LogLine(source: String, warn: Boolean, message: String):
  override def toString: String = s"${if warn then "WARN" else "INFO"} [$source] $message"

/** What the plugin registered, by extension point, each under the id the server lists it by. */
final case class Contributions(
    sourceFetchers: List[String],
    mediaCollections: List[String],
    eventCatalogues: List[String],
    aiProviders: List[String],
    secretVaults: List[String]):
  /** As `histmersion plugin add` and `histmersion about` show them: `source-fetcher:oapen`, … */
  def provides: List[String] =
    sourceFetchers.map("source-fetcher:" + _) ++ mediaCollections.map("media-collection:" + _) ++
      eventCatalogues.map("event-catalogue:" + _) ++ aiProviders.map("ai-provider:" + _) ++
      secretVaults.map("secret-vault:" + _)

/** What the acquisition chain made of one work, after the server's own checks of each answer. */
enum FetchOutcome:
  /** A copy: the fetcher that found it, the file as the server would store it, and where the harness
    * wrote it when asked to. */
  case Found(fetcher: String, file: FetchedFile, savedTo: Option[Path])
  /** No fetcher had a copy; each one's reason, as the ingest report lists them. */
  case Miss(why: String)
  /** A fetcher knows of a copy it may not take (a lending-only item, a bot challenge, no open licence). */
  case Refused(why: String)

/** One fetcher's answer when it had no copy: `refused` (a copy it may not take) or a miss. */
final case class FetcherAnswer(fetcher: String, refused: Boolean, why: String)

/** One fetch of one work through the plugin's fetchers, run as the server runs its acquisition chain:
  * the legal gate, an exception read as a miss, a "PDF" that is not one read as no copy, one memo for
  * the work.
  *
  * @param answers  what each fetcher that ran answered, when none had a copy
  * @param log      what the plugin and the chain logged during the fetch
  * @param requests every request the fetch made (`GET <url>`, secrets shown as `{secret}`) */
final case class FetchReport(outcome: FetchOutcome, answers: List[FetcherAnswer], log: List[LogLine],
    requests: List[String]):
  /** The file found, if any. */
  def file: Option[FetchedFile] = outcome match
    case FetchOutcome.Found(_, f, _) => Some(f)
    case _                           => None

  /** The licence the copy is published under: what the fetcher stated on the file, else the first line
    * it logged about a licence (the server records the first and logs the second). */
  def licence: Option[String] =
    file.flatMap(_.licence).map(_.value).orElse(log.collectFirst {
      case l if !l.source.startsWith("acquire") && l.message.toLowerCase(java.util.Locale.ROOT).contains("licen") => l.message
    })

/** How the server judges one record a media collection answered, before it ties it to a place.
  *
  * @param key     the record's key, or the reason the collection gave for an unreadable one
  * @param licence the licence as the SERVER reads the statement (`CC-BY-4.0`), when it can
  * @param refused why the server would not keep it (withheld, no credit line, a licence family the
  *                allowlist `media.collections` lacks, no image); `None`: it would be considered for a
  *                place — the server's match rules (names on the map, dates) decide the rest */
final case class MediaVerdict(key: String, item: Option[MediaItem], licence: Option[String], refused: Option[String])

/** One search of a media collection for one place, as the server's import runs it: through the
  * collection's polite client, the records judged, each record the server would keep completed.
  *
  * @param failure why the search itself failed (an exception is the plugin's failure, as on the server) */
final case class MediaReport(verdicts: List[MediaVerdict], failure: Option[String], log: List[LogLine],
    requests: List[String]):
  def kept: List[MediaVerdict] = verdicts.filter(_.refused.isEmpty)

/** One event as the server would store it: the catalogue's record, the kind in the server's spelling,
  * and its dating after the server derived it (a stated year and its ±, held inside the present and the
  * catalogue's floor). */
final case class AcceptedEvent(event: CatalogueEvent, kind: String, notBefore: Option[Year], beginBy: Option[Year],
    endFrom: Option[Year], notAfter: Option[Year])

/** One import of an event catalogue, read as the server reads it: its parts, each part page by page.
  *
  * @param pages    each page read (`part → events on it`, or why it could not be read)
  * @param accepted the events the server would store
  * @param refused  the records it would refuse, with the reason (no id, a dating that runs backwards, …) */
final case class CatalogueReport(parts: List[String], pages: List[(String, Either[String, Int])],
    accepted: List[AcceptedEvent], refused: List[String], failure: Option[String], log: List[LogLine],
    requests: List[String])

/** One model call to an AI provider as the server makes it: the provider's own check of the model
  * first, then the call — an exception or a `null` being the provider's failure.
  *
  * @param turn `None` when the check said no */
final case class AiReport(check: Either[String, Unit], turn: Option[Either[AiFailure, AiTurn]], log: List[LogLine])

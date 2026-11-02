package com.histmersion.pluginhost

import com.histmersion.config.MediaConfig
import com.histmersion.domain.Attribution
import com.histmersion.domain.{Licence, LicenceFamily}
import com.histmersion.domain.media.imports.{LicencePolicy, MediaRecord}
import com.histmersion.plugin.*

import scala.util.control.NonFatal

/** A plugin's media collection as the host reads it: its polite HTTP, its answers guarded, each item turned into the
  * host's [[MediaRecord]] with its licence READ BY THE HOST, and the host's judgement of whether a record may be kept
  * at all. Pure but for [[http]].
  */
object PluginMedia:

  /** A collection run's polite client: the collection's own pace, the media politeness otherwise. */
  def polite(
      http: HttpTransport,
      media: MediaConfig,
      collection: MediaCollection,
      sleep: Long => Unit = ms => Thread.sleep(ms),
      log: HostLog = HostLog.scribe
  ): Polite =
    Polite(http, media.copy(requestIntervalMs = collection.requestIntervalMs), sleep = sleep, log = log)

  /** Pure: why a record may not be kept whatever place it names — a withheld record, rights the host cannot read, a
    * licence family not in `allow` (the collection's allowlist), no image — or `None`.
    */
  def refusal(rec: MediaRecord, allow: LicencePolicy, provider: String): Option[String] =
    rec.rights match
      case _ if rec.withheld.isDefined          => rec.withheld
      case Left(why)                            => Some(why)
      case Right(r) if !allow.allows(r.licence) =>
        Some(s"licence ${r.licence.family.wire} not allowed for $provider")
      case Right(_) if rec.imageUrl.isEmpty => Some("no image")
      case Right(_)                         => None

  /** The run's polite client as the plugin's HTTP: its pace, its User-Agent, Retry-After honoured, a challenge the end
    * of the request.
    */
  def http(polite: Polite): PluginHttp = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = polite.get(url, headers).map(_.body)

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[String, A]): Either[String, A] =
    try Option(a).getOrElse(Left("the plugin answered nothing"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** Pure: the record a plugin's item becomes; its rights read by the HOST ([[rights]]). A period its type would refuse
    * makes the record unreadable (its rights a `Left` naming the value, so it is never kept), as does a record the host
    * cannot read at all (a plugin built against another SPI).
    */
  def record(it: MediaItem): MediaRecord =
    val period =
      try SpiValues.optional(it.period)(SpiValues.periodOf)
      catch
        case NonFatal(e) =>
          Left(
            s"a period the host cannot read (${e.getClass.getSimpleName}) — is the plugin " +
              s"built against plugin SPI ${Spi.Version}?"
          )
    val stated = Option(it.rights).getOrElse(Left("no rights statement"))
    MediaRecord(
      it.key.trim,
      it.title.trim,
      it.pageUrl.trim,
      it.imageUrl.map(_.trim).filter(_.nonEmpty),
      it.thumbUrl.map(_.trim).filter(_.nonEmpty),
      it.originalUrl.map(_.trim).filter(_.nonEmpty),
      period.left.map(why => s"${it.key.trim}: $why").flatMap(_ => stated.flatMap(rights)),
      it.geography,
      period.toOption.flatten,
      it.ids,
      it.withheld,
      Option(it.role).flatten.map(_.trim).filter(_.nonEmpty)
    )

  /** Pure: what a provider's licence statement means, read by the host as every built-in collection's is ([[Licence]]):
    * the statement as an id or short name, or as a Creative Commons URL; the licence URL when the statement says
    * nothing known. A statement it cannot read is `unknown` — never stored, whatever an allowlist says. A record must
    * carry a credit line. The family decided here is what the allowlist is checked against: the plugin states, the host
    * judges.
    */
  def rights(r: com.histmersion.plugin.MediaRights): Either[String, Attribution] =
    val stated =
      try Option(r.licence).map(SpiValues.licenceOf).getOrElse("").trim
      catch case NonFatal(_) => ""
    val byStatement = Licence.ofStatement(stated)
    val licence =
      if byStatement.family != LicenceFamily.Unknown then byStatement
      else r.licenceUrl.map(Licence.ofUrl).filter(_.family != LicenceFamily.Unknown).getOrElse(byStatement)
    val credit = Option(r.credit).getOrElse("").trim
    if credit.isEmpty then Left("no credit line")
    else
      Right(
        Attribution(
          licence,
          r.licenceUrl.map(_.trim).filter(_.nonEmpty),
          r.author.map(_.trim).filter(_.nonEmpty),
          credit
        )
      )

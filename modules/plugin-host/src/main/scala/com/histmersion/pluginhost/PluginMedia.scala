package com.histmersion
package pluginhost

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.optics.syntax.*

import com.histmersion.config.MediaConfig
import com.histmersion.domain.Attribution
import com.histmersion.domain.CoreQuota
import com.histmersion.domain.HttpPolicy
import com.histmersion.domain.Licence
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.media.imports.{LicencePolicy, MediaRecord}
import com.histmersion.plugin.*

import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure

/** A plugin's media collection as the host reads it: its polite HTTP, its answers guarded, each item turned into the
  * host's [[MediaRecord]] with its licence READ BY THE HOST, and the host's judgement of whether a record may be kept
  * at all. Pure but for [[http]].
  */
object PluginMedia:
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]

  /** A collection run's polite client: the collection's own pace, the media politeness otherwise. */
  def polite(
      http: HttpTransport,
      media: MediaConfig,
      collection: MediaCollection,
      sleep: HttpPolicy.Step.Wait.Ms => Unit = ms => Thread.sleep(ms.value),
      log: HostLog = HostLog.scribe
  ): Polite =
    new Polite(
      http,
      media.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(collection.requestIntervalMs.value)),
      sleep = ms => sleep(HttpPolicy.Step.Wait.Ms(ms.value)),
      log = log
    )

  /** Pure: why a record may not be kept whatever place it names — a withheld record, rights the host cannot read, a
    * licence family not in `allow` (the collection's allowlist), no image — or `None`.
    */
  def refusal(rec: MediaRecord, allow: LicencePolicy, provider: Provider): Option[Refusal] =
    rec.rights match
      case _ if rec.withheld.isDefined =>
        rec.withheld.map(w => Refusal.Withheld(Problem.Unclassified(Legacy.Text(w.value))))
      case Left(why)                            => Some(Refusal.Unreadable(why))
      case Right(r) if !allow.allows(r.licence) => Some(Refusal.LicenceNotAllowed(r.licence.family, provider))
      case Right(_) if rec.imageUrl.isEmpty     => Some(Refusal.NoImage)
      case Right(_)                             => None

  /** Why a record may not be kept. */
  enum Refusal derives CanEqual:
    /** The plugin withheld it, with its reason. */
    case Withheld(why: Problem)

    /** The host cannot read its rights statement. */
    case Unreadable(why: Problem)

    /** Its licence family is not in the collection's allowlist. */
    case LicenceNotAllowed(family: Licence.Family, provider: Provider)

    /** It has no image to keep. */
    case NoImage

    /** The sentence for a person. */
    def problem: Problem = this match
      case Withheld(why)                     => why
      case Unreadable(why)                   => why
      case LicenceNotAllowed(family, source) =>
        Problem.Unclassified(Legacy.Text(s"licence ${family.wire} not allowed for $source"))
      case NoImage => Problem.Unclassified(Legacy.Text("no image"))

  /** The run's polite client as the plugin's HTTP: its pace, its User-Agent, Retry-After honoured, a challenge the end
    * of the request.
    */
  def http(polite: Polite): Plugin.Http = new Plugin.Http:
    def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
      polite
        .get(
          HttpTransport.Url(url.value),
          headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
        )
        .map(a => Plugin.HttpResponse.Body(a.body.value))

  /** A plugin's answer to a request, or what it threw as a failure of that call (one that needed no answer). */
  def guardHttp[A](a: => Either[Plugin.Http.Failure, A]): Either[Plugin.Http.Failure, A] =
    try
      Option(a).getOrElse(
        Left(Plugin.Http.Failure.Transport(Problem.Unclassified(Legacy.Text("the plugin answered nothing"))))
      )
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             =>
        Left(
          Plugin.Http.Failure.Transport(
            Problem.Unclassified(Legacy.Text(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))
          )
        )

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[Problem, A]): Either[Problem, A] =
    try Option(a).getOrElse(Left(Problem.Unclassified(Legacy.Text("the plugin answered nothing"))))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             =>
        Left(Problem.Unclassified(Legacy.Text(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}")))

  /** Pure: the record a plugin's item becomes; its rights read by the HOST ([[rights]]). A period its type would refuse
    * makes the record unreadable (its rights a `Left` naming the value, so it is never kept), as does a record the host
    * cannot read at all (a plugin built against another SPI).
    */
  def record(it: MediaItem): MediaRecord =
    val key = it.key.value.trim
    val period: Either[Problem, Option[com.histmersion.Period]] =
      try SpiValues.optional(it.period)(SpiValues.periodOf)
      catch
        case NonFatal(e) =>
          Left(
            Problem.Unclassified(
              Legacy.Text(
                s"a period the host cannot read (${e.getClass.getSimpleName}) — is the plugin " +
                  s"built against plugin SPI ${Spi.Version}?"
              )
            )
          )
    val stated: Either[Problem, MediaRights] =
      Option(it.rights)
        .getOrElse(Left(Problem.Unclassified(Legacy.Text("no rights statement"))))
    MediaRecord(
      MediaRecord.Key(key),
      MediaRecord.Title(it.title.value.trim),
      MediaRecord.PageUrl(it.pageUrl.value.trim),
      it.imageUrl.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.ImageUrl(_)),
      it.thumbUrl.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.ThumbUrl(_)),
      it.originalUrl.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.OriginalUrl(_)),
      period.left
        .map(why => Problem.Unclassified(Legacy.Text(s"$key: ${why.sentence}")))
        .flatMap(_ => stated.flatMap(rights)),
      it.geography.map((field, value) =>
        MediaRecord.Geography.Field(field.value) -> MediaRecord.Geography.Value(value.value)
      ),
      period.toOption.flatten,
      it.ids.map((scheme, id) => ExternalIds.Scheme(scheme.value) -> ExternalIds.ExternalId(id.value)),
      it.withheld.map(w => MediaRecord.Withheld(w.value)),
      Option(it.role).flatten.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.Role(_))
    )

  /** Pure: what a provider's licence statement means, read by the host as every built-in collection's is ([[Licence]]):
    * the statement as an id or short name, or as a Creative Commons URL; the licence URL when the statement says
    * nothing known. A statement it cannot read is `unknown` — never stored, whatever an allowlist says. A record must
    * carry a credit line. The family decided here is what the allowlist is checked against: the plugin states, the host
    * judges.
    */
  def rights(r: MediaRights): Either[Problem, Attribution] =
    val stated =
      try Option(r.licence).map(l => SpiValues.licenceOf(l).value).getOrElse("").trim
      catch case NonFatal(_) => ""
    val byStatement = Licence.ofStatement(Licence.Statement(stated))
    val licence =
      if byStatement.family != Licence.Family.Unknown then byStatement
      else
        r.licenceUrl
          .map(u => Licence.ofUrl(Licence.Url(u.value)))
          .filter(_.family != Licence.Family.Unknown)
          .getOrElse(byStatement)
    val credit = Option(r.credit).map(_.value).getOrElse("").trim
    if credit.isEmpty then Left(Problem.Unclassified(Legacy.Text("no credit line")))
    else
      Right(
        Attribution(
          licence,
          r.licenceUrl.map(_.value.trim).filter(_.nonEmpty).map(Attribution.LicenceUrl(_)),
          r.author.map(_.value.trim).filter(_.nonEmpty).map(Attribution.Author(_)),
          Attribution.Credit(credit)
        )
      )

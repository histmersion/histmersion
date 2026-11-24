package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
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
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** A plugin's media collection as the host reads it: its polite HTTP, its answers guarded, each item turned into the
  * host's [[MediaRecord]] with its licence READ BY THE HOST, and the host's judgement of whether a record may be kept
  * at all. Pure but for [[http]].
  */
@atBoundary(BoundaryKind.Spi, "turns a plugin's media items into the host's media records")
object PluginMedia:
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]

  /** A collection run's polite client: the collection's own pace, the media politeness otherwise. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def refusal(rec: MediaRecord, allow: LicencePolicy, provider: Provider): Option[Refusal] =
    rec.rights match
      case _ if rec.withheld.isDefined =>
        rec.withheld.map(w => Refusal.Withheld(External.PluginSaid.of(w.value)))
      case Left(why)                            => Some(Refusal.Unreadable(why))
      case Right(r) if !allow.allows(r.licence) => Some(Refusal.LicenceNotAllowed(r.licence.family, provider))
      case Right(_) if rec.imageUrl.isEmpty     => Some(Refusal.NoImage)
      case Right(_)                             => None

  /** Why a record may not be kept. */
  enum Refusal:
    /** The plugin withheld it, with its reason. */
    case Withheld(why: External.PluginSaid)

    /** The host cannot read its rights statement. */
    case Unreadable(why: Problem)

    /** Its licence family is not in the collection's allowlist. */
    case LicenceNotAllowed(family: Licence.Family, provider: Provider)

    /** It has no image to keep. */
    case NoImage

    /** The refusal of `item`, a record of the collection `provider`, as a problem. */
    def problem(provider: Provider, item: MediaRecord.Key): Problem =
      val plugin = Problem.Plugin.Id(s"$provider")
      this match
        case Withheld(why)                     => Problem.Plugin.Said(plugin, Problem.Plugin.Claim.Rejected, why)
        case Unreadable(why)                   => why
        case LicenceNotAllowed(family, source) =>
          Problem.Plugin.Rejected(
            Problem.Plugin.Id(s"$source"),
            Problem.Raw(s"$item"),
            Problem.Plugin.Refusal.LicenceNotAllowed(Problem.Raw(s"${family.wire}"))
          )
        case NoImage => Problem.Plugin.Rejected(plugin, Problem.Raw(s"$item"), Problem.Plugin.Refusal.NoImage)

  /** The run's polite client as the plugin's HTTP: its pace, its User-Agent, Retry-After honoured, a challenge the end
    * of the request.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def http(polite: Polite): Plugin.Http = new Plugin.Http:
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def get(url: Plugin.Http.Url, headers: Map[Header.Name, Header.Value]) =
      polite
        .get(
          HttpTransport.Url(url.value),
          headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
        )
        .map(a => Plugin.HttpResponse.Body(a.body.value))

  /** A plugin's answer to a request, or what it threw as a failure of that call (one that needed no answer). */
  def guardHttp[A](a: => Either[Problem, A]): Either[Problem, A] =
    try Option(a).getOrElse(Left(answeredNothing))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(threw(e))

  /** A plugin answered `null` where the SPI promises an answer. */
  val answeredNothing: Problem = Problem.Invalid.Missing.here(Problem.Field("the plugin's answer"))

  /** A plugin's call threw `e`: logged under its problem's id. */
  def threw(e: Throwable): Problem = Problem.from(e, Problem.Where("the plugin"), Problem.Internal.Id.mint())

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[Problem, A]): Either[Problem, A] =
    try Option(a).getOrElse(Left(answeredNothing))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(threw(e))

  /** Pure: the record a plugin's item becomes; its rights read by the HOST ([[rights]]). A period its type would refuse
    * makes the record unreadable (its rights a `Left` naming the value, so it is never kept), as does a record the host
    * cannot read at all (a plugin built against another SPI).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def record(it: MediaItem): MediaRecord =
    val key = it.key.value.trim
    val period: Either[Problem, Option[com.histmersion.Period]] =
      try SpiValues.optional(it.period)(SpiValues.periodOf)
      catch
        case NonFatal(e) =>
          Left(Problem.from(e, Problem.Where(s"reading the period of $key"), Problem.Internal.Id.mint()))
    val stated: Either[Problem, MediaRights] =
      Option(it.rights)
        .getOrElse(Left(Problem.Invalid.Missing(Problem.Field("rights"), Vector(Problem.Field(key)))))
    MediaRecord(
      MediaRecord.Key(key),
      MediaRecord.Title(it.title.value.trim),
      MediaRecord.PageUrl(it.pageUrl.value.trim),
      it.imageUrl.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.ImageUrl(_)),
      it.thumbUrl.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.ThumbUrl(_)),
      it.originalUrl.map(_.value.trim).filter(_.nonEmpty).map(MediaRecord.OriginalUrl(_)),
      period.left
        .map(_.within(Problem.Field(key)))
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
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
    if credit.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("credit")))
    else
      Right(
        Attribution(
          licence,
          r.licenceUrl.map(_.value.trim).filter(_.nonEmpty).map(Attribution.LicenceUrl(_)),
          r.author.map(_.value.trim).filter(_.nonEmpty).map(Attribution.Author(_)),
          Attribution.Credit(credit)
        )
      )

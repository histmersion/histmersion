package com.histmersion
package pluginhost
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.OpenSide
import com.histmersion.api.catalogue.{Period as ApiPeriod, Place}
import com.histmersion.api.instance.Version
import com.histmersion.config.PlacesConfig
import com.histmersion.plugin.*

import scala.util.control.NonFatal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.HttpPolicy
import com.histmersion.domain.Attribution
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** A plugin's gazetteer source as the host reads it: the client its download goes through, its answer guarded, and each
  * of its places checked and turned into the host's [[PlaceInput]]. Pure but for [[http]].
  */
@atBoundary(BoundaryKind.Spi, "turns a plugin's gazetteer places into the host's place inputs")
object PluginGazetteers:
  type FieldName = FieldName.T
  object FieldName extends Newtype[String, Pure]
  type Warning = Warning.T
  object Warning extends Newtype[String, Pure]

  /** The User-Agent of a gazetteer download (the outbound policy makes sure it names no person). */
  val UserAgent: HttpPolicy.UserAgent = HttpPolicy.UserAgent(s"Histmersion/${Version.version} (gazetteer import)")

  /** The wire of a gazetteer download: `java.net.http` over HTTP/1.1, redirects followed within a scheme, the answer
    * waited for `places.fetch-timeout-seconds` and the connection `places.fetch-connect-timeout-seconds`; the body read
    * whole (a dataset is one file).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def transport(places: PlacesConfig): HttpTransport =
    JavaHttpTransport(
      HttpTransport.TimeoutSeconds(places.fetchTimeoutSeconds.value),
      Some(JavaHttpTransport.ConnectSeconds(places.fetchConnectTimeoutSeconds.value))
    )

  /** The client the host lends a source's [[GazetteerSource.fetch]]: the one outbound policy with the gazetteer
    * import's User-Agent over `transport`. Its `fetch` asks once and answers as it came; its `get` asks again after a
    * slow-down, as every plugin's.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def http(transport: HttpTransport, log: HostLog = HostLog.scribe): Plugin.Http =
    PolicyHttp(UserAgent, transport, log = log).plugin

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[Problem, A]): Either[Problem, A] =
    PluginMedia.guard(a)

  /** Every place `source` answers, each checked and converted, in its order, and the ones refused with the reason.
    * `Left` when the source answered no dataset at all (its download failed, its body was not one).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def places(source: GazetteerSource, http: Plugin.Http): Either[Problem, (List[Place.Input], List[Warning])] =
    guard(source.fetch(http)).map { got =>
      val credit = guard(Right(source.rights)).toOption.flatMap(r => Option(r).flatMap(r => Option(r.credit)))
      val read = got.map(p => input(p, credit.map(c => Attribution.Credit(c.value)).getOrElse(Attribution.Credit(""))))
      (read.collect { case Right(in) => in }, read.collect { case Left(why) => Warning(why.sentence) })
    }

  /** Pure: the host's place of a plugin's, or why it is refused — no id or kind, a shape or properties that are not
    * JSON, a period the host cannot read. Its source is its own, else the dataset's credit; nothing else is changed.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def input(p: GazetteerPlace, credit: Attribution.Credit): Either[Problem, Place.Input] =
    try
      if p == null then Left(Problem.Invalid.Missing.here(Problem.Field("place")))
      else
        val id = Option(p.id).map(_.value).getOrElse("")
        def named(why: Problem): Problem = why.within(Problem.Field(id))
        if id.trim.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("id")))
        else if Option(p.kind).forall(_.value.trim.isEmpty) then
          Left(named(Problem.Invalid.Missing.here(Problem.Field("kind"))))
        else
          for
            label <- Option(p.label).toRight(named(Problem.Invalid.Missing.here(Problem.Field("label"))))
            geometry <- json(Option(p.geometry).flatten.map(_.value), FieldName("geometry")).left.map(named)
            properties <- json(Option(p.properties).flatten.map(_.value), FieldName("properties")).left.map(named)
            period <- span(Option(p.period).flatten, Option(p.open).flatten).left.map(named)
            names <- all(Option(p.names).getOrElse(Nil))(name).left.map(named)
            relations <- all(Option(p.relations).getOrElse(Nil))(relation).left.map(named)
          yield
            val source =
              Option(p.source).flatten.map(_.value).orElse(Option(credit).map(_.value.trim).filter(_.nonEmpty))
            Place.Input(
              Place(
                Place.Id(id),
                Place.Label(label.value),
                Place.KindValue(p.kind.value),
                geometry.map(Place.Geometry(_)),
                period._1,
                source.map(Place.Source(_)),
                properties.map(Place.Properties(_)),
                period._2
              ),
              names,
              relations
            )
    catch
      case NonFatal(e) => Left(Problem.from(e, Problem.Where("reading a plugin's place"), Problem.Internal.Id.mint()))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def name(n: GazetteerName): Either[Problem, Place.Name] =
    if n == null || Option(n.name).isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("name")))
    else
      span(Option(n.period).flatten, Option(n.open).flatten).map((period, open) =>
        Place.Name(
          Place.Name.Value(n.name.value),
          Place.Name.Lang(Option(n.lang).map(_.value).getOrElse("")),
          period,
          open
        )
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def relation(r: GazetteerRelation): Either[Problem, Place.Relation] =
    if r == null then Left(Problem.Invalid.Missing.here(Problem.Field("relation")))
    else if Option(r.childId).forall(_.value.trim.isEmpty) then
      Left(Problem.Invalid.Missing(Problem.Field("childId"), Vector(Problem.Field("relation"))))
    else if Option(r.parentId).forall(_.value.trim.isEmpty) then
      Left(Problem.Invalid.Missing(Problem.Field("parentId"), Vector(Problem.Field("relation"))))
    else if Option(r.kind).forall(_.value.trim.isEmpty) then
      Left(
        Problem.Invalid
          .Missing(Problem.Field("kind"), Vector(Problem.Field("relation"), Problem.Field(s"${r.parentId}")))
      )
    else
      span(Option(r.period).flatten, Option(r.open).flatten).map((period, open) =>
        Place.Relation(
          Place.Relation.ChildId(r.childId.value),
          Place.Relation.ParentId(r.parentId.value),
          Place.Relation.Kind(r.kind.value),
          period,
          open
        )
      )

  /** A plugin's period and open side as the host's: the period's ends checked again ([[SpiValues.periodOf]]); an open
    * side without a period is refused (it would say nothing).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def span(
      period: Option[Period],
      open: Option[GazetteerOpenSide]
  ): Either[Problem, (Option[ApiPeriod], Option[OpenSide])] =
    for
      p <- SpiValues.optional(period)(SpiValues.periodOf)
      side <- (p, open) match
        case (None, Some(_)) => Left(Problem.Invalid.Missing(Problem.Field("period"), Vector(Problem.Field("open"))))
        case _               => Right(open.map(openSide))
    yield (
      p.map(v =>
        ApiPeriod(
          com.histmersion.api.catalogue.Year(v.start.value.toLong),
          com.histmersion.api.catalogue.Year(v.end.value.toLong)
        )
      ),
      side
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def openSide(o: GazetteerOpenSide): OpenSide = o match
    case GazetteerOpenSide.Start => OpenSide.Start
    case GazetteerOpenSide.End   => OpenSide.End

  /** A JSON text the store keeps as JSON (a shape, the properties): it must be a JSON object. */
  private def json(text: Option[String], what: FieldName): Either[Problem, Option[String]] =
    text match
      case None    => Right(None)
      case Some(t) =>
        RawJson.parse(t) match
          case Right(j) if j.isObject => Right(Some(t))
          case _                      =>
            Left(Problem.Io.Corrupt(Problem.Io.Format.Json, Problem.Where(s"$what"), Problem.Io.Damage.Undecodable))

  /** Every element converted, or the first refusal. */
  private def all[A, B](as: List[A])(f: A => Either[Problem, B]): Either[Problem, List[B]] =
    @annotation.tailrec
    def loop(rest: List[A], acc: List[B]): Either[Problem, List[B]] = rest match
      case Nil    => Right(acc.reverse)
      case h :: t =>
        f(h) match
          case Left(e)  => Left(e)
          case Right(b) => loop(t, b :: acc)
    loop(as, Nil)

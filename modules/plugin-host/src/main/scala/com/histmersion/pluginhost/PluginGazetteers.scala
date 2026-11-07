package com.histmersion.pluginhost

import com.histmersion.api.OpenSide
import com.histmersion.api.catalogue.{Period as ApiPeriod, Place}
import com.histmersion.api.instance.Version
import com.histmersion.config.PlacesConfig
import com.histmersion.plugin.*

import scala.util.control.NonFatal

/** A plugin's gazetteer source as the host reads it: the client its download goes through, its answer guarded, and each
  * of its places checked and turned into the host's [[PlaceInput]]. Pure but for [[http]].
  */
object PluginGazetteers:
  type FieldName = FieldName.T
  object FieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warning = Warning.T
  object Warning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The User-Agent of a gazetteer download (the outbound policy makes sure it names no person). */
  val UserAgent: com.histmersion.domain.HttpPolicy.UserAgent = com.histmersion.domain.HttpPolicy.UserAgent(s"Histmersion/${Version.version} (gazetteer import)")

  /** The wire of a gazetteer download: `java.net.http` over HTTP/1.1, redirects followed within a scheme, the answer
    * waited for `places.fetch-timeout-seconds` and the connection `places.fetch-connect-timeout-seconds`; the body read
    * whole (a dataset is one file).
    */
  def transport(places: PlacesConfig): HttpTransport =
    JavaHttpTransport(HttpTransport.TimeoutSeconds(places.fetchTimeoutSeconds.value), Some(JavaHttpTransport.ConnectSeconds(places.fetchConnectTimeoutSeconds.value)))

  /** The client the host lends a source's [[GazetteerSource.fetch]]: the one outbound policy with the gazetteer
    * import's User-Agent over `transport`. Its `fetch` asks once and answers as it came; its `get` asks again after a
    * slow-down, as every plugin's.
    */
  def http(transport: HttpTransport, log: HostLog = HostLog.scribe): Plugin.Http =
    PolicyHttp(UserAgent, transport, log = log).plugin

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[?, A]): Either[com.histmersion.pluginhost.PluginGazetteers.Problem, A] =
    PluginMedia.guard(a).left.map(e => Problem(e.value))

  /** Every place `source` answers, each checked and converted, in its order, and the ones refused with the reason.
    * `Left` when the source answered no dataset at all (its download failed, its body was not one).
    */
  def places(source: GazetteerSource, http: Plugin.Http): Either[com.histmersion.pluginhost.PluginGazetteers.Problem, (List[Place.Input], List[com.histmersion.pluginhost.PluginGazetteers.Warning])] =
    guard(source.fetch(http)).map { got =>
      val credit = guard(Right(source.rights)).toOption.flatMap(r => Option(r).flatMap(r => Option(r.credit)))
      val read = got.map(p => input(p, credit.map(c => com.histmersion.domain.Attribution.Credit(c.value)).getOrElse(com.histmersion.domain.Attribution.Credit(""))))
      (read.collect { case Right(in) => in }, read.collect { case Left(why) => Warning(why.value) })
    }

  /** Pure: the host's place of a plugin's, or why it is refused — no id or kind, a shape or properties that are not
    * JSON, a period the host cannot read. Its source is its own, else the dataset's credit; nothing else is changed.
    */
  def input(p: GazetteerPlace, credit: com.histmersion.domain.Attribution.Credit): Either[com.histmersion.pluginhost.PluginGazetteers.Problem, Place.Input] =
    try
      if p == null then Left(Problem("a place that is null"))
      else
        val id = Option(p.id).map(_.value).getOrElse("")
        def named(why: Reason): Problem = Problem(s"place '$id': ${why.value}")
        if id.trim.isEmpty then Left(Problem("a place with no id"))
        else if Option(p.kind).forall(_.value.trim.isEmpty) then Left(named(Reason("no kind")))
        else
          for
            label <- Option(p.label).toRight(named(Reason("no label")))
            geometry <- json(Option(p.geometry).flatten.map(_.value), FieldName("geometry")).left.map(named)
            properties <- json(Option(p.properties).flatten.map(_.value), FieldName("properties")).left.map(named)
            period <- span(Option(p.period).flatten, Option(p.open).flatten).left.map(named)
            names <- all(Option(p.names).getOrElse(Nil))(name).left.map(named)
            relations <- all(Option(p.relations).getOrElse(Nil))(relation).left.map(named)
          yield
            val source = Option(p.source).flatten.map(_.value).orElse(Option(credit).map(_.value.trim).filter(_.nonEmpty))
            Place.Input(
              Place(
                com.histmersion.api.catalogue.Place.Id(id),
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
      case NonFatal(e) =>
        Left(
          Problem(
            s"a place the host cannot read (${e.getClass.getSimpleName}) — is the plugin built against plugin SPI " +
              s"${Spi.Version}?"
          )
        )

  private def name(n: GazetteerName): Either[Reason, Place.Name] =
    if n == null || Option(n.name).isEmpty then Left(Reason("a name that is null"))
    else
      span(Option(n.period).flatten, Option(n.open).flatten).map((period, open) =>
        Place.Name(Place.Name.Value(n.name.value), Place.Name.Lang(Option(n.lang).map(_.value).getOrElse("")), period, open)
      )

  private def relation(r: GazetteerRelation): Either[Reason, Place.Relation] =
    if r == null then Left(Reason("a relation that is null"))
    else if Option(r.childId).forall(_.value.trim.isEmpty) || Option(r.parentId).forall(_.value.trim.isEmpty) then
      Left(Reason("a relation without both its places"))
    else if Option(r.kind).forall(_.value.trim.isEmpty) then Left(Reason(s"the relation to '${r.parentId.value}' has no kind"))
    else
      span(Option(r.period).flatten, Option(r.open).flatten).map((period, open) =>
        Place.Relation(Place.Relation.ChildId(r.childId.value), Place.Relation.ParentId(r.parentId.value), Place.Relation.Kind(r.kind.value), period, open)
      )

  /** A plugin's period and open side as the host's: the period's ends checked again ([[SpiValues.periodOf]]); an open
    * side without a period is refused (it would say nothing).
    */
  private def span(
      period: Option[Period],
      open: Option[GazetteerOpenSide]
  ): Either[Reason, (Option[ApiPeriod], Option[OpenSide])] =
    for
      p <- SpiValues.optional(period)(SpiValues.periodOf).left.map(e => Reason(e.value))
      side <- (p, open) match
        case (None, Some(_)) => Left(Reason("an open side without a period"))
        case _               => Right(open.map(openSide))
    yield (
      p.map(v => ApiPeriod(com.histmersion.api.catalogue.Year(v.start.value.toLong), com.histmersion.api.catalogue.Year(v.end.value.toLong))),
      side
    )

  private def openSide(o: GazetteerOpenSide): OpenSide = o match
    case GazetteerOpenSide.Start => OpenSide.Start
    case GazetteerOpenSide.End   => OpenSide.End

  /** A JSON text the store keeps as JSON (a shape, the properties): it must be a JSON object. */
  private def json(text: Option[String], what: FieldName): Either[Reason, Option[String]] =
    text match
      case None    => Right(None)
      case Some(t) =>
        io.circe.parser.parse(t) match
          case Right(j) if j.isObject => Right(Some(t))
          case _                      => Left(Reason(s"its ${what.value} is not a JSON object"))

  /** Every element converted, or the first refusal. */
  private def all[A, B](as: List[A])(f: A => Either[Reason, B]): Either[Reason, List[B]] =
    @annotation.tailrec
    def loop(rest: List[A], acc: List[B]): Either[Reason, List[B]] = rest match
      case Nil    => Right(acc.reverse)
      case h :: t =>
        f(h) match
          case Left(e)  => Left(e)
          case Right(b) => loop(t, b :: acc)
    loop(as, Nil)

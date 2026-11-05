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

  /** The User-Agent of a gazetteer download (the outbound policy makes sure it names no person). */
  type UserAgent = UserAgent.T
  object UserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardResultPart1 = GuardResultPart1.T
  object GuardResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesResultPart1 = PlacesResultPart1.T
  object PlacesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacesResultPart2Part2 = PlacesResultPart2Part2.T
  object PlacesResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputResultPart1 = InputResultPart1.T
  object InputResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InputCredit = InputCredit.T
  object InputCredit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameResultPart1 = NameResultPart1.T
  object NameResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelationResultPart1 = RelationResultPart1.T
  object RelationResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanResultPart1 = SpanResultPart1.T
  object SpanResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonResultPart1 = JsonResultPart1.T
  object JsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonResultPart2 = JsonResultPart2.T
  object JsonResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonText = JsonText.T
  object JsonText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonWhat = JsonWhat.T
  object JsonWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllResultPart1 = AllResultPart1.T
  object AllResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllFResultPart1 = AllFResultPart1.T
  object AllFResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val UserAgent: com.histmersion.pluginhost.PluginGazetteers.UserAgent = s"Histmersion/${Version.version} (gazetteer import)"

  /** The wire of a gazetteer download: `java.net.http` over HTTP/1.1, redirects followed within a scheme, the answer
    * waited for `places.fetch-timeout-seconds` and the connection `places.fetch-connect-timeout-seconds`; the body read
    * whole (a dataset is one file).
    */
  def transport(places: PlacesConfig): HttpTransport =
    JavaHttpTransport(places.fetchTimeoutSeconds, Some(places.fetchConnectTimeoutSeconds))

  /** The client the host lends a source's [[GazetteerSource.fetch]]: the one outbound policy with the gazetteer
    * import's User-Agent over `transport`. Its `fetch` asks once and answers as it came; its `get` asks again after a
    * slow-down, as every plugin's.
    */
  def http(transport: HttpTransport, log: HostLog = HostLog.scribe): Plugin.Http =
    PolicyHttp(UserAgent, transport, log = log).plugin

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[String, A]): Either[com.histmersion.pluginhost.PluginGazetteers.GuardResultPart1, A] = PluginMedia.guard(a)

  /** Every place `source` answers, each checked and converted, in its order, and the ones refused with the reason.
    * `Left` when the source answered no dataset at all (its download failed, its body was not one).
    */
  def places(source: GazetteerSource, http: Plugin.Http): Either[com.histmersion.pluginhost.PluginGazetteers.PlacesResultPart1, (List[Place.Input], List[com.histmersion.pluginhost.PluginGazetteers.PlacesResultPart2Part2])] =
    guard(source.fetch(http)).map { got =>
      val credit = guard(Right(source.rights)).toOption.flatMap(r => Option(r).flatMap(r => Option(r.credit)))
      val read = got.map(p => input(p, credit.getOrElse("")))
      (read.collect { case Right(in) => in }, read.collect { case Left(why) => why })
    }

  /** Pure: the host's place of a plugin's, or why it is refused — no id or kind, a shape or properties that are not
    * JSON, a period the host cannot read. Its source is its own, else the dataset's credit; nothing else is changed.
    */
  def input(p: GazetteerPlace, credit: com.histmersion.pluginhost.PluginGazetteers.InputCredit): Either[com.histmersion.pluginhost.PluginGazetteers.InputResultPart1, Place.Input] =
    try
      if p == null then Left("a place that is null")
      else
        val id = Option(p.id).getOrElse("")
        def named(why: String) = s"place '$id': $why"
        if id.trim.isEmpty then Left("a place with no id")
        else if Option(p.kind).forall(_.trim.isEmpty) then Left(named("no kind"))
        else
          for
            label <- Option(p.label).toRight(named("no label"))
            geometry <- json(Option(p.geometry).flatten, "geometry").left.map(named)
            properties <- json(Option(p.properties).flatten, "properties").left.map(named)
            period <- span(Option(p.period).flatten, Option(p.open).flatten).left.map(named)
            names <- all(Option(p.names).getOrElse(Nil))(name).left.map(named)
            relations <- all(Option(p.relations).getOrElse(Nil))(relation).left.map(named)
          yield
            val source = Option(p.source).flatten.orElse(Option(credit).map(_.trim).filter(_.nonEmpty))
            Place.Input(Place(com.histmersion.api.catalogue.Place.Id(id), label, p.kind, geometry, period._1, source, properties, period._2), names, relations)
    catch
      case NonFatal(e) =>
        Left(
          s"a place the host cannot read (${e.getClass.getSimpleName}) — is the plugin built against plugin SPI " +
            s"${Spi.Version}?"
        )

  private def name(n: GazetteerName): Either[com.histmersion.pluginhost.PluginGazetteers.NameResultPart1, Place.Name] =
    if n == null || n.name == null then Left("a name that is null")
    else
      span(Option(n.period).flatten, Option(n.open).flatten).map((period, open) =>
        Place.Name(n.name, Option(n.lang).getOrElse(""), period, open)
      )

  private def relation(r: GazetteerRelation): Either[com.histmersion.pluginhost.PluginGazetteers.RelationResultPart1, Place.Relation] =
    if r == null then Left("a relation that is null")
    else if Option(r.childId).forall(_.trim.isEmpty) || Option(r.parentId).forall(_.trim.isEmpty) then
      Left("a relation without both its places")
    else if Option(r.kind).forall(_.trim.isEmpty) then Left(s"the relation to '${r.parentId}' has no kind")
    else
      span(Option(r.period).flatten, Option(r.open).flatten).map((period, open) =>
        Place.Relation(r.childId, r.parentId, r.kind, period, open)
      )

  /** A plugin's period and open side as the host's: the period's ends checked again ([[SpiValues.periodOf]]); an open
    * side without a period is refused (it would say nothing).
    */
  private def span(
      period: Option[Period],
      open: Option[GazetteerOpenSide]
  ): Either[com.histmersion.pluginhost.PluginGazetteers.SpanResultPart1, (Option[ApiPeriod], Option[OpenSide])] =
    for
      p <- SpiValues.optional(period)(SpiValues.periodOf)
      side <- (p, open) match
        case (None, Some(_)) => Left("an open side without a period")
        case _               => Right(open.map(openSide))
    yield (p.map(v => ApiPeriod(v.start.value.toLong, v.end.value.toLong)), side)

  private def openSide(o: GazetteerOpenSide): OpenSide = o match
    case GazetteerOpenSide.Start => OpenSide.Start
    case GazetteerOpenSide.End   => OpenSide.End

  /** A JSON text the store keeps as JSON (a shape, the properties): it must be a JSON object. */
  private def json(text: Option[com.histmersion.pluginhost.PluginGazetteers.JsonText], what: com.histmersion.pluginhost.PluginGazetteers.JsonWhat): Either[com.histmersion.pluginhost.PluginGazetteers.JsonResultPart1, Option[com.histmersion.pluginhost.PluginGazetteers.JsonResultPart2]] =
    text match
      case None    => Right(None)
      case Some(t) =>
        io.circe.parser.parse(t) match
          case Right(j) if j.isObject => Right(Some(t))
          case _                      => Left(s"its $what is not a JSON object")

  /** Every element converted, or the first refusal. */
  private def all[A, B](as: List[A])(f: A => Either[com.histmersion.pluginhost.PluginGazetteers.AllFResultPart1, B]): Either[com.histmersion.pluginhost.PluginGazetteers.AllResultPart1, List[B]] =
    @annotation.tailrec
    def loop(rest: List[A], acc: List[B]): Either[String, List[B]] = rest match
      case Nil    => Right(acc.reverse)
      case h :: t =>
        f(h) match
          case Left(e)  => Left(e)
          case Right(b) => loop(t, b :: acc)
    loop(as, Nil)

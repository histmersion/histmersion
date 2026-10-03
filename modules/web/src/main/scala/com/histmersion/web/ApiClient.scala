package com.histmersion.web

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.concurrent.Promise
import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*
import sttp.client4.fetch.FetchBackend
import sttp.model.Method
import sttp.tapir.{DecodeResult, PublicEndpoint}
import sttp.tapir.client.sttp4.SttpClientInterpreter

import com.histmersion.core.*

/** A request the server answered with an error status: the status and the server's own message.
  * `unauthorized` is a write refused by the API-key gate; `conflict` a stale `ifUpdatedAt`. */
final case class ApiFailure(status: Int, error: ApiError) extends Exception(error.message):
  def unauthorized: Boolean = status == 401 || status == 403
  def conflict: Boolean     = status == 412
  def duplicate: Boolean    = status == 409

object ApiFailure:
  /** Pure: what a failed response means, from its status and whatever body could be decoded. */
  def of(status: Int, decoded: Option[ApiError]): ApiFailure =
    ApiFailure(status, decoded.getOrElse(ApiError(status match
      case 401 | 403 => "the server refused the change: an API key is required"
      case 0         => "the server could not be reached"
      case s         => s"unexpected response from the server (HTTP $s)")))

object ApiClient:

  private val backend = FetchBackend()
  private val interp  = SttpClientInterpreter()

  /** Pure: does a request of this method carry the API key? Only writes — the gate lets reads by. */
  private[web] def isWrite(m: Method): Boolean = m != Method.GET && m != Method.HEAD && m != Method.OPTIONS

  /** THE one place a write is given its `Authorization` header ([[Auth.currentHeader]]). */
  private def authorize[T](req: Request[T]): Request[T] =
    if isWrite(req.method) then Auth.currentHeader.fold(req)((k, v) => req.header(k, v)) else req

  /** A refused write drops the key and asks for it again, wherever it came from. */
  private def refuse(f: ApiFailure): ApiFailure =
    if f.unauthorized then Auth.rejected()
    f

  private def call[I, O](ep: PublicEndpoint[I, ApiError, O, Any])(in: I): Future[O] =
    val req = authorize(interp.toRequest(ep, None).apply(in))
    req.send(backend).flatMap { resp =>
      val code = resp.code.code
      resp.body match
        case _ if code == 401 || code == 403  => Future.failed(refuse(ApiFailure.of(code, None)))
        case DecodeResult.Value(Right(o))     => Future.successful(o)
        case DecodeResult.Value(Left(e))      => Future.failed(refuse(ApiFailure(code, e)))
        case other                            =>
          Future.failed(ApiFailure(code, ApiError(s"could not read the server's answer (HTTP $code): $other")))
    }

  def config(): Future[UiConfig]                                       = call(Api.config)(())
  def sources(f: Filter): Future[SearchResult]                         = call(Api.sources)(f)
  def findings(q: Api.FindingsQuery): Future[List[GeoHit]]             = call(Api.findings)(q)
  def aggregate(q: Api.AggregateQuery): Future[TimeHistogram]          = call(Api.aggregate)(q)
  def facets(field: String, prefix: Option[String], f: Filter): Future[List[Facet]] =
    call(Api.facets)((field, prefix, f))
  def resolveId(id: String): Future[ResolvedId]                         = call(Api.resolveId)(id)
  def getSource(ck: String, body: Boolean = true): Future[SourceWithFindings] =
    call(Api.getSource)((ck, body))
  def neighbors(ck: String, kinds: List[String] = Nil): Future[List[NeighborRow]] =
    call(Api.neighbors)((ck, kinds))
  def articles(q: Api.ArticleQuery): Future[ArticleList]                = call(Api.articles)(q)
  def getArticle(slug: String): Future[ArticleView]                     = call(Api.getArticle)(slug)
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): Future[List[PlaceHit]] =
    call(Api.places)((q, asOf, limit))
  def place(id: String, asOf: Option[Long], counts: Boolean = false): Future[PlaceDetail] =
    call(Api.place)((id, asOf, Option.when(counts)(true)))
  def about(): Future[About]                                            = call(Api.about)(())

  def queueList(list: String, all: Boolean = false): Future[Queue.QueueListing] =
    call(OpsApi.queueList)((list, all))
  def queuePatch(id: Int, p: Queue.QueuePatch): Future[Queue.QueueItem] =
    call(OpsApi.queuePatch)((id, p))
  def queueAdd(list: String, in: Queue.QueueAdd): Future[Queue.QueueItem] =
    call(OpsApi.queueAdd)((list, in))
  def queueDelete(id: Int): Future[Queue.QueueItem] =
    call(OpsApi.queueDelete)(id)

  def graph(citekey: String, depth: Option[Int] = None): Future[SourceGraph] =
    call(Api.graph)((citekey, depth))

  def periods(q: Option[String] = None, region: Option[String] = None,
      from: Option[Long] = None, to: Option[Long] = None): Future[List[HistoricalPeriod]] =
    call(Api.periods)((q, region, from, to))

  def placesGeo(kind: Option[String] = None, asOf: Option[Long] = None,
      limit: Option[Int] = None): Future[String] =
    call(Api.placesGeo)((kind, asOf, limit))

  def mediaGeo(kind: Option[String] = None, from: Option[Long] = None, to: Option[Long] = None,
      limit: Option[Int] = None): Future[List[MediaGeoHit]] =
    call(Api.mediaGeo)((kind, from, to, limit))

  def events(q: Api.EventQuery): Future[EventList] = call(Api.events)(q)

  def jobs(status: Option[JobState], limit: Option[Int]): Future[List[JobInfo]] =
    call(Api.jobs)((status.map(_.wire), limit))

  def patchSource(ck: String, p: SourcePatch): Future[SourceWithFindings] = call(Api.patchSource)((ck, p))

  // ── embedded agents ──────────────────────────────────────────────────────────────────────────────

  def agentRuns(status: Option[Agents.RunState], limit: Option[Int]): Future[List[Agents.RunInfo]] =
    call(AgentsApi.runs)((status.map(_.wire), limit))
  def agentRun(id: String): Future[Agents.RunDetail] = call(AgentsApi.run)(id)
  /** pause | resume | cancel; a resume may raise the run's cost cap (the way out of a budget pause). */
  def agentControl(id: String, action: String, budgetUsd: Option[Double] = None): Future[Agents.RunInfo] =
    call(AgentsApi.control)((id, action, budgetUsd, None))
  def approvals(state: Option[String], limit: Option[Int]): Future[List[Agents.ApprovalInfo]] =
    call(AgentsApi.approvals)((state, limit))
  def decide(id: Long, d: Agents.Decision, note: Option[String]): Future[Agents.ApprovalInfo] =
    call(AgentsApi.decide)((id, d.wire, Agents.DecisionRequest(note)))

  /** `POST /api/ingest`, with upload progress (`fetch` reports none, so this one request goes
    * through `XMLHttpRequest`). The URL and its query parameters still come from the tapir endpoint,
    * and the key from [[authorize]] — the same two places as every other call. */
  def ingest(params: IngestParams, file: dom.File, progress: Double => Unit): Future[IngestAccepted] =
    val req    = authorize(interp.toRequest(Api.ingest, None).apply((params, file)))
    val result = Promise[IngestAccepted]()
    val xhr    = new dom.XMLHttpRequest()
    xhr.open("POST", req.uri.toString)
    req.headers.filter(h => h.name.equalsIgnoreCase("Authorization")).foreach(h => xhr.setRequestHeader(h.name, h.value))
    xhr.setRequestHeader("Content-Type", "application/octet-stream")
    xhr.upload.onprogress = (e: dom.ProgressEvent) =>
      if e.lengthComputable && e.total > 0 then progress(e.loaded / e.total)
    xhr.onload = (_: dom.Event) =>
      val body = Option(xhr.responseText).getOrElse("")
      if xhr.status == 202 || xhr.status == 200 then
        io.circe.parser.decode[IngestAccepted](body).fold(
          e => result.failure(ApiFailure(xhr.status, ApiError(s"could not read the server's answer: ${e.getMessage}"))),
          ok => result.success(ok))
      else
        val decoded = io.circe.parser.decode[ApiError](body).toOption
        result.failure(refuse(ApiFailure.of(xhr.status, decoded)))
    xhr.onerror = (_: dom.Event) => result.failure(ApiFailure.of(0, None))
    xhr.send(file)
    result.future

  def fetchHtml(url: String): Future[String] =
    basicRequest.get(uri"$url").response(asStringAlways).send(backend).map(_.body)

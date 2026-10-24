package com.histmersion.server.acquire

import com.histmersion.plugin.{Access, Fetch, FetchContext, PluginLog, SourceFetcher, WorkKey}

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** The host side of source fetching (docs/research-plugins.md §5.1): the [[SourceFetcher]]s the
  * plugins registered, run as ONE chain until one finds a copy. The fetchers know how to ask their
  * upstream; everything around them is decided here, once, for every fetcher alike:
  *
  *  - **the order** — `histmersion.acquire.chain` first, as listed, then every other registered fetcher in
  *    registration order (so a newly installed plugin's fetcher runs without a config change);
  *  - **the legal gate** — [[FetchChain.runs]]: an [[Access.Unauthorised]] fetcher runs only when
  *    `acquire.allow-unauthorised = true`, and NEVER on the legal path (`fetchLegal`, a watcher's
  *    auto-ingest, an archive.org import), whatever that switch says;
  *  - **the PDF check** — a copy that calls itself a PDF and is not one is no copy;
  *  - **the memo** — one per fetch of one work, shared by every fetcher ([[FetchContext.memo]]);
  *  - **the log** — every answer but a copy is logged with its reason, one line per fetcher (a miss
  *    as `acquire: <id>: no copy — <why>`), so a fetch that found nothing says which steps ran and
  *    why each missed; a refusal is not asked again; an exception is a miss.
  */
final class FetchChain(steps: List[FetchChain.Step], allowUnauthorised: Boolean) extends OaFetcher:

  /** The fetchers in the order they run, with their access — what `--self-check` and the log show. */
  def plan: List[(String, Access)] = steps.map(s => s.id -> s.fetcher.access)

  def fetch(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile] =
    fetchWork(WorkKey(doi = doi, arxiv = arxiv), legalOnly = false)

  override def fetchLegal(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile] =
    fetchWork(WorkKey(doi = doi, arxiv = arxiv), legalOnly = true)

  override def fetchWork(work: WorkKey, legalOnly: Boolean): Either[String, FetchedFile] =
    fetchWorkExplained(work, legalOnly).file

  override def fetchWorkExplained(work: WorkKey, legalOnly: Boolean): FetchChain.Outcome =
    val memo  = FetchChain.Memo()
    val tried = LazyList.from(steps)
      .filter(s => FetchChain.runs(s.fetcher.access, legalOnly, allowUnauthorised))
      .map(s => s.id -> attempt(s, work, memo))
    tried.collectFirst { case (_, Fetch.Found(f)) => f } match
      case Some(f) => FetchChain.Outcome(Right(f), Nil)
      case None =>
        val refused = tried.collect { case (id, Fetch.Refused(why)) => s"$id: $why" }.toList
        FetchChain.Outcome(
          Left(FetchChain.NoCopy + (if refused.isEmpty then "" else refused.mkString(" (", "; ", ")"))),
          tried.toList.collect {
            case (id, Fetch.Miss(why))    => FetchChain.Answer(id, FetchChain.Answer.Kind.Miss, why)
            case (id, Fetch.Refused(why)) => FetchChain.Answer(id, FetchChain.Answer.Kind.Refused, why)
          })

  /** One fetcher, one work: its answer, with an exception read as a miss and a "PDF" that is not one
    * read as no copy. */
  private def attempt(s: FetchChain.Step, work: WorkKey, shared: FetchChain.Memo): Fetch =
    if s.fetcher.access == Access.Unauthorised then
      scribe.warn(s"acquire: ${s.id} obtains copies WITHOUT the rights holder's authorisation; asked for " +
        s"${FetchChain.describe(work)} only because acquire.allow-unauthorised = true")
    val ctx = new FetchContext:
      def memo[A](key: String)(compute: => A): A = shared.get(key)(compute)
      val log: PluginLog = new PluginLog:
        def info(message: String) = scribe.info(s"acquire: ${s.id}: $message")
        def warn(message: String) = scribe.warn(s"acquire: ${s.id}: $message")
    val answer =
      try s.fetcher.fetch(work, ctx)
      catch case NonFatal(e) => Fetch.Miss(s"${s.id} failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
    answer match
      case Fetch.Found(f) if f.extension.equalsIgnoreCase("pdf") && !FetchChain.looksLikePdf(f.bytes) =>
        scribe.info(s"acquire: ${s.id}: ${f.url.getOrElse("its copy")} is ${f.bytes.length} bytes that are not a PDF — no copy")
        Fetch.Miss("not a PDF")
      case Fetch.Refused(why) =>
        scribe.info(s"acquire: ${s.id} refused ${FetchChain.describe(work)}: $why")
        answer
      case Fetch.Miss(why) =>
        scribe.info(s"acquire: ${s.id}: no copy — $why")
        answer
      case other => other

object FetchChain:

  /** What every failed fetch says (the ingest step and the acquisition list read it). */
  val NoCopy = "no legal open-access PDF found"

  /** One registered fetcher under its id. */
  final case class Step(id: String, fetcher: SourceFetcher)

  /** What one fetcher that ran answered, when it had no copy. */
  final case class Answer(fetcher: String, kind: Answer.Kind, why: String):
    /** As the ingest report shows it: `oapen: no copy — no record of this work`. */
    def line: String = s"$fetcher: ${kind.label} — $why"
  object Answer:
    enum Kind(val label: String):
      case Miss    extends Kind("no copy")
      case Refused extends Kind("refused")

  /** A fetch of one work: the copy, or the message every failed fetch gives; and, when there is no
    * copy, the answer of each fetcher that ran, in chain order. */
  final case class Outcome(file: Either[String, FetchedFile], answers: List[Answer])

  /** A chain over these fetchers, in this order. */
  def apply(steps: List[(String, SourceFetcher)], allowUnauthorised: Boolean = false): FetchChain =
    new FetchChain(steps.map(Step(_, _)), allowUnauthorised)

  /** The chain over a generation's registered fetchers, in `acquire.chain` order — with the log lines
    * saying what will run (a chain entry no enabled plugin registered, an unauthorised fetcher that
    * stays off). */
  def of(registered: ListMap[String, SourceFetcher], chain: List[String], allowUnauthorised: Boolean)
      : (FetchChain, List[String]) =
    val (ids, unknown) = order(registered.keys.toList, chain)
    val off = ids.filter(id => registered(id).access == Access.Unauthorised && !allowUnauthorised)
      .map(id => s"acquire: $id obtains copies without the rights holder's authorisation and stays off " +
        "(acquire.allow-unauthorised = false)")
    (FetchChain(ids.map(id => id -> registered(id)), allowUnauthorised), unknown ++ off)

  /** Pure: the run order — the ids `acquire.chain` names that are registered, as listed, then the
    * other registered ids in registration order; with a note naming the listed ids nothing registered
    * (a disabled plugin's, or a typo). */
  def order(registered: List[String], chain: List[String]): (List[String], List[String]) =
    val listed = chain.map(_.trim).filter(_.nonEmpty).distinct
    val notes  = listed.filterNot(registered.contains) match
      case Nil     => Nil
      case missing => List(s"acquire.chain names ${missing.map(id => s"'$id'").mkString(", ")}, which no " +
        "enabled plugin registered — skipped")
    (listed.filter(registered.contains) ++ registered.filterNot(listed.contains), notes)

  /** Pure: the legal gate. Open and subscription fetchers always run; an unauthorised one only when the
    * deployment allows it AND the caller did not ask for the legal chain. */
  def runs(access: Access, legalOnly: Boolean, allowUnauthorised: Boolean): Boolean = access match
    case Access.Open | Access.Subscription => true
    case Access.Unauthorised               => allowUnauthorised && !legalOnly

  /** Pure: a PDF that is really a PDF. Without a PDF library this is the cheap structural check: the
    * magic bytes within the first KiB and a plausible size. A publisher's "you do not have access"
    * interstitial is HTML and fails it. */
  def looksLikePdf(b: Array[Byte]): Boolean =
    b.length > 1024 && new String(b.take(1024), "ISO-8859-1").contains("%PDF")

  /** Pure: the work as a log line names it. */
  def describe(w: WorkKey): String =
    List("doi" -> w.doi, "arxiv" -> w.arxiv, "pmid" -> w.pmid, "pmcid" -> w.pmcid, "archive.org" -> w.archive,
      "isbn" -> w.isbn).collect { case (k, Some(v)) => s"$k:$v" } match
      case Nil => w.title.fold("a work with no identifier")(t => s"\"$t\"")
      case ids => ids.mkString(" ")

  /** The per-fetch memo: one fetch of one work runs on one thread, its fetchers one after another. A
    * value computed while another is being computed (a record that needs a search) is stored too. */
  final class Memo:
    // a var-free mutable map, owned by one fetch and dropped with it
    private val values = scala.collection.mutable.HashMap.empty[String, Any]
    def get[A](key: String)(compute: => A): A =
      values.get(key) match
        case Some(v) => v.asInstanceOf[A]
        case None =>
          val v = compute
          values.update(key, v)
          v

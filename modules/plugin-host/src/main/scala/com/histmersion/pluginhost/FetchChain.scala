package com.histmersion.pluginhost

import com.histmersion.plugin.{Access, Fetch, FetchedFile, SourceFetcher, WorkKey}

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal
import com.histmersion.plugin.Plugin

/** The host side of source fetching (docs/research-plugins.md §5.1): the [[SourceFetcher]]s the plugins registered, run
  * as ONE chain until one finds a copy. The fetchers know how to ask their upstream; everything around them is decided
  * here, once, for every fetcher alike:
  *
  *   - **the order** — `histmersion.acquire.chain` first, as listed, then every other registered fetcher in
  *     registration order (so a newly installed plugin's fetcher runs without a config change);
  *   - **the legal gate** — [[FetchChain.runs]]: an [[Access.Unauthorised]] fetcher runs only when
  *     `acquire.allow-unauthorised = true`, and NEVER on the legal path (`fetchLegal`, a watcher's auto-ingest, an
  *     archive.org import), whatever that switch says;
  *   - **the PDF check** — a copy that calls itself a PDF and is not one is no copy;
  *   - **the memo** — one per fetch of one work, shared by every fetcher ([[Fetch.Context.memo]]);
  *   - **the log** — every answer but a copy is logged with its reason, one line per fetcher (a miss as
  *     `acquire: <id>: no copy — <why>`), so a fetch that found nothing says which steps ran and why each missed; a
  *     refusal is not asked again; an exception is a miss.
  */
final class FetchChain(
    steps: List[FetchChain.Step],
    allowUnauthorised: FetchChain.AllowUnauthorised,
    log: HostLog = HostLog.scribe,
    registered: FetchChain.RegisteredArg1 => FetchChain.RegisteredResult = _ => com.histmersion.pluginhost.FetchChain.RegisteredResult(false)
) extends OaFetcher:

  /** The fetchers in the order they run, with their access — what `--self-check` and the log show. */
  def plan: List[(com.histmersion.pluginhost.FetchChain.PlanResultPart1, Access)] = steps.map(s => s.id -> s.fetcher.access)

  def fetch(doi: Option[com.histmersion.domain.library.Doi], arxiv: Option[com.histmersion.domain.library.ArxivId]): Either[com.histmersion.pluginhost.FetchChain.Problem, FetchedFile] =
    fetchWork(WorkKey(doi = doi.flatMap(SpiValues.doi), arxiv = arxiv), legalOnly = false)

  override def fetchLegal(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile] =
    fetchWork(WorkKey(doi = doi.flatMap(SpiValues.doi), arxiv = arxiv), legalOnly = true)

  override def fetchWork(work: WorkKey, legalOnly: Boolean): Either[String, FetchedFile] =
    fetchWorkExplained(work, legalOnly).file

  override def fetchWorkExplained(work: WorkKey, legalOnly: Boolean): FetchChain.Outcome =
    val memo = FetchChain.Memo()
    val tried = LazyList
      .from(steps)
      .filter(s => FetchChain.runs(s.fetcher.access, legalOnly, allowUnauthorised))
      .map(s => s.id -> attempt(s, work, memo))
    tried.collectFirst { case (id, Fetch.Found(f)) => id -> f } match
      case Some((id, f)) => FetchChain.Outcome(Right(f), Nil, Some(id))
      case None          =>
        val refused = tried.collect { case (id, Fetch.Refused(why)) => s"$id: $why" }.toList
        FetchChain.Outcome(
          Left(FetchChain.NoCopy + (if refused.isEmpty then "" else refused.mkString(" (", "; ", ")"))),
          tried.toList.collect {
            case (id, Fetch.Miss(why))    => FetchChain.Answer(id, FetchChain.Answer.Kind.Miss, why)
            case (id, Fetch.Refused(why)) => FetchChain.Answer(id, FetchChain.Answer.Kind.Refused, why)
          }
        )

  /** One fetcher, one work: its answer, with an exception read as a miss and a "PDF" that is not one read as no copy.
    */
  private def attempt(s: FetchChain.Step, work: WorkKey, shared: FetchChain.Memo): Fetch =
    if s.fetcher.access == Access.Unauthorised then
      log.warn(
        s"acquire: ${s.id} obtains copies WITHOUT the rights holder's authorisation; asked for " +
          s"${FetchChain.describe(work)} only because acquire.allow-unauthorised = true"
      )
    val ctx = new com.histmersion.plugin.Fetch.Context:
      def memo[A](key: com.histmersion.pluginhost.FetchChain.MemoKey)(compute: => A): A = shared.get(key)(compute)
      val log: Plugin.Log = new Plugin.Log:
        def info(message: com.histmersion.pluginhost.FetchChain.LogMessage) = FetchChain.this.log.info(s"acquire: ${s.id}: $message")
        def warn(message: com.histmersion.pluginhost.FetchChain.LogMessage) = FetchChain.this.log.warn(s"acquire: ${s.id}: $message")
      override def registered(pluginId: String): Boolean = FetchChain.this.registered(pluginId)
    val answer =
      try s.fetcher.fetch(work, ctx)
      catch case NonFatal(e) => Fetch.Miss(s"${s.id} failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
    answer match
      case Fetch.Found(f) if f.extension.equalsIgnoreCase("pdf") && !FetchChain.looksLikePdf(f.bytes) =>
        log.info(
          s"acquire: ${s.id}: ${f.url.getOrElse("its copy")} is ${f.bytes.length} bytes that are not a PDF — no copy"
        )
        Fetch.Miss(com.histmersion.plugin.Fetch.Miss.Why("not a PDF"))
      case Fetch.Refused(why) =>
        log.info(s"acquire: ${s.id} refused ${FetchChain.describe(work)}: $why")
        answer
      case Fetch.Miss(why) =>
        log.info(s"acquire: ${s.id}: no copy — $why")
        answer
      case other => other

object FetchChain:
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileBytes = FileBytes.T
  object FileBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegalOnly = LegalOnly.T
  object LegalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogMessage = LogMessage.T
  object LogMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MemoKey = MemoKey.T
  object MemoKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What every failed fetch says (the ingest step and the acquisition list read it). */


  type AllowUnauthorised = AllowUnauthorised.T
  object AllowUnauthorised extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredArg1 = RegisteredArg1.T
  object RegisteredArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegisteredResult = RegisteredResult.T
  object RegisteredResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PlanResultPart1 = PlanResultPart1.T
  object PlanResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyStepsPart1 = ApplyStepsPart1.T
  object ApplyStepsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyAllowUnauthorised = ApplyAllowUnauthorised.T
  object ApplyAllowUnauthorised extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRegisteredArg1 = ApplyRegisteredArg1.T
  object ApplyRegisteredArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyRegisteredResult = ApplyRegisteredResult.T
  object ApplyRegisteredResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfRegisteredPart1 = OfRegisteredPart1.T
  object OfRegisteredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfChain = _root_.com.histmersion.util.plugins.Plugin.Id
  val OfChain: _root_.com.histmersion.util.plugins.Plugin.Id.type = _root_.com.histmersion.util.plugins.Plugin.Id
  type OfAllowUnauthorised = OfAllowUnauthorised.T
  object OfAllowUnauthorised extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsAllowUnauthorised = RunsAllowUnauthorised.T
  object RunsAllowUnauthorised extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val NoCopy = "no legal open-access PDF found"

  /** One registered fetcher under its id. */
  final case class Step(id: Step.Id, fetcher: SourceFetcher)

  object Step:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What one fetcher that ran answered, when it had no copy. */
  final case class Answer(fetcher: Answer.Fetcher, kind: Answer.Kind, why: Answer.Why):
    /** As the ingest report shows it: `oapen: no copy — no record of this work`. */
    def line: com.histmersion.pluginhost.FetchChain.Answer.Line = s"$fetcher: ${kind.label} — $why"
  object Answer:
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Fetcher = Fetcher.T
    object Fetcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    enum Kind(val label: Kind.Label):
      case Miss extends Kind(com.histmersion.pluginhost.FetchChain.Answer.Kind.Label("no copy"))
      case Refused extends Kind(com.histmersion.pluginhost.FetchChain.Answer.Kind.Label("refused"))

    object Kind:
      type Label = Label.T
      object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A fetch of one work: the copy, or the message every failed fetch gives; and, when there is no copy, the answer of
    * each fetcher that ran, in chain order; the fetcher that found the copy.
    */
  final case class Outcome(file: Either[Outcome.FilePart1, FetchedFile], answers: List[Answer], foundBy: Option[Outcome.FoundBy] = None)

  object Outcome:
    type FilePart1 = FilePart1.T
    object FilePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FoundBy = FoundBy.T
    object FoundBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A chain over these fetchers, in this order. `registered` answers [[Fetch.Context.registered]]: the plugins of the
    * generation the fetch runs in.
    */
  def apply(
      steps: List[(com.histmersion.pluginhost.FetchChain.ApplyStepsPart1, SourceFetcher)],
      allowUnauthorised: com.histmersion.pluginhost.FetchChain.ApplyAllowUnauthorised = com.histmersion.pluginhost.FetchChain.ApplyAllowUnauthorised(false),
      log: HostLog = HostLog.scribe,
      registered: com.histmersion.pluginhost.FetchChain.ApplyRegisteredArg1 => com.histmersion.pluginhost.FetchChain.ApplyRegisteredResult = _ => com.histmersion.pluginhost.FetchChain.ApplyRegisteredResult(false)
  ): FetchChain =
    new FetchChain(steps.map(Step(_, _)), allowUnauthorised, log, registered)

  /** The chain over a generation's registered fetchers, in `acquire.chain` order — with the log lines saying what will
    * run (a chain entry no enabled plugin registered, an unauthorised fetcher that stays off). `plugins`: the plugins
    * registered in that generation, which each fetch is told of ([[Fetch.Context.registered]]).
    */
  def of(
      registered: ListMap[com.histmersion.pluginhost.FetchChain.OfRegisteredPart1, SourceFetcher],
      chain: List[com.histmersion.pluginhost.FetchChain.OfChain],
      allowUnauthorised: com.histmersion.pluginhost.FetchChain.OfAllowUnauthorised,
      log: HostLog = HostLog.scribe,
      plugins: Set[com.histmersion.util.plugins.Plugin.Id] = Set.empty
  ): (FetchChain, List[String]) =
    val (ids, unknown) = order(registered.keys.toList, chain)
    val off = ids
      .filter(id => registered(id).access == Access.Unauthorised && !allowUnauthorised)
      .map(id =>
        s"acquire: $id obtains copies without the rights holder's authorisation and stays off " +
          "(acquire.allow-unauthorised = false)"
      )
    (FetchChain(ids.map(id => id -> registered(id)), allowUnauthorised, log, plugins), unknown ++ off)

  /** Pure: the run order — the ids `acquire.chain` names that are registered, as listed, then the other registered ids
    * in registration order; with a note naming the listed ids nothing registered (a disabled plugin's, or a typo).
    */
  def order(registered: List[com.histmersion.util.plugins.Plugin.Id], chain: List[com.histmersion.util.plugins.Plugin.Id]): (List[com.histmersion.util.plugins.Plugin.Id], List[com.histmersion.pluginhost.FetchChain.Problem]) =
    val listed = chain.map(_.trim).filter(_.nonEmpty).distinct
    val notes = listed.filterNot(registered.contains) match
      case Nil     => Nil
      case missing =>
        List(
          s"acquire.chain names ${missing.map(id => s"'$id'").mkString(", ")}, which no " +
            "enabled plugin registered — skipped"
        )
    (listed.filter(registered.contains) ++ registered.filterNot(listed.contains), notes)

  /** Pure: the legal gate. Open and subscription fetchers always run; an unauthorised one only when the deployment
    * allows it AND the caller did not ask for the legal chain.
    */
  def runs(access: Access, legalOnly: com.histmersion.pluginhost.FetchChain.LegalOnly, allowUnauthorised: com.histmersion.pluginhost.FetchChain.RunsAllowUnauthorised): Boolean = access match
    case Access.Open | Access.Subscription => true
    case Access.Unauthorised               => allowUnauthorised && !legalOnly

  /** Pure: a PDF that is really a PDF. Without a PDF library this is the cheap structural check: the magic bytes within
    * the first KiB and a plausible size. A publisher's "you do not have access" interstitial is HTML and fails it.
    */
  def looksLikePdf(b: com.histmersion.pluginhost.FetchChain.FileBytes): Boolean =
    b.length > 1024 && new String(b.take(1024), "ISO-8859-1").contains("%PDF")

  /** Pure: the work as a log line names it. */
  def describe(w: WorkKey): com.histmersion.pluginhost.FetchChain.Description =
    List(
      "doi" -> w.doi.map(_.value),
      "arxiv" -> w.arxiv,
      "pmid" -> w.pmid,
      "pmcid" -> w.pmcid,
      "archive.org" -> w.archive,
      "isbn" -> w.isbn
    ).collect { case (k, Some(v)) => s"$k:$v" } match
      case Nil => w.title.fold("a work with no identifier")(t => s"\"$t\"")
      case ids => ids.mkString(" ")

  /** The per-fetch memo: one fetch of one work runs on one thread, its fetchers one after another. A value computed
    * while another is being computed (a record that needs a search) is stored too.
    */
  final class Memo:
    // a var-free mutable map, owned by one fetch and dropped with it
    private val values = scala.collection.mutable.HashMap.empty[String, Any]
    def get[A](key: com.histmersion.pluginhost.FetchChain.MemoKey)(compute: => A): A =
      values.get(key) match
        case Some(v) => v.asInstanceOf[A]
        case None    =>
          val v = compute
          values.update(key, v)
          v


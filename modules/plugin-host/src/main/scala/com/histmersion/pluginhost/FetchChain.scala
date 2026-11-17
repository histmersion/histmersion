package com.histmersion
package pluginhost
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import FetchChain.describe

import com.histmersion.plugin.{Access, Fetch, FetchedFile, SourceFetcher, WorkKey}

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal
import com.histmersion.plugin.Plugin
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.ArxivId
import io.github.iltotore.iron.constraint.any.Pure

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
    registered: Fetch.Context.RegisteredPluginId => Boolean = _ => false
) extends OaFetcher:

  /** The fetchers in the order they run, with their access — what `--self-check` and the log show. */
  def plan: List[(FetchChain.Step.Id, Access)] = steps.map(s => s.id -> s.fetcher.access)

  def fetch(doi: Option[Doi], arxiv: Option[ArxivId]): Either[Problems, FetchedFile] =
    fetchWork(workOf(doi, arxiv), FetchChain.LegalOnly(false))

  override def fetchLegal(doi: Option[Doi], arxiv: Option[ArxivId]): Either[Problems, FetchedFile] =
    fetchWork(workOf(doi, arxiv), FetchChain.LegalOnly(true))

  private def workOf(doi: Option[Doi], arxiv: Option[ArxivId]): WorkKey =
    WorkKey(doi = doi.flatMap(SpiValues.doi), arxiv = arxiv.map(a => WorkKey.Arxiv(a.value)))

  override def fetchWork(work: WorkKey, legalOnly: FetchChain.LegalOnly): Either[Problems, FetchedFile] =
    fetchWorkExplained(work, legalOnly).file

  override def fetchWorkExplained(work: WorkKey, legalOnly: FetchChain.LegalOnly): FetchChain.Outcome =
    val memo = FetchChain.Memo()
    val tried = LazyList
      .from(steps)
      .filter(s => FetchChain.runs(s.fetcher.access, legalOnly, allowUnauthorised))
      .map(s => s.id -> attempt(s, work, memo))
    tried.collectFirst { case (id, Fetch.Found(f)) => id -> f } match
      case Some((id, f)) => FetchChain.Outcome(Right(f), Nil, Some(FetchChain.Outcome.FoundBy(id.value)))
      case None          =>
        val refused = tried.collect { case (_, Fetch.Refused(why)) => why }.toList
        FetchChain.Outcome(
          Left(Problems.of(Problem.NotFound.Copy(Problem.Raw(s"${describe(work)}"), Vector.empty), refused*)),
          tried.toList.collect {
            case (id, Fetch.Miss(why)) =>
              FetchChain.Answer(
                FetchChain.Answer.Fetcher(id.value),
                FetchChain.Answer.Kind.Miss,
                why
              )
            case (id, Fetch.Refused(why)) =>
              FetchChain.Answer(
                FetchChain.Answer.Fetcher(id.value),
                FetchChain.Answer.Kind.Refused,
                why
              )
          }
        )

  /** One fetcher, one work: its answer, with an exception read as a miss and a "PDF" that is not one read as no copy.
    */
  private def attempt(s: FetchChain.Step, work: WorkKey, shared: FetchChain.Memo): Fetch =
    if s.fetcher.access == Access.Unauthorised then
      log.warn(
        HostLog.Line(
          s"acquire: ${s.id.value} obtains copies WITHOUT the rights holder's authorisation; asked for " +
            s"${FetchChain.describe(work).value} only because acquire.allow-unauthorised = true"
        )
      )
    val ctx = new Fetch.Context:
      def memo[A](key: Fetch.Context.MemoKey)(compute: => A): A = shared.get(key)(compute)
      val log: Plugin.Log = new Plugin.Log:
        def info(message: Plugin.Log.Line) =
          FetchChain.this.log.info(HostLog.Line(s"acquire: ${s.id.value}: ${message.value}"))
        def warn(message: Plugin.Log.Line) =
          FetchChain.this.log.warn(HostLog.Line(s"acquire: ${s.id.value}: ${message.value}"))
      override def registered(pluginId: Fetch.Context.RegisteredPluginId): Boolean =
        FetchChain.this.registered(pluginId)
    val answer =
      try s.fetcher.fetch(work, ctx)
      catch
        case NonFatal(e) =>
          Fetch.Miss(Problem.from(e, Problem.Where(s"fetcher ${s.id}"), Problem.Internal.Id.mint()))
    answer match
      case Fetch.Found(f) if f.extension.value.equalsIgnoreCase("pdf") && !FetchChain.looksLikePdf(f.bytes) =>
        log.info(
          HostLog.Line(
            s"acquire: ${s.id.value}: ${f.url.map(_.value).getOrElse("its copy")} is ${f.bytes.value.length} bytes that are not a PDF — no copy"
          )
        )
        Fetch.Miss(
          Problem.Io.Corrupt(
            Problem.Io.Format.Pdf,
            Problem.Where(f.url.fold(s"${describe(work)}")(u => s"$u")),
            Problem.Io.Damage.Undecodable
          )
        )
      case Fetch.Refused(why) =>
        log.info(HostLog.Line(s"acquire: ${s.id.value} refused ${FetchChain.describe(work).value}: ${why.sentence}"))
        answer
      case Fetch.Miss(why) =>
        log.info(HostLog.Line(s"acquire: ${s.id.value}: no copy — ${why.sentence}"))
        answer
      case other => other

object FetchChain:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type LegalOnly = LegalOnly.T
  object LegalOnly extends Newtype[Boolean, Pure]

  type AllowUnauthorised = AllowUnauthorised.T
  object AllowUnauthorised extends Newtype[Boolean, Pure]

  /** The setting that lets an unauthorised fetcher run. */
  val AllowUnauthorisedKey = "histmersion.acquire.allow-unauthorised"

  /** The setting that orders the fetchers. */
  val ChainKey = "histmersion.acquire.chain"

  /** One registered fetcher under its id. */
  final case class Step(id: Step.Id, fetcher: SourceFetcher)

  object Step:
    type Id = Id.T
    object Id extends Newtype[String, Pure]

  /** What one fetcher that ran answered, when it had no copy. */
  final case class Answer(fetcher: Answer.Fetcher, kind: Answer.Kind, why: Problem):
    /** As the ingest report shows it: `oapen: no copy — no record of this work`. */
    def line: Answer.Line = Answer.Line(s"${fetcher.value}: ${kind.label.value} — ${why.sentence}")
  object Answer:
    type Line = Line.T
    object Line extends Newtype[String, Pure]

    type Fetcher = Fetcher.T
    object Fetcher extends Newtype[String, Pure]

    enum Kind(val label: Kind.Label) derives CanEqual:
      case Miss extends Kind(Kind.Label("no copy"))
      case Refused extends Kind(Kind.Label("refused"))

    object Kind:
      type Label = Label.T
      object Label extends Newtype[String, Pure]

  /** A fetch of one work: the copy, or the message every failed fetch gives; and, when there is no copy, the answer of
    * each fetcher that ran, in chain order; the fetcher that found the copy.
    */
  final case class Outcome(
      file: Either[Problems, FetchedFile],
      answers: List[Answer],
      foundBy: Option[Outcome.FoundBy] = None
  )

  object Outcome:
    type FoundBy = FoundBy.T
    object FoundBy extends Newtype[String, Pure]

  /** A chain over these fetchers, in this order. `registered` answers [[Fetch.Context.registered]]: the plugins of the
    * generation the fetch runs in.
    */
  def apply(
      steps: List[(Step.Id, SourceFetcher)],
      allowUnauthorised: AllowUnauthorised = AllowUnauthorised(false),
      log: HostLog = HostLog.scribe,
      registered: Fetch.Context.RegisteredPluginId => Boolean = _ => false
  ): FetchChain =
    new FetchChain(steps.map(Step(_, _)), allowUnauthorised, log, registered)

  /** The chain over a generation's registered fetchers, in `acquire.chain` order — with the log lines saying what will
    * run (a chain entry no enabled plugin registered, an unauthorised fetcher that stays off). `plugins`: the plugins
    * registered in that generation, which each fetch is told of ([[Fetch.Context.registered]]).
    */
  def of(
      registered: ListMap[com.histmersion.util.plugins.Plugin.Id, SourceFetcher],
      chain: List[com.histmersion.util.plugins.Plugin.Id],
      allowUnauthorised: AllowUnauthorised,
      log: HostLog = HostLog.scribe,
      plugins: Set[com.histmersion.util.plugins.Plugin.Id] = Set.empty
  ): (FetchChain, List[Problem]) =
    val (ids, unknown) = order(registered.keys.toList, chain)
    val off = ids
      .filter(id => registered(id).access == Access.Unauthorised && !allowUnauthorised.value)
      .map(id =>
        Problem.Refused.Disabled(
          Problem.Refused.Feature(s"$id"),
          Problem.SettingKey(AllowUnauthorisedKey),
          None,
          Some(Problem.Refused.Effect.Unauthorised)
        )
      )
    val known: Set[String] = plugins.map(_.value)
    (
      FetchChain(
        ids.map(id => Step.Id(id.value) -> registered(id)),
        allowUnauthorised,
        log,
        pluginId => known.contains(pluginId.value)
      ),
      unknown ++ off
    )

  /** Pure: the run order — the ids `acquire.chain` names that are registered, as listed, then the other registered ids
    * in registration order; with a note naming the listed ids nothing registered (a disabled plugin's, or a typo).
    */
  def order(
      registered: List[com.histmersion.util.plugins.Plugin.Id],
      chain: List[com.histmersion.util.plugins.Plugin.Id]
  ): (List[com.histmersion.util.plugins.Plugin.Id], List[Problem]) =
    val listed = chain.distinct
    val notes = listed
      .filterNot(registered.contains)
      .map(id =>
        Problem.Config.Setting(Problem.SettingKey(ChainKey), Problem.Config.Fault.Unregistered(Problem.Raw(s"$id")))
      )
    (listed.filter(registered.contains) ++ registered.filterNot(listed.contains), notes)

  /** Pure: the legal gate. Open and subscription fetchers always run; an unauthorised one only when the deployment
    * allows it AND the caller did not ask for the legal chain.
    */
  def runs(access: Access, legalOnly: LegalOnly, allowUnauthorised: AllowUnauthorised): Boolean = access match
    case Access.Open | Access.Subscription => true
    case Access.Unauthorised               => allowUnauthorised.value && !legalOnly.value

  /** Pure: a PDF that is really a PDF. Without a PDF library this is the cheap structural check: the magic bytes within
    * the first KiB and a plausible size. A publisher's "you do not have access" interstitial is HTML and fails it.
    */
  def looksLikePdf(b: FetchedFile.Bytes): Boolean =
    b.value.length > 1024 && new String(b.value.take(1024), "ISO-8859-1").contains("%PDF")

  /** Pure: the work as a log line names it. */
  def describe(w: WorkKey): Description =
    List(
      "doi" -> w.doi.map(_.value),
      "arxiv" -> w.arxiv.map(_.value),
      "pmid" -> w.pmid.map(_.value),
      "pmcid" -> w.pmcid.map(_.value),
      "archive.org" -> w.archive.map(_.value),
      "isbn" -> w.isbn.map(_.value)
    ).collect { case (k, Some(v)) => s"$k:$v" } match
      case Nil => Description(w.title.fold("a work with no identifier")(t => s"\"${t.value}\""))
      case ids => Description(ids.mkString(" "))

  /** The per-fetch memo: one fetch of one work runs on one thread, its fetchers one after another. A value computed
    * while another is being computed (a record that needs a search) is stored too.
    */
  final class Memo:
    // a var-free mutable map, owned by one fetch and dropped with it
    private val values = scala.collection.mutable.HashMap.empty[Fetch.Context.MemoKey, Any]
    def get[A](key: Fetch.Context.MemoKey)(compute: => A): A =
      values.get(key) match
        case Some(v) => v.asInstanceOf[A]
        case None    =>
          val v = compute
          values.update(key, v)
          v

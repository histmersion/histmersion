package com.histmersion
package domain.library.workflows

import com.histmersion.domain.library.workflows.Loaded
import com.histmersion.domain.library.workflows.MetaChecked
import com.histmersion.domain.library.workflows.Classification
import com.histmersion.domain.library.workflows.Classified
import com.histmersion.domain.library.workflows.Geocoded
import com.histmersion.domain.library.workflows.Notes
import com.histmersion.domain.library.workflows.Patched
import com.histmersion.domain.library.workflows.Upstreamed
import com.histmersion.domain.library.workflows.StatusRouted
import com.histmersion.domain.library.workflows.Closed
import com.histmersion.domain.library.workflows.VerifiedFinding
import com.histmersion.domain.library.workflows.Findings
import com.histmersion.domain.library.workflows.VerifiedFindings
import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.domain.library.workflows.GeocodeChoice
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.PipelineDef

import com.histmersion.domain.automation.worklists.QueueItemId

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, Pipeline, Role}
import com.histmersion.domain.EnrichSource.*

import com.histmersion.api.automation.{AgentsMarkdown, Queue}
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.{Finding, Source as ApiSource}
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.library.Doi
import com.histmersion.api.library.Confidence
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.automation.Agents
import com.histmersion.api.Api
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** `enrich-source <citekey>` (docs/research-embedded-agents.md §4.2): read one source and propose its summary,
  * classification, period, quoted findings and notes; CODE verifies every answer and writes ONE patch. The LLM steps
  * get no write tool — classify may look up the vocabulary (`facets`, `search`), the others get none at all, so text
  * injected into an OCR'd body has nothing to call.
  *
  * Steps (each checkpointed, in this order): load · metadata · classify · findings-1..n · geocode · notes · patch ·
  * refs · assess · status · close. `metadata`, `refs` and `assess` reach OpenAlex / Crossref through [[AgentUpstream]]
  * (skipped, and said so, when `agents.enrich.upstream` is off or the source has no DOI). A status the model proposes
  * beyond filling `unassessed` goes through [[Approvals]]. `close` runs the per-source lint, ticks off the acquisition
  * item the run was started for, and writes the log line.
  *
  * Every write goes through the run's gate ([[RunCtx.write]]): the patch, `refs`/`assess` applied, the status proposal,
  * the acquisition item and the log line. A DRY run records each on its step instead (`refs` and `assess` then only
  * read upstream).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class EnrichSourcePipeline(
    site: LibrarySite,
    cfg: => AgentsConfig,
    lib: => LibraryAgentsConfig,
    upstream: () => AgentUpstream,
    approvals: SourceProposals,
    records: EnrichRecords
) extends PipelineDef:
  import records.given
  val pipeline: Pipeline = Pipeline.EnrichSource
  private val name = pipeline.wire

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def run(ctx: RunCtx)(using WritePermit)(using Creator.New): Either[Halt, PipelineDef.Summary] =
    for
      ck <- Citekey.parse(ctx.run.target.value).left.map(Halt.Fail(_))
      loaded <- ctx.code[Loaded](AgentRepo.StepRecord.Name("load"))(load(ck))
      _ <- ctx.code[MetaChecked](AgentRepo.StepRecord.Name("metadata"))(Right(metadata(ctx, loaded)))
      classified <- ctx.llm[Classification, Classified](
        AgentRepo.StepRecord.Name("classify"),
        StepAgent.EnrichClassify,
        Prompt.UserText(classifyInput(loaded)),
        LibraryAnswers.classification
      )((c, last) => accepted(c.checkClassification(MaxAbsYear(lib.verify.maxAbsYear.value), Last(last.value))))
      verified <- findings(ctx, loaded, classified)
      geocoded <- ctx.code[Geocoded](AgentRepo.StepRecord.Name("geocode"))(Right(geocode(ctx, verified)))
      notes <- ctx.llm[Notes, String](
        AgentRepo.StepRecord.Name("notes"),
        StepAgent.EnrichNotes,
        Prompt.UserText(notesInput(loaded, classified, verified)),
        LibraryAnswers.notes
      )((n, _) =>
        n.checkNotes(isCitekey, site.articleExists)
          .map(d => Checked(d.value))
      )
      patched <- ctx.code[Patched](AgentRepo.StepRecord.Name("patch"))(
        write(ctx, ck, loaded, classified, geocoded.findings, notes)
      )
      _ <- ctx.code[Upstreamed](AgentRepo.StepRecord.Name("refs"))(Right(refs(ctx, loaded)))
      assessed <- ctx.code[Upstreamed](AgentRepo.StepRecord.Name("assess"))(Right(assess(ctx, loaded)))
      routed <- ctx.code[StatusRouted](AgentRepo.StepRecord.Name("status"))(
        Right(status(ctx, ck, classified, patched, assessed))
      )
      closed <- ctx.code[Closed](AgentRepo.StepRecord.Name("close"))(close(ctx, ck, patched, routed))
    yield PipelineDef.Summary(closed.line.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def accepted[A](
      r: Either[List[Problem], (A, List[EnrichSource.Description])]
  ): Either[List[Problem], Checked[A]] =
    r.map((v, w) => Checked(v, w.map(d => Checked.Warnings(d.value))))

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def load(ck: Citekey): Either[Problems, Loaded] =
    site.sources
      .get(ck, body = SourcesControllers.GetBody(true))
      .left
      .map(Api.Error(_))
      .left
      .map(_.problems)
      .flatMap { sw =>
        val s = sw.source
        val body = s.ocrMarkdown.map(_.value.trim).getOrElse("")
        if body.isEmpty then Left(Problem.Run.Needs(Problem.Run.Need.Text(Problem.Raw.of(ck))))
        else
          Right(
            Loaded(
              citekey = Citekey.assume(s.citekey.value),
              title = Loaded.Title(s.title.value),
              authors = s.authors.map(a => AuthorName(a.value)),
              year = s.year.map(y => Year(y.value.toInt)),
              venue = s.venue.map(v => Loaded.Venue(v.value)),
              status = s.status.wire,
              hasSummary = Loaded.HasSummary(s.summary.exists(_.value.trim.nonEmpty)),
              hasNotes = Loaded.HasNotes(s.notes.exists(_.value.trim.nonEmpty)),
              hasPeriod = Loaded.HasPeriod(s.periodStart.isDefined),
              period = for a <- s.periodStart; b <- s.periodEnd yield Period(a, b),
              topics = s.topics.map(t => Topic(t.value)),
              regions = s.regions.map(r => Region(r.value)),
              existingClaims = sw.findings.map(f => Loaded.ExistingClaims(f.claim.value)),
              updatedAt = s.updatedAt,
              head = Loaded.Head(body.take(lib.enrich.headChars.value)),
              windows = windows(
                SourceBody(body),
                WindowChars(lib.enrich.windowChars.value),
                MaxWindows(lib.enrich.maxWindows.value)
              ),
              tags = s.tags.map(t => Tag(t.value)),
              doi = s.doi.flatMap(d => Doi.option(d.value))
            )
          )
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def meta(l: Loaded): String =
    s"citekey: ${l.citekey}\ntitle: ${l.title}\nauthors: ${l.authors.mkString("; ")}\n" +
      s"year: ${l.year.fold("unknown")(_.toString)}\nvenue: ${l.venue.getOrElse("unknown")}"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def classifyInput(l: Loaded): String =
    s"Classify this source.\n\n${meta(l)}\n\nThe opening of its text:\n\n${Prompt.data("source-text", l.head.value)}"

  /** One LLM step per window. Each answer's quotes are checked against THAT window, and its places against the
    * gazetteer the `geocode` step will use (#1424: asking before the answer is accepted is the only point where a
    * better name can still be asked for).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def findings(ctx: RunCtx, l: Loaded, c: Classified)(using WritePermit): Either[Halt, List[VerifiedFinding]] =
    val existing = l.existingClaims.toSet
    val guards =
      lib.enrich.guards.guards((name, kind, at) => site.geocode(LibrarySite.PlaceName(name.value), kind, at).isDefined)
    l.windows.foldLeft[Either[Halt, List[VerifiedFinding]]](Right(Nil)) { (acc, w) =>
      acc.flatMap { sofar =>
        val user = s"Extract the findings of this stretch of the source.\n\n${meta(l)}\n" +
          w.heading.fold("")(hd => s"section: $hd\n") +
          s"stretch ${w.index} of ${l.windows.size}\n\n${Prompt.data("source-text", w.text.value)}" + ctx.notes
        // the VERIFIED list is what is checkpointed: a resume replays it without re-checking
        ctx
          .llm[Findings, VerifiedFindings](
            AgentRepo.StepRecord.Name(s"findings-${w.index}"),
            StepAgent.EnrichFindings,
            Prompt.UserText(user),
            LibraryAnswers.findings
          ) { (f, last) =>
            accepted(
              f.checkFindings(
                w,
                MinRatio(lib.verify.quoteMinRatio.value),
                MaxAbsYear(lib.verify.maxAbsYear.value),
                l.period.orElse(c.period),
                existing ++ sofar.map(v => Loaded.ExistingClaims(v.claim.value)),
                guards,
                Last(last.value)
              )
            )
          }
          .map(v => sofar ++ v.findings)
      }
    }

  /** Places through the gazetteer (the catalogue's places, #981), never the model's coordinates: the model's place kind
    * and the finding's period steer the choice. The finding keeps the name that was ASKED as its `region` (the chosen
    * place's label can be a Pleiades string of five names); the choice — place id and label — goes into the checkpoint,
    * so a person can audit it. An unknown place keeps its name with no point, and an approximate choice (a qualifier
    * dropped, a placeholder point) is kept with a warning; both are said.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def geocode(ctx: RunCtx, fs: List[VerifiedFinding])(using WritePermit): Geocoded =
    val placed = fs.map { f =>
      val hit = f.place.flatMap(p =>
        site.geocode(LibrarySite.PlaceName(p.value), f.placeKind.getOrElse(KindHint.Unknown), f.period)
      )
      f.place.foreach { p =>
        hit match
          case None => ctx.warn(s"geocode: no gazetteer place named '$p' (finding kept without a point)")
          case Some(r) if r.isApproximate => ctx.warn(s"geocode: ${r.describe}")
          case Some(_)                    => ()
      }
      val input = Finding.Input(
        claim = Finding.Claim(f.claim.value),
        region = f.place.map(p => Finding.Region(p.value)),
        lon = hit.map(_.lon),
        lat = hit.map(_.lat),
        periodStart = f.start,
        periodEnd = f.end,
        periodName = f.periodName.map(n => Finding.PeriodName(n.value)),
        tags = f.tags.map(t => Finding.Tags(t.value)),
        confidence = Some(Confidence.Wire(f.confidence.value))
      )
      val choice = f.place.map(p =>
        GeocodeChoice(
          GeocodeChoice.Claim(f.claim.value),
          GeocodeChoice.Asked(p.value),
          hit.map(h => Place.Id.assume(h.placeId.value)),
          hit.map(h => GeocodeChoice.Label(h.label.value)),
          hit.filter(_.isApproximate).map(h => GeocodeChoice.Approximate(h.approximate.map(_.describe).mkString("; ")))
        )
      )
      (input, choice)
    }
    Geocoded(placed.map(_._1), placed.flatMap(_._2))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def notesInput(l: Loaded, c: Classified, fs: List[VerifiedFinding]): String =
    s"Write the notes for [[${l.citekey}]].\n\n${meta(l)}\n\nsummary: ${c.summary}\n\nverified findings:\n" +
      fs.map(f => s"- ${f.claim} (quote: \"${f.quote}\")").mkString("\n")

  private def isCitekey(s: Citekey): Boolean =
    site.sources.get(s, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)).isRight

  /** The single write. On a 412 (someone wrote the source since `load`) the source is re-read and the patch rebuilt
    * from the CHECKPOINTED answers — no LLM call — and sent once more.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def write(
      ctx: RunCtx,
      ck: Citekey,
      l: Loaded,
      c: Classified,
      fs: List[Finding.Input],
      notes: String
  )(using Creator.New): Either[Problems, Patched] =
    val (p, fields, proposal) = l.patch(c, fs, Option(notes).filter(_.nonEmpty).map(PatchNotes(_)))
    // a dry run's simulated patch "succeeds": its step says what it would have set
    ctx.write(s"patch [[$ck]]: ${if fields.isEmpty then "nothing new" else fields.mkString(", ")}", p.asJson)(
      site.sources.patch(ck, p).map(_ => ())
    )(Right(())) match
      case Right(_) =>
        Right(
          Patched(
            Patched.FindingsAdded(fs.size),
            fields.map(f => Patched.FieldsSet(f.value)),
            proposal.map(d => Patched.StatusProposal(d.value))
          )
        )
      case Left(e) if e.code.exists(_.value == Write.CodePrecondition) =>
        load(ck).flatMap { fresh =>
          val again = fresh.modify(_.windows).setTo(Nil).modify(_.head).setTo(Loaded.Head(""))
          val existing = fresh.existingClaims.map(x => AgentVerify.normalise(AgentVerify.Text(x.value))).toSet
          val (p2, fields2, proposal2) = again.patch(
            c,
            fs.filterNot(f => existing.contains(AgentVerify.normalise(AgentVerify.Text(f.claim.value)))),
            Option(notes).filter(_.nonEmpty).map(PatchNotes(_))
          )
          site.sources
            .patch(ck, p2)
            .left
            .map(_.problems)
            .map(_ =>
              Patched(
                Patched.FindingsAdded(p2.findings.fold(0)(_.add.size)),
                fields2.map(f => Patched.FieldsSet(f.value)),
                proposal2.map(d => Patched.StatusProposal(d.value))
              )
            )
        }
      case Left(e) => Left(e.problems)

  // ── upstream: metadata, refs, assess ──────────────────────────────────────────────────────────

  /** Why an upstream step cannot run, if it cannot: switched off, or no DOI to ask about. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def upstreamBlocked(l: Loaded): Option[String] =
    if !lib.enrich.upstream.value then Some("upstream checks are off (agents.enrich.upstream)")
    else if l.doi.forall(_.value.trim.isEmpty) then Some("the source has no DOI")
    else None

  /** The record against OpenAlex/Crossref. Differences are WARNINGS for a person (a wrong year can mean a wrong
    * citekey, which only a person renames); nothing is written.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def metadata(ctx: RunCtx, l: Loaded)(using WritePermit): MetaChecked =
    upstreamBlocked(l) match
      case Some(why) => MetaChecked(checked = MetaChecked.Checked(false), Nil, MetaChecked.Note(why))
      case None      =>
        upstream().meta(l.doi.get) match
          case Left(m) =>
            ctx.warn(s"metadata: not checked — ${m.sentences.mkString("; ")}")
            MetaChecked(checked = MetaChecked.Checked(false), Nil, MetaChecked.Note(m.sentences.mkString("; ")))
          case Right(w) =>
            val diffs = compareMetadata(l.citekey, Title(l.title.value), l.authors, l.year, w)
            diffs.foreach(d => ctx.warn(s"metadata: $d"))
            MetaChecked(checked = MetaChecked.Checked(true), diffs.map(d => MetaChecked.Differences(d.value)))

  /** `refs --apply`: the deposited reference list becomes `references` edges (a network failure is a warning, never a
    * failed run — the enrichment is already written).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def refs(ctx: RunCtx, l: Loaded)(using WritePermit)(using Creator.New): Upstreamed =
    upstreamBlocked(l) match
      case Some(why) => Upstreamed(ran = Upstreamed.Ran(false), Upstreamed.Summary(why))
      case None      =>
        upstream().refs(l.citekey, apply = AgentUpstream.RefsApply(!ctx.dryRun)) match
          case Left(m) =>
            ctx.warn(s"refs: ${m.sentences.mkString("; ")}")
            Upstreamed(ran = Upstreamed.Ran(false), Upstreamed.Summary(m.sentences.mkString("; ")))
          case Right(r) =>
            if ctx.dryRun then
              ctx.write(
                s"refs [[${l.citekey}]] applied: ${r.resolved} reference edge(s) to sources in the corpus",
                r.asJson
              )(())(())
            Upstreamed(
              ran = Upstreamed.Ran(true),
              Upstreamed.Summary(s"${r.count} reference(s), ${r.resolved} in the corpus, ${r.edgesAdded} edge(s) added")
            )

  /** `assess --apply`: a retraction overwrites the status (the assess rule) and flags the run. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def assess(ctx: RunCtx, l: Loaded)(using WritePermit)(using Creator.New): Upstreamed =
    upstreamBlocked(l) match
      case Some(why) => Upstreamed(ran = Upstreamed.Ran(false), Upstreamed.Summary(why))
      case None      =>
        upstream().assess(l.citekey, apply = AgentUpstream.AssessApply(!ctx.dryRun)) match
          case Left(m) =>
            ctx.warn(s"assess: ${m.sentences.mkString("; ")}")
            Upstreamed(ran = Upstreamed.Ran(false), Upstreamed.Summary(m.sentences.mkString("; ")))
          case Right(a) =>
            if a.isRetracted.isRetracted then ctx.warn(s"assess: RETRACTED — ${a.recommendation}")
            if ctx.dryRun then ctx.write(s"assess [[${l.citekey}]] applied: verdict ${a.verdict}", a.asJson)(())(())
            Upstreamed(
              ran = Upstreamed.Ran(true),
              Upstreamed.Summary(s"verdict ${a.verdict}${a.applied.fold("")(st => s", status set to $st")}"),
              Upstreamed.Retracted(a.isRetracted.isRetracted)
            )

  /** The classify step's status, if the patch did not already fill it: through the approval gate. A retraction found by
    * `assess` outranks any proposal.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def status(ctx: RunCtx, ck: Citekey, c: Classified, p: Patched, a: Upstreamed)(using
      WritePermit
  )(using Creator.New): StatusRouted =
    if p.fieldsSet.contains(Patched.FieldsSet("status")) then
      StatusRouted(Some(StatusRouted.Applied(c.status.wire.value)), None, StatusRouted.Note("filled by the patch"))
    else if c.status == ApiSource.Status.Unassessed then
      StatusRouted(None, None, StatusRouted.Note("no status proposed"))
    else if a.retracted.value then
      StatusRouted(None, None, StatusRouted.Note(s"${c.status.wire} not proposed: the source is retracted"))
    else
      val proposal = Proposal(
        ApprovalKind.StatusChange,
        ck,
        status = Some(c.status),
        evidence = Proposal.Evidence(
          Option(c.statusEvidence.value).filter(_.trim.nonEmpty).getOrElse("(the model gave no evidence)")
        )
      )
      approvals.propose(ctx, proposal) match
        case o @ SourceProposals.Outcome.Applied(w) =>
          StatusRouted(Some(StatusRouted.Applied(c.status.wire.value)), None, StatusRouted.Note(o.line))
        case o @ SourceProposals.Outcome.Recorded(id, _) =>
          ctx.warn(s"status ${c.status.wire} not applied: approval $id waits for a person")
          StatusRouted(None, Some(StatusRouted.Approval(id.value)), StatusRouted.Note(o.line))
        case o @ SourceProposals.Outcome.Failed(_) =>
          ctx.warn(s"status: ${o.line}")
          StatusRouted(None, None, StatusRouted.Note(o.line))
        case o => StatusRouted(None, None, StatusRouted.Note(o.line))

  // ── close ──────────────────────────────────────────────────────────────────────────────────────

  /** Bookkeeping, as the skills do it: the per-source lint (each hit a warning), the acquisition item the run was
    * started for ticked off, and a `log` line. Calibre is untouched: a source not in Calibre is already what
    * `histmersion calibre gaps` reports, host-side.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def close(ctx: RunCtx, ck: Citekey, p: Patched, r: StatusRouted)(using
      WritePermit
  ): Either[Problems, Closed] =
    // a dry run patched nothing: the lint would only describe the source as it was before
    val lint = if ctx.dryRun then Nil
    else
      site.sources.get(ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)).toOption.toList.flatMap { s =>
        closeLint(FindingCount(s.findings.size), Loaded.HasPeriod(s.source.periodStart.isDefined), s.source.status)
      }
    lint.foreach(l => ctx.warn(s"lint: $l"))
    val acquire = ctx.run.acquireItem.map(id =>
      ctx.write(
        s"tick off acquisition item $id",
        Json
          .obj("item" -> Json.fromInt(id.value), "fulfilledBy" -> Json.fromString(ck.value))
      )(
        tickOff(id, ck, ctx.run.id)
      )(s"acquisition item $id not ticked off (${AgentsMarkdown.DryRunNote})")
    )
    acquire.filter(_.contains("NOT")).foreach(ctx.warn)
    val what = if p.fieldsSet.isEmpty then "nothing new" else p.fieldsSet.mkString(", ")
    val approval = r.approval.fold("")(id => s"; its status proposal waits for approval $id")
    val line = s"enrich-source [[$ck]] (agent run ${ctx.run.id.value.take(8)}): $what$approval" +
      acquire.fold("")(a => s"; $a")
    PipelineDef
      .log(site.workLists, ctx, PipelineDef.LogLine(line))
      .map(_ => Closed(Closed.Line(line), lint.map(d => Closed.Lint(d.value)), acquire.map(Closed.Acquire(_))))

  /** Tick acquisition item `id` off as fulfilled by `ck` — the same rule an OCR upload follows. It never fails the run:
    * an item done or deleted meanwhile is left as it is, and said so.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def tickOff(id: QueueItem.Id, ck: Citekey, runId: RunInfo.Id): String =
    QueueItem.fulfillable(id, site.queues.get(QueueItemId(id.value))) match
      case Left(m)     => s"acquisition item $id NOT ticked off: ${m.sentence}"
      case Right(item) =>
        site.workLists.patch(
          QueueItemId(id.value),
          item.fulfil(com.histmersion.api.library.Citekey(ck.value), Some(QueueItem.ClosedBy(runId.value)))
        ) match
          case Right(_) => s"ticked off acquisition item $id"
          case Left(e)  => s"acquisition item $id NOT ticked off: ${e.problems.sentences.mkString("; ")}"

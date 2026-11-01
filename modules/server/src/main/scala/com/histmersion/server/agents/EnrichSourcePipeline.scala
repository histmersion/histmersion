package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.domain.automation.worklists.QueueItemId

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.{ApprovalKind, Pipeline, Role}
import com.histmersion.domain.EnrichSource.*
import com.histmersion.db.AgentRepo
import com.histmersion.server.Handlers
import io.circe.syntax.*

import javax.sql.DataSource
import com.histmersion.values.SourceStatus

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
final class EnrichSourcePipeline(
    h: Handlers,
    ds: DataSource,
    cfg: => AgentsConfig,
    upstream: () => AgentUpstream,
    approvals: SourceProposals
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.EnrichSource
  private val name = pipeline.wire

  def run(ctx: RunCtx): Either[Halt, String] =
    val ck = ctx.run.target
    for
      loaded <- ctx.code[Loaded]("load")(load(ck))
      _ <- ctx.code[MetaChecked]("metadata")(Right(metadata(ctx, loaded)))
      classified <- ctx.llm[Classification, Classified](
        "classify",
        StepAgent.EnrichClassify,
        classifyInput(loaded),
        LibraryAnswers.classification
      )((c, last) => checkClassification(c, cfg.verify.maxAbsYear, last).map(Checked(_, _)))
      verified <- findings(ctx, loaded, classified)
      geocoded <- ctx.code[Geocoded]("geocode")(Right(geocode(ctx, verified)))
      notes <- ctx.llm[Notes, String](
        "notes",
        StepAgent.EnrichNotes,
        notesInput(loaded, classified, verified),
        LibraryAnswers.notes
      )((n, _) => checkNotes(n, isCitekey, slug => AgentRepo.slugExists(ds, slug)).map(Checked(_)))
      patched <- ctx.code[Patched]("patch")(write(ctx, ck, loaded, classified, geocoded.findings, notes))
      _ <- ctx.code[Upstreamed]("refs")(Right(refs(ctx, loaded)))
      assessed <- ctx.code[Upstreamed]("assess")(Right(assess(ctx, loaded)))
      routed <- ctx.code[StatusRouted]("status")(Right(status(ctx, ck, classified, patched, assessed)))
      closed <- ctx.code[Closed]("close")(close(ctx, ck, patched, routed))
    yield closed.line

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(ck: String): Either[String, Loaded] =
    h.getSource(ck, body = true).left.map(_.message).flatMap { sw =>
      val s = sw.source
      val body = s.ocrMarkdown.map(_.trim).getOrElse("")
      if body.isEmpty then Left(s"$ck has no text — ingest or OCR it first")
      else
        Right(
          Loaded(
            citekey = s.citekey,
            title = s.title,
            authors = s.authors,
            year = s.year,
            venue = s.venue,
            status = s.status.wire,
            hasSummary = s.summary.exists(_.trim.nonEmpty),
            hasNotes = s.notes.exists(_.trim.nonEmpty),
            hasPeriod = s.periodStart.isDefined,
            period = for a <- s.periodStart; b <- s.periodEnd yield Period(a, b),
            topics = s.topics,
            regions = s.regions,
            existingClaims = sw.findings.map(_.claim),
            updatedAt = s.updatedAt,
            head = body.take(cfg.enrich.headChars),
            windows = windows(body, cfg.enrich.windowChars, cfg.enrich.maxWindows),
            tags = s.tags,
            doi = s.doi
          )
        )
    }

  private def meta(l: Loaded): String =
    s"citekey: ${l.citekey}\ntitle: ${l.title}\nauthors: ${l.authors.mkString("; ")}\n" +
      s"year: ${l.year.fold("unknown")(_.toString)}\nvenue: ${l.venue.getOrElse("unknown")}"

  private def classifyInput(l: Loaded): String =
    s"Classify this source.\n\n${meta(l)}\n\nThe opening of its text:\n\n${Prompt.data("source-text", l.head)}"

  /** One LLM step per window. Each answer's quotes are checked against THAT window, and its places against the
    * gazetteer the `geocode` step will use (#1424: asking before the answer is accepted is the only point where a
    * better name can still be asked for).
    */
  private def findings(ctx: RunCtx, l: Loaded, c: Classified): Either[Halt, List[VerifiedFinding]] =
    val existing = l.existingClaims.toSet
    val guards = cfg.enrich.guards.guards((name, kind, at) => h.geocode(name, kind, at).isDefined)
    l.windows.foldLeft[Either[Halt, List[VerifiedFinding]]](Right(Nil)) { (acc, w) =>
      acc.flatMap { sofar =>
        val user = s"Extract the findings of this stretch of the source.\n\n${meta(l)}\n" +
          w.heading.fold("")(hd => s"section: $hd\n") +
          s"stretch ${w.index} of ${l.windows.size}\n\n${Prompt.data("source-text", w.text)}" + ctx.notes
        // the VERIFIED list is what is checkpointed: a resume replays it without re-checking
        ctx
          .llm[Findings, VerifiedFindings](
            s"findings-${w.index}",
            StepAgent.EnrichFindings,
            user,
            LibraryAnswers.findings
          ) { (f, last) =>
            checkFindings(
              f,
              w,
              cfg.verify.quoteMinRatio,
              cfg.verify.maxAbsYear,
              l.period.orElse(c.period),
              existing ++ sofar.map(_.claim),
              guards,
              last
            ).map(Checked(_, _))
          }
          .map(v => sofar ++ v.findings)
      }
    }

  /** Places through the gazetteer ([[Handlers.geocode]], #981), never the model's coordinates: the model's place kind
    * and the finding's period steer the choice. The finding keeps the name that was ASKED as its `region` (the chosen
    * place's label can be a Pleiades string of five names); the choice — place id and label — goes into the checkpoint,
    * so a person can audit it. An unknown place keeps its name with no point, and an approximate choice (a qualifier
    * dropped, a placeholder point) is kept with a warning; both are said.
    */
  private def geocode(ctx: RunCtx, fs: List[VerifiedFinding]): Geocoded =
    val placed = fs.map { f =>
      val hit = f.place.flatMap(p => h.geocode(p, f.placeKind.getOrElse(Geocode.KindHint.Unknown), f.period))
      f.place.foreach { p =>
        hit match
          case None => ctx.warn(s"geocode: no gazetteer place named '$p' (finding kept without a point)")
          case Some(r) if r.isApproximate => ctx.warn(s"geocode: ${r.describe}")
          case Some(_)                    => ()
      }
      val input = FindingInput(
        claim = f.claim,
        region = f.place,
        lon = hit.map(_.lon),
        lat = hit.map(_.lat),
        periodStart = f.start,
        periodEnd = f.end,
        periodName = f.periodName,
        tags = f.tags,
        confidence = Some(f.confidence)
      )
      val choice = f.place.map(p =>
        GeocodeChoice(
          f.claim,
          p,
          hit.map(_.placeId),
          hit.map(_.label),
          hit.filter(_.isApproximate).map(_.approximate.map(_.describe).mkString("; "))
        )
      )
      (input, choice)
    }
    Geocoded(placed.map(_._1), placed.flatMap(_._2))

  private def notesInput(l: Loaded, c: Classified, fs: List[VerifiedFinding]): String =
    s"Write the notes for [[${l.citekey}]].\n\n${meta(l)}\n\nsummary: ${c.summary}\n\nverified findings:\n" +
      fs.map(f => s"- ${f.claim} (quote: \"${f.quote}\")").mkString("\n")

  private def isCitekey(s: String): Boolean = h.getSource(s, body = false).isRight

  /** The single write. On a 412 (someone wrote the source since `load`) the source is re-read and the patch rebuilt
    * from the CHECKPOINTED answers — no LLM call — and sent once more.
    */
  private def write(
      ctx: RunCtx,
      ck: String,
      l: Loaded,
      c: Classified,
      fs: List[FindingInput],
      notes: String
  ): Either[String, Patched] =
    val (p, fields, proposal) = patch(l, c, fs, Option(notes).filter(_.nonEmpty))
    // a dry run's simulated patch "succeeds": its step says what it would have set
    ctx.write(s"patch [[$ck]]: ${if fields.isEmpty then "nothing new" else fields.mkString(", ")}", p.asJson)(
      h.patchSource(ck, p).map(_ => ())
    )(Right(())) match
      case Right(_)                                           => Right(Patched(fs.size, fields, proposal))
      case Left(e) if e.code.contains(Write.CodePrecondition) =>
        load(ck).flatMap { fresh =>
          val again = fresh.copy(windows = Nil, head = "")
          val existing = fresh.existingClaims.map(AgentVerify.normalise).toSet
          val (p2, fields2, proposal2) = patch(
            again,
            c,
            fs.filterNot(f => existing.contains(AgentVerify.normalise(f.claim))),
            Option(notes).filter(_.nonEmpty)
          )
          h.patchSource(ck, p2)
            .left
            .map(_.message)
            .map(_ => Patched(p2.findings.fold(0)(_.add.size), fields2, proposal2))
        }
      case Left(e) => Left(e.message)

  // ── upstream: metadata, refs, assess ──────────────────────────────────────────────────────────

  /** Why an upstream step cannot run, if it cannot: switched off, or no DOI to ask about. */
  private def upstreamBlocked(l: Loaded): Option[String] =
    if !cfg.enrich.upstream then Some("upstream checks are off (agents.enrich.upstream)")
    else if l.doi.forall(_.trim.isEmpty) then Some("the source has no DOI")
    else None

  /** The record against OpenAlex/Crossref. Differences are WARNINGS for a person (a wrong year can mean a wrong
    * citekey, which only a person renames); nothing is written.
    */
  private def metadata(ctx: RunCtx, l: Loaded): MetaChecked =
    upstreamBlocked(l) match
      case Some(why) => MetaChecked(checked = false, Nil, why)
      case None      =>
        upstream().meta(l.doi.get) match
          case Left(m) =>
            ctx.warn(s"metadata: not checked — $m")
            MetaChecked(checked = false, Nil, m)
          case Right(w) =>
            val diffs = compareMetadata(l.citekey, l.title, l.authors, l.year, w)
            diffs.foreach(d => ctx.warn(s"metadata: $d"))
            MetaChecked(checked = true, diffs)

  /** `refs --apply`: the deposited reference list becomes `references` edges (a network failure is a warning, never a
    * failed run — the enrichment is already written).
    */
  private def refs(ctx: RunCtx, l: Loaded): Upstreamed =
    upstreamBlocked(l) match
      case Some(why) => Upstreamed(ran = false, why)
      case None      =>
        upstream().refs(l.citekey, apply = !ctx.dryRun) match
          case Left(m) =>
            ctx.warn(s"refs: $m")
            Upstreamed(ran = false, m)
          case Right(r) =>
            if ctx.dryRun then
              ctx.write(
                s"refs [[${l.citekey}]] applied: ${r.resolved} reference edge(s) to sources in the corpus",
                r.asJson
              )(())(())
            Upstreamed(
              ran = true,
              s"${r.count} reference(s), ${r.resolved} in the corpus, ${r.edgesAdded} edge(s) added"
            )

  /** `assess --apply`: a retraction overwrites the status (the assess rule) and flags the run. */
  private def assess(ctx: RunCtx, l: Loaded): Upstreamed =
    upstreamBlocked(l) match
      case Some(why) => Upstreamed(ran = false, why)
      case None      =>
        upstream().assess(l.citekey, apply = !ctx.dryRun) match
          case Left(m) =>
            ctx.warn(s"assess: $m")
            Upstreamed(ran = false, m)
          case Right(a) =>
            if a.isRetracted then ctx.warn(s"assess: RETRACTED — ${a.recommendation}")
            if ctx.dryRun then ctx.write(s"assess [[${l.citekey}]] applied: verdict ${a.verdict}", a.asJson)(())(())
            Upstreamed(
              ran = true,
              s"verdict ${a.verdict}${a.applied.fold("")(st => s", status set to $st")}",
              a.isRetracted
            )

  /** The classify step's status, if the patch did not already fill it: through the approval gate. A retraction found by
    * `assess` outranks any proposal.
    */
  private def status(ctx: RunCtx, ck: String, c: Classified, p: Patched, a: Upstreamed): StatusRouted =
    if p.fieldsSet.contains("status") then StatusRouted(Some(c.status.wire), None, "filled by the patch")
    else if c.status == SourceStatus.Unassessed then StatusRouted(None, None, "no status proposed")
    else if a.retracted then StatusRouted(None, None, s"${c.status.wire} not proposed: the source is retracted")
    else
      val proposal = AgentProposals.Proposal(
        ApprovalKind.StatusChange,
        ck,
        status = Some(c.status),
        evidence = Option(c.statusEvidence).filter(_.trim.nonEmpty).getOrElse("(the model gave no evidence)")
      )
      approvals.propose(ctx, proposal) match
        case o @ SourceProposals.Outcome.Applied(w)      => StatusRouted(Some(c.status.wire), None, o.line)
        case o @ SourceProposals.Outcome.Recorded(id, _) =>
          ctx.warn(s"status ${c.status.wire} not applied: approval $id waits for a person")
          StatusRouted(None, Some(id), o.line)
        case o @ SourceProposals.Outcome.Failed(_) =>
          ctx.warn(s"status: ${o.line}")
          StatusRouted(None, None, o.line)
        case o => StatusRouted(None, None, o.line)

  // ── close ──────────────────────────────────────────────────────────────────────────────────────

  /** Bookkeeping, as the skills do it: the per-source lint (each hit a warning), the acquisition item the run was
    * started for ticked off, and a `log` line. Calibre is untouched: a source not in Calibre is already what
    * `histmersion calibre gaps` reports, host-side.
    */
  private def close(ctx: RunCtx, ck: String, p: Patched, r: StatusRouted): Either[String, Closed] =
    // a dry run patched nothing: the lint would only describe the source as it was before
    val lint = if ctx.dryRun then Nil
    else
      h.getSource(ck, body = false).toOption.toList.flatMap { s =>
        closeLint(s.findings.size, s.source.periodStart.isDefined, s.source.status)
      }
    lint.foreach(l => ctx.warn(s"lint: $l"))
    val acquire = ctx.run.acquireItem.map(id =>
      ctx.write(s"tick off acquisition item $id", io.circe.Json.obj("item" -> id.asJson, "fulfilledBy" -> ck.asJson))(
        tickOff(id, ck, ctx.run.id)
      )(s"acquisition item $id not ticked off (${AgentsMarkdown.DryRunNote})")
    )
    acquire.filter(_.contains("NOT")).foreach(ctx.warn)
    val what = if p.fieldsSet.isEmpty then "nothing new" else p.fieldsSet.mkString(", ")
    val approval = r.approval.fold("")(id => s"; its status proposal waits for approval $id")
    val line = s"enrich-source [[$ck]] (agent run ${ctx.run.id.take(8)}): $what$approval" +
      acquire.fold("")(a => s"; $a")
    Pipelines.log(h.workLists, ctx, line).map(_ => Closed(line, lint, acquire))

  /** Tick acquisition item `id` off as fulfilled by `ck` — the same rule an OCR upload follows. It never fails the run:
    * an item done or deleted meanwhile is left as it is, and said so.
    */
  private def tickOff(id: Int, ck: String, runId: String): String =
    Queue.fulfillable(id, h.queues.get(QueueItemId(id))) match
      case Left(m)     => s"acquisition item $id NOT ticked off: $m"
      case Right(item) =>
        h.ops.queuePatch(id, Queue.fulfil(item, ck, Some(runId))) match
          case Right(_) => s"ticked off acquisition item $id"
          case Left(e)  => s"acquisition item $id NOT ticked off: ${e.message}"

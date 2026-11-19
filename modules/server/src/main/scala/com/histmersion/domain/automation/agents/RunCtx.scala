package com.histmersion
package domain.automation.agents
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{External, Problem, ProblemLog, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.managers.Zero
import com.histmersion.server.agents.Codex

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, Semaphore}
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.automation.Run
import com.histmersion.util.jobs.Job
import com.histmersion.util.jobs.JobRepo
import com.histmersion.util.jobs.JobRow
import com.histmersion.domain.automation.approvals.ApprovalQuery
import io.github.iltotore.iron.constraint.any.Pure

/** What a running pipeline sees: its run, and the two kinds of step. Every step is checkpointed in `agent_steps`;
  * before running step `n` the context looks for a finished attempt of `n` and, if there is one, REPLAYS its output
  * instead — so a resumed run never repeats a completed step, and never pays for an LLM answer twice.
  */
final class RunCtx private[automation] (val run: RunInfo, engine: Engine):
  private val ds = engine.ds

  /** The approvals a run's steps open and wait on (and a pipeline records). */
  def approvalStore: ApprovalService = engine.agents.approvalStore
  private val cfg = engine.cfg

  /** This run's id, read back from the store it was saved in. */
  val runId: Run.Id = Run.Id.assume(run.id.value)
  private val jobId: Job.Id = Job.Id.assume(run.id.value)
  private val agentJobKind: Job.Kind = Job.Kind.assume(JobKind)

  /** The next step's number. A `var` because the pipeline is straight-line code calling steps in order; the counter IS
    * that order. Confined to the run's own thread.
    */
  private var nextSeq = 0

  private def claimSeq(): AgentRepo.StepRecord.Seq = { nextSeq += 1; AgentRepo.StepRecord.Seq(nextSeq) }

  /** The next `n` step numbers, for steps that then run IN PARALLEL ([[llmAt]] inside one [[RunCtx.parallel]]):
    * numbering happens here, in program order, so a resumed run lines its parallel steps up with their checkpoints
    * whichever of them finished first.
    */
  def reserve(n: Int): List[AgentRepo.StepRecord.Seq] = List.fill(math.max(0, n))(claimSeq())

  /** Run steps concurrently (Ox `par`, inside the run's own thread's scope: a cancel interrupts them all). A halt wins
    * over the others' results — cancel first, then a failure (terminal), then a pause — but every step that finished is
    * checkpointed either way, so a resume re-runs only the ones that did not.
    */
  def parallel[A](steps: List[() => Either[Halt, A]]): Either[Halt, List[A]] =
    // each step carries the run's generation (its database, its config) onto its fork
    val results = ox.par(steps.map { s =>
      val c = engine.agents.pins.carry(s())
      () => c.run()
    })
    val halts = results.collect { case Left(h) => h }
    halts
      .collectFirst { case h @ Halt.Cancel => h }
      .orElse(halts.collectFirst { case h: Halt.Fail => h })
      .orElse(halts.headOption) match
      case Some(h) => Left(h)
      case None    => Right(results.collect { case Right(a) => a }.toList)

  def warn(w: String): Unit = engine.agents.jobRuns.warn(agentJobKind, runId, JobRepo.WarningText(w.take(500)))

  /** A DRY run (#992) reads and calls models as usual but writes nothing to the corpus. */
  def dryRun: Boolean = run.dryRun.value

  /** The writes this run's current step skipped (a dry run), recorded with the step when it ends. A concurrent queue: a
    * step's body runs on the run's thread, but nothing stops a parallel step from asking.
    */
  private val skipped = java.util.concurrent.ConcurrentLinkedQueue[WouldWrite]()

  private def drainSkipped(): List[WouldWrite] =
    Iterator.continually(skipped.poll()).takeWhile(_ != null).toList

  /** THE write gate: every write a pipeline makes to the corpus (a patch, an article, a queue item, an approval, a
    * goal, a link, an upstream `--apply`) goes through here. In a dry run `perform` is not called: `what` and `payload`
    * (the whole write, as JSON) are recorded on the step under "dry run: not written", and `simulated` stands in for
    * the result.
    */
  def write[A](what: String, payload: => Json)(perform: => A)(simulated: => A): A =
    if !dryRun then perform
    else
      skipped.add(WouldWrite(WouldWrite.What(what), WouldWrite.Payload(payload.noSpaces)))
      simulated

  def progress(p: String): Unit = engine.agents.jobRuns.progress(agentJobKind, runId, JobRow.Progress(p))

  /** Replay step `seq` if it finished before; a recorded step of another NAME means the pipeline changed under a paused
    * run, which is refused rather than misread.
    */
  private def replay[O: JsonValueCodec](
      seq: AgentRepo.StepRecord.Seq,
      name: AgentRepo.StepRecord.Name
  ): Option[Either[Halt, O]] =
    engine.agents.runs.checkpoint(jobId, seq).map { (recorded, json) =>
      if recorded != name then
        Left(
          Halt.Fail(
            Problem.Run.Reordered(Problem.Count.of(seq), Problem.Run.Step.of(recorded), Problem.Run.Step.of(name))
          )
        )
      else
        RawJson
          .decode[O](json.value)
          .left
          .map(e =>
            Halt.Fail(
              Problem.Run
                .CheckpointUnread(Problem.Count.of(seq), Problem.Run.Step.of(name), External.Reported.of(s"$e"))
            )
          )
    }

  /** A deterministic step. Its output is checkpointed, so a side effect in `body` (a patch, a queue entry) happens at
    * most once per run.
    */
  def code[O: JsonValueCodec](name: AgentRepo.StepRecord.Name)(body: => Either[Problems, O]): Either[Halt, O] =
    val seq = claimSeq()
    replay[O](seq, name).getOrElse {
      engine.checkControl(runId).flatMap { _ =>
        engine.beat(runId, Some(Engine.BeatSeq(seq.value)))
        progress(name.value)
        val started = java.time.Instant.now(engine.agents.clock)
        val out =
          try body
          catch
            case e: InterruptedException => throw e
            case e: Exception            => Left(unexpected(e))
        engine.beat(runId)
        val attempt = AgentRepo.StepRecord.Attempt(engine.agents.runs.attempts(jobId, seq).value + 1)
        val would = drainSkipped()
        out match
          case Right(o) =>
            engine.agents.runs.record(
              jobId,
              AgentRepo.StepRecord(
                seq,
                attempt,
                name,
                StepKind.Code,
                StepStatus.Done,
                Some(AgentRepo.StepRecord.Output(o.asJson.noSpaces)),
                None,
                None,
                Usage.Zero,
                BigDecimal(0),
                None,
                started,
                wouldWrite = would
              ),
              Nil
            )
            Right(o)
          case Left(m) =>
            engine.agents.runs.record(
              jobId,
              AgentRepo.StepRecord(
                seq,
                attempt,
                name,
                StepKind.Code,
                StepStatus.Failed,
                None,
                None,
                None,
                Usage.Zero,
                BigDecimal(0),
                Some(Problem.Run.Failed(m.toNonEmpty)),
                started,
                wouldWrite = would
              ),
              Nil
            )
            Left(Halt.Fail(Problem.Run.AtStep(Problem.Run.Step.of(name), m.toNonEmpty)))
      }
    }

  /** A BLOCKING approval step (docs/research-embedded-agents.md §14): the first time the run reaches it, `payload` (the
    * proposal as JSON) is recorded as an approval of `kind` about `subject`, and the run stops in `waiting` without
    * holding a worker. Deciding the approval re-queues the run; it replays its finished steps, reaches this step again
    * and gets the decision, which is then checkpointed like any output. In `auto` mode the approval is recorded already
    * approved and the run goes on at once. A resumed run finds the approval it opened, never a second one. A DRY run
    * opens none: the proposal is recorded on the step as not written, and the run goes on as if it were approved, so
    * the steps after it run too (and skip their writes).
    */
  def approval(name: AgentRepo.StepRecord.Name, kind: ApprovalKind, subject: ApprovalQuery.Subject, mode: ApprovalMode)(
      payload: => Either[Problems, String]
  ): Either[Halt, Decided] =
    val seq = claimSeq()
    replay[Decided](seq, name).getOrElse {
      engine.checkControl(runId).flatMap { _ =>
        val started = java.time.Instant.now(engine.agents.clock)
        def done(d: Decided): Either[Halt, Decided] =
          engine.agents.runs.record(
            jobId,
            AgentRepo.StepRecord(
              seq,
              AgentRepo.StepRecord.Attempt(engine.agents.runs.attempts(jobId, seq).value + 1),
              name,
              StepKind.Code,
              StepStatus.Done,
              Some(AgentRepo.StepRecord.Output(d.asJson.noSpaces)),
              None,
              None,
              Usage.Zero,
              BigDecimal(0),
              None,
              started,
              wouldWrite = drainSkipped()
            ),
            Nil
          )
          Right(d)
        def waitFor(a: ApprovalInfo): Either[Halt, Decided] =
          progress(s"$name: waiting for approval ${a.id}")
          Left(Halt.Wait(Halt.Wait.Id(a.id.value), Halt.Wait.What(s"$name: approval ${a.id} waits for a person")))
        approvalStore.forStep(jobId, ApprovalService.StepSeq(seq.value)) match
          case Some(a) if a.state == ApprovalState.Open.wire => waitFor(a)
          case Some(a)                                       =>
            done(
              Decided(
                Decided.Approved(a.state == ApprovalState.Approved.wire),
                Decided.Approval(a.id.value),
                a.note.map(n => Decided.Note(n.value))
              )
            )
          case None =>
            val body =
              try payload
              catch
                case e: InterruptedException => throw e
                case e: Exception            => Left(unexpected(e))
            body match
              case Left(m) =>
                engine.agents.runs.record(
                  jobId,
                  AgentRepo.StepRecord(
                    seq,
                    AgentRepo.StepRecord.Attempt(engine.agents.runs.attempts(jobId, seq).value + 1),
                    name,
                    StepKind.Code,
                    StepStatus.Failed,
                    None,
                    None,
                    None,
                    Usage.Zero,
                    BigDecimal(0),
                    Some(Problem.Run.Failed(m.toNonEmpty)),
                    started
                  ),
                  Nil
                )
                Left(Halt.Fail(Problem.Run.AtStep(Problem.Run.Step.of(name), m.toNonEmpty)))
              case Right(json) if dryRun =>
                skipped.add(
                  WouldWrite(
                    WouldWrite.What(s"${kind.wire} approval for $subject (treated as approved)"),
                    WouldWrite.Payload(json)
                  )
                )
                done(Decided(Decided.Approved(true), Decided.Approval(0L), Some(Decided.Note(Engine.DryRunApproval))))
              case Right(json) if mode == ApprovalMode.Auto =>
                val a = approvalStore.openForStep(
                  jobId,
                  ApprovalService.StepSeq(seq.value),
                  kind,
                  subject,
                  ApprovalService.Payload(json),
                  ApprovalState.Approved,
                  Some(ApprovalService.StepOutcome("approved automatically (agents.approvals)"))
                )
                done(Decided(Decided.Approved(true), Decided.Approval(a.id.value)))
              case Right(json) =>
                waitFor(
                  approvalStore.openForStep(
                    jobId,
                    ApprovalService.StepSeq(seq.value),
                    kind,
                    subject,
                    ApprovalService.Payload(json)
                  )
                )
      }
    }

  /** An LLM step run as one canonical agent definition (`agents/<name>.md`): its prompt (rendered for the runtime the
    * model chain picks), its role (which chain), its tools (the ONLY ones offered) — plus `user`, the message carrying
    * the data, the typed answer `A` the model must return, and `validate` — the code check every answer must pass,
    * which turns it into the VERIFIED value `O` that is checkpointed and returned. A rejected answer is retried (up to
    * `agents.step.max-retries`) with the problems appended; on the LAST attempt `validate` is told so (`last = true`)
    * and should keep what passed rather than reject. Each attempt is recorded with its tokens and cost.
    */
  /** The person's notes for this run (manager hints snapshotted at dispatch, `RunParams.notes`), as the block a step
    * appends to its USER message — never the system prompt: they are fenced, and subordinate to the record. Empty when
    * the run has none.
    */
  def notes: String = Prompt.notes(run.params.noteList.map(_.value))

  def llm[A, O: JsonValueCodec](
      name: AgentRepo.StepRecord.Name,
      agent: StepAgent,
      user: Prompt.UserText,
      schema: ResponseSchema[A],
      maxTokens: Option[RunCtx.MaxTokens] = None,
      turns: Option[AgentRepo.StepRecord.Turns] = None
  )(validate: (A, RunCtx.LastAttempt) => Either[List[Problem], Checked[O]]): Either[Halt, O] =
    llmAt(claimSeq(), name, agent, user, schema, maxTokens, turns)(validate)

  /** [[llm]] at a step number taken earlier with [[reserve]] — the form a parallel step uses. `maxTokens` overrides
    * `agents.step.max-tokens` for a step whose answer is long (a whole article); `turns` overrides the agent's
    * `max-turns` for a step whose work grows with its input (the fixer: one more turn per finding, see
    * [[Engine.stepTurns]]).
    */
  def llmAt[A, O: JsonValueCodec](
      seq: AgentRepo.StepRecord.Seq,
      name: AgentRepo.StepRecord.Name,
      agent: StepAgent,
      user: Prompt.UserText,
      schema: ResponseSchema[A],
      maxTokens: Option[RunCtx.MaxTokens] = None,
      turns: Option[AgentRepo.StepRecord.Turns] = None
  )(validate: (A, RunCtx.LastAttempt) => Either[List[Problem], Checked[O]]): Either[Halt, O] =
    llmAtSeen[A, O](seq, name, agent, user, schema, maxTokens, turns)((a, last, _) => validate(a, last))

  /** [[llmAt]] whose `validate` also sees the tool calls the step has made so far — this attempt's and the earlier
    * ones' of this execution, as the runtime reported them (sttp-ai's loop, Claude Code's stream-json, Codex's and
    * opencode's events), so an answer that says it looked something up can be held to a call that did (#1840). A retry
    * amends its answer without repeating its lookups, so the earlier attempts' calls count.
    */
  def llmAtSeen[A, O: JsonValueCodec](
      seq: AgentRepo.StepRecord.Seq,
      name: AgentRepo.StepRecord.Name,
      agent: StepAgent,
      user: Prompt.UserText,
      schema: ResponseSchema[A],
      maxTokens: Option[RunCtx.MaxTokens] = None,
      turns: Option[AgentRepo.StepRecord.Turns] = None
  )(validate: (A, RunCtx.LastAttempt, List[ToolUse]) => Either[List[Problem], Checked[O]]): Either[Halt, O] =
    replay[O](seq, name).getOrElse {
      engine.checkControl(runId).flatMap { _ =>
        engine.beat(runId, Some(Engine.BeatSeq(seq.value)))
        val defs = engine.agents.definitions
        (for
          d <- defs.agent(agent)
          sys <- defs.system(d, ToolStyle.Bare)
        yield (d, Prompt(Prompt.System(sys), Prompt.User(user.value)))).left
          .map(m => Halt.Fail(Problem.Run.AtStep(Problem.Run.Step.of(name), m.toNonEmpty)))
          .flatMap { (d, prompt) =>
            attempts(
              seq,
              name,
              d,
              prompt,
              schema,
              maxTokens.getOrElse(LlmCall.MaxTokens(cfg.step.maxTokens.value)),
              Engine.stepTurns(d, turns.map(t => AgentDef.MaxTurns(t.value))),
              validate,
              Engine.ceiling(Engine.CapKeyOverridden(maxTokens.isDefined))
            )
          }
      }
    }

  /** How many attempts of one step may end on a model's allowance or plan limit (each moving to the chain's next model)
    * before the step gives up — a guard against a chain that never settles.
    */
  private val MaxModelSwitches = 8

  /** Models of a role's chain this run has already been told it passed over (one warning each). */
  private val warnedSkips = java.util.concurrent.ConcurrentHashMap.newKeySet[Picked.Skipped]()

  /** The model for the next call of `role`: the first of its chain with allowance left ([[ModelPicker.pick]]). None
    * left = the run pauses until the earliest window frees.
    */
  private def pickModel(name: AgentRepo.StepRecord.Name, role: Role): Either[Halt, Picked] =
    engine.agents.picker.pick(
      role,
      run.modelFor(role).map(m => AgentsControllers.ModelName(m.value)),
      onRefresh = n => warn(s"${role.wire}: $n")
    ) match
      case Left(PickFailure.Unavailable(m)) =>
        Left(Halt.Fail(Problem.Run.AtStep(Problem.Run.Step.of(name), m.toNonEmpty)))
      case Left(PickFailure.Exhausted(m, at, why)) =>
        Left(Halt.Pause(why, Problem.Run.AtStep(Problem.Run.Step.of(name), NonEmptyVector.of(m)), at))
      case Right(p) =>
        p.skipped
          .filter(warnedSkips.add)
          .foreach(why => warn(s"${role.wire}: passed over $why; used ${p.client.model.wire}"))
        Right(p)

  private def attempts[A, O: JsonValueCodec](
      seq: AgentRepo.StepRecord.Seq,
      name: AgentRepo.StepRecord.Name,
      agent: AgentDef,
      prompt: Prompt,
      schema: ResponseSchema[A],
      maxTokens: RunCtx.MaxTokens,
      turns: AgentDef.MaxTurns,
      validate: (A, RunCtx.LastAttempt, List[ToolUse]) => Either[List[Problem], Checked[O]],
      ceiling: Problem.Run.Ceiling
  ): Either[Halt, O] =
    val role = agent.role
    val maxAttempts = 1 + math.max(0, cfg.step.maxRetries.value)
    // a resumed step's earlier attempts count as answers judged: its retries are not handed out twice
    val before = engine.agents.runs.attempts(jobId, seq).value
    // tail-recursive retry loop over attempts; `problems` are the previous attempt's rejections and
    // `tries` counts the answers judged so far (an attempt cut short by a model's allowance or plan
    // limit is not an answer: it moves on to the chain's next model without using up a retry)
    // `seen`: the tool calls of this step's attempts so far, oldest first (what `validate` is shown)
    @annotation.tailrec
    def go(
        attempt: Int,
        tries: Int,
        problems: List[Problem],
        previous: Option[String],
        seen: List[ToolUse]
    ): Either[Halt, O] =
      engine.checkControl(runId).flatMap(_ => engine.checkBudget(runId)).flatMap(_ => pickModel(name, role)) match
        case Left(h)                                                       => Left(h)
        case Right(_) if attempt - before > maxAttempts + MaxModelSwitches =>
          Left(
            Halt.Fail(Problem.Run.Unsettled(Problem.Run.Step.of(name), Problem.Count((attempt - before - 1).toLong)))
          )
        case Right(picked) =>
          val (client, billing) = (picked.client, picked.billing)
          val last = tries + 1 >= maxAttempts
          val user =
            if problems.isEmpty then prompt.user else Prompt.User(prompt.user.value + Prompt.retry(problems, previous))
          progress(s"$name (attempt $attempt, ${client.model.wire})")
          val started = java.time.Instant.now(engine.agents.clock)
          val budget = engine.budgetInterceptor(runId, billing)
          val latency = LatencyInterceptor(engine.llmSlots, () => engine.beat(runId))(using engine.agents.timer)
          val allowance = AllowanceGuard(
            picked.headroomTokens.map(h => AllowanceGuard.HeadroomTokens(h.value)),
            picked.headroomUsd,
            billing
          )
          val first = FirstCallProbe()
          // the agent's own `max-turns` (or the step's override): a reviewer that must look up 6–10
          // claims needs more than a one-shot step
          val call = LlmCall[A](
            LlmCall.System(prompt.system.value),
            LlmCall.User(user.value),
            engine.agents.toolbox.tools(engine.offered(run, agent)),
            schema,
            LlmCall.MaxIterations(turns.value),
            maxTokens,
            List(latency, budget, allowance, first),
            Some(agent),
            Some(RunScope(RunScope.Run(runId.value), RunScope.Step(seq.value)))
          )
          // a plan pool takes at most its `max-calls-in-flight` calls at once, across every run
          val poolSlot = engine.agents.pools.poolOf(client.model).map(engine.agents.pools.slot)
          AgentsControllers.call(client.model) {
            poolSlot.foreach(engine.acquireBeating(runId, _))
            try client.run(call)
            finally poolSlot.foreach(_.release())
          } match
            case Left(Left(limit)) =>
              val until = engine.agents.picker.cool(client.model, limit.until)
              record(
                seq,
                attempt,
                name,
                client,
                prompt,
                Usage.Zero,
                billing,
                None,
                Some(Problem.Run.LimitReached(limit.reason, Problem.EpochMs(until.toEpochMilli))),
                started,
                Nil
              )
              go(attempt + 1, tries, problems, previous, seen)
            case Left(Right(e)) =>
              record(
                seq,
                attempt,
                name,
                client,
                prompt,
                Usage.Zero,
                billing,
                None,
                Some(Problem.Run.Failed(e.problems)),
                started,
                Nil
              )
              Left(
                Halt.Pause(
                  PauseReason.Provider,
                  Problem.Run.ProviderDown(Problem.Run.Step.of(name), e.problems.toNonEmpty)
                )
              )
            case Right(res) =>
              val use = LlmClient.usage(res.usage)
              val msgs = Trace.messages(res.history, latency.millis, cfg.trace.maxToolResultChars.value)
              val served =
                Served(LlmClient.servedBy(res.llmCalls), first.tokens, Some(AgentRepo.StepRecord.Turns(res.iterations)))
              val calls =
                seen ++ res.toolCalls.toList.map(c => ToolUse(ToolUse.Tool(c.toolName), ToolUse.Input(c.input)))
              res.finishReason match
                case FinishReason.BudgetExceeded =>
                  record(
                    seq,
                    attempt,
                    name,
                    client,
                    prompt,
                    use,
                    billing,
                    None,
                    Some(Problem.Run.BudgetSpent(Problem.Run.Step.of(name))),
                    started,
                    msgs,
                    served
                  )
                  Left(Halt.Pause(PauseReason.Budget, Problem.Run.BudgetSpent(Problem.Run.Step.of(name))))
                case FinishReason.Custom(AllowanceGuard.Reason) =>
                  record(
                    seq,
                    attempt,
                    name,
                    client,
                    prompt,
                    use,
                    billing,
                    None,
                    Some(Problem.Run.AllowanceReached(Problem.Run.Model.of(client.model.wire))),
                    started,
                    msgs,
                    served
                  )
                  go(attempt + 1, tries, problems, previous, calls)
                case _ if res.finalAnswer.isLeft && Engine.outputCapHit(res).isDefined =>
                  // the same cap fails the same way again: a configuration error, not a retry (#1450)
                  val capped = Problem.Run.OutputCapped(
                    Problem.Run.Step.of(name),
                    Problem.Run.Model.of(client.model.wire),
                    Problem.Count.of(Engine.outputCapHit(res).getOrElse(Engine.OutputCapLimit(maxTokens.value))),
                    Problem.Count.of(maxTokens),
                    ceiling
                  )
                  record(
                    seq,
                    attempt,
                    name,
                    client,
                    prompt,
                    use,
                    billing,
                    None,
                    Some(capped),
                    started,
                    msgs,
                    served
                  )
                  Left(Halt.Pause(PauseReason.Provider, capped))
                case _ =>
                  // #941: the decoder's error (path + message) is recorded and goes back with the retry
                  val verdict = res.finalAnswer match
                    case Left(f)  => Left(List(Engine.answerProblem(f, res.finishReason)))
                    case Right(a) => validate(a, RunCtx.LastAttempt(last), calls)
                  // a well-formed answer that failed a check goes back with the problems, to be amended
                  val rejected = res.finalAnswer.toOption.map(a => schema.codec(a).noSpaces)
                  verdict match
                    case Right(Checked(o, ws)) =>
                      record(
                        seq,
                        attempt,
                        name,
                        client,
                        prompt,
                        use,
                        billing,
                        Some(o.asJson.noSpaces),
                        None,
                        started,
                        msgs,
                        served
                      )
                      ws.foreach(w => warn(s"$name: $w"))
                      Right(o)
                    case Left(ps) =>
                      // the rejected answer is kept on the failed attempt (never a checkpoint: only a
                      // done attempt is replayed) — what `agent compare` reads a rejected answer from
                      record(
                        seq,
                        attempt,
                        name,
                        client,
                        prompt,
                        use,
                        billing,
                        rejected,
                        Some(Problem.Run.Declined(ps.toVector)),
                        started,
                        msgs,
                        served
                      )
                      if last then
                        Left(
                          Halt.Fail(
                            Problem.Run
                              .NoValidAnswer(Problem.Run.Step.of(name), Problem.Count(attempt.toLong), ps.toVector)
                          )
                        )
                      // an answer that did not decode goes back too, so the model fixes the named field
                      // instead of redoing its lookups (#941); it is never recorded as the output (jsonb)
                      else go(attempt + 1, tries + 1, ps, rejected.orElse(Engine.undecoded(res.finalAnswer)), calls)
    go(before + 1, before, Nil, None, Nil)

  /** An exception a step's own code threw, logged under a new problem id ([[ProblemLog.unexpected]]). */
  private def unexpected(e: Exception): Problem = ProblemLog.unexpected(e, Problem.Where(s"agent run ${run.id}"))

  /** What the provider said about an attempt that came back: the model that served it, the input of its first model
    * call, its turns.
    */
  final private case class Served(
      model: Option[AgentRepo.StepRecord.ActualModel],
      firstCallTokens: Option[AgentRepo.StepRecord.FirstCallTokens],
      turns: Option[AgentRepo.StepRecord.Turns]
  )
  private object Served:
    val Unknown: Served = Served(None, None, None)

  /** One attempt of an LLM step: done when it carries no `error` (its `output` is then the checkpoint), failed
    * otherwise (an `output` then is the rejected answer). `served` = what the provider reported.
    */
  private def record(
      seq: AgentRepo.StepRecord.Seq,
      attempt: Int,
      name: AgentRepo.StepRecord.Name,
      client: LlmClient,
      prompt: Prompt,
      use: Usage,
      billing: Billing,
      output: Option[String],
      error: Option[Problem],
      started: java.time.Instant,
      msgs: List[AgentRepo.MessageRecord],
      served: Served = Served.Unknown
  ): Unit =
    val cost = billing.cost(use)
    engine.agents.runs.record(
      jobId,
      AgentRepo.StepRecord(
        seq,
        AgentRepo.StepRecord.Attempt(attempt),
        name,
        StepKind.Llm,
        if output.isDefined && error.isEmpty then StepStatus.Done else StepStatus.Failed,
        output.map(AgentRepo.StepRecord.Output(_)),
        Some(AgentRepo.StepRecord.PromptHash(prompt.hash.value)),
        Some(AgentRepo.StepRecord.Model(client.model.wire.value)),
        use,
        cost,
        error,
        started,
        actualModel = served.model,
        firstCallTokens = served.firstCallTokens,
        turns = served.turns
      ),
      msgs
    )

object RunCtx:
  export LlmCall.MaxTokens
  type LastAttempt = LastAttempt.T
  object LastAttempt extends Newtype[Boolean, Pure]

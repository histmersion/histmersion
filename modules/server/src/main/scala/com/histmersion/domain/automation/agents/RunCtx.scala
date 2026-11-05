package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.managers.Zero
import com.histmersion.server.agents.Codex

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.api.VocabularyCodecs.given
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, Semaphore}
import scala.jdk.CollectionConverters.*

/** What a running pipeline sees: its run, and the two kinds of step. Every step is checkpointed in `agent_steps`;
  * before running step `n` the context looks for a finished attempt of `n` and, if there is one, REPLAYS its output
  * instead — so a resumed run never repeats a completed step, and never pays for an LLM answer twice.
  */
final class RunCtx private[automation] (val run: RunInfo, engine: Engine):
  private val ds = engine.ds

  /** The approvals a run's steps open and wait on (and a pipeline records). */
  def approvalStore: com.histmersion.domain.automation.agents.ApprovalService = engine.agents.approvalStore
  private val cfg = engine.cfg

  /** The next step's number. A `var` because the pipeline is straight-line code calling steps in order; the counter IS
    * that order. Confined to the run's own thread.
    */
  private var nextSeq = 0

  private def claimSeq(): com.histmersion.domain.automation.agents.RunCtx.ClaimSeqResult = { nextSeq += 1; nextSeq }

  /** The next `n` step numbers, for steps that then run IN PARALLEL ([[llmAt]] inside one [[RunCtx.parallel]]):
    * numbering happens here, in program order, so a resumed run lines its parallel steps up with their checkpoints
    * whichever of them finished first.
    */
  def reserve(n: com.histmersion.domain.automation.agents.RunCtx.ReserveN): List[com.histmersion.domain.automation.agents.RunCtx.ReserveResult] = List.fill(math.max(0, n))(claimSeq())

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

  def warn(w: com.histmersion.domain.automation.agents.RunCtx.WarnW): Unit = engine.agents.jobRuns.warn(JobKind, run.id, w.take(500))

  /** A DRY run (#992) reads and calls models as usual but writes nothing to the corpus. */
  def dryRun: com.histmersion.domain.automation.agents.RunCtx.DryRunResult = run.dryRun

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
  def write[A](what: com.histmersion.domain.automation.agents.RunCtx.WriteWhat, payload: => io.circe.Json)(perform: => A)(simulated: => A): A =
    if !dryRun then perform
    else
      skipped.add(WouldWrite(what, payload.noSpaces))
      simulated

  def progress(p: com.histmersion.domain.automation.agents.RunCtx.ProgressP): Unit = engine.agents.jobRuns.progress(JobKind, run.id, p)

  /** Replay step `seq` if it finished before; a recorded step of another NAME means the pipeline changed under a paused
    * run, which is refused rather than misread.
    */
  private def replay[O: Decoder](seq: com.histmersion.domain.automation.agents.RunCtx.ReplaySeq, name: com.histmersion.domain.automation.agents.RunCtx.ReplayName): Option[Either[Halt, O]] =
    engine.agents.runs.checkpoint(run.id, seq).map { (recorded, json) =>
      if recorded != name then
        Left(
          Halt
            .Fail(s"step $seq is '$recorded' in the checkpoint but '$name' now — the pipeline changed; start a new run")
        )
      else
        decode[O](json).left
          .map(e => Halt.Fail(s"step $seq ($name): the checkpoint no longer decodes (${e.getMessage})"))
    }

  /** A deterministic step. Its output is checkpointed, so a side effect in `body` (a patch, a queue entry) happens at
    * most once per run.
    */
  def code[O: {Encoder, Decoder}](name: com.histmersion.domain.automation.agents.RunCtx.CodeName)(body: => Either[String, O]): Either[Halt, O] =
    val seq = claimSeq()
    replay[O](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        engine.beat(run.id, Some(seq))
        progress(name)
        val started = java.time.Instant.now(engine.agents.clock)
        val out =
          try body
          catch
            case e: InterruptedException => throw e
            case e: Exception            => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        engine.beat(run.id)
        val attempt = engine.agents.runs.attempts(run.id, seq) + 1
        val would = drainSkipped()
        out match
          case Right(o) =>
            engine.agents.runs.record(
              run.id,
              AgentRepo.StepRecord(
                seq,
                attempt,
                name,
                StepKind.Code,
                StepStatus.Done,
                Some(o.asJson.noSpaces),
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
              run.id,
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
                Some(m.take(2000)),
                started,
                wouldWrite = would
              ),
              Nil
            )
            Left(Halt.Fail(s"$name: $m"))
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
  def approval(name: com.histmersion.domain.automation.agents.RunCtx.ApprovalName, kind: ApprovalKind, subject: com.histmersion.domain.automation.agents.RunCtx.ApprovalSubject, mode: ApprovalMode)(
      payload: => Either[String, String]
  ): Either[Halt, Decided] =
    val seq = claimSeq()
    replay[Decided](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        val started = java.time.Instant.now(engine.agents.clock)
        def done(d: Decided): Either[Halt, Decided] =
          engine.agents.runs.record(
            run.id,
            AgentRepo.StepRecord(
              seq,
              engine.agents.runs.attempts(run.id, seq) + 1,
              name,
              StepKind.Code,
              StepStatus.Done,
              Some(d.asJson.noSpaces),
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
          Left(Halt.Wait(a.id, s"$name: approval ${a.id} waits for a person"))
        approvalStore.forStep(run.id, seq) match
          case Some(a) if a.state == ApprovalState.Open.wire => waitFor(a)
          case Some(a) => done(Decided(a.state == ApprovalState.Approved.wire, a.id, a.note))
          case None    =>
            val body =
              try payload
              catch
                case e: InterruptedException => throw e
                case e: Exception            => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
            body match
              case Left(m) =>
                engine.agents.runs.record(
                  run.id,
                  AgentRepo.StepRecord(
                    seq,
                    engine.agents.runs.attempts(run.id, seq) + 1,
                    name,
                    StepKind.Code,
                    StepStatus.Failed,
                    None,
                    None,
                    None,
                    Usage.Zero,
                    BigDecimal(0),
                    Some(m.take(2000)),
                    started
                  ),
                  Nil
                )
                Left(Halt.Fail(s"$name: $m"))
              case Right(json) if dryRun =>
                skipped.add(WouldWrite(s"${kind.wire} approval for $subject (treated as approved)", json))
                done(Decided(approved = true, 0L, Some(Engine.DryRunApproval)))
              case Right(json) if mode == ApprovalMode.Auto =>
                val a = approvalStore.openForStep(
                  run.id,
                  seq,
                  kind,
                  subject,
                  json,
                  ApprovalState.Approved,
                  Some("approved automatically (agents.approvals)")
                )
                done(Decided(approved = true, a.id))
              case Right(json) => waitFor(approvalStore.openForStep(run.id, seq, kind, subject, json))
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
  def notes: com.histmersion.domain.automation.agents.RunCtx.NotesResult = Prompt.notes(run.params.noteList)

  def llm[A, O: {Encoder, Decoder}](
      name: com.histmersion.domain.automation.agents.RunCtx.LlmName,
      agent: StepAgent,
      user: com.histmersion.domain.automation.agents.RunCtx.LlmUser,
      schema: ResponseSchema[A],
      maxTokens: Option[com.histmersion.domain.automation.agents.RunCtx.LlmMaxTokens] = None,
      turns: Option[com.histmersion.domain.automation.agents.RunCtx.LlmTurns] = None
  )(validate: (A, com.histmersion.domain.automation.agents.RunCtx.LlmValidateArg2) => Either[List[com.histmersion.domain.automation.agents.RunCtx.LlmValidateResultPart1], Checked[O]]): Either[Halt, O] =
    llmAt(claimSeq(), name, agent, user, schema, maxTokens, turns)(validate)

  /** [[llm]] at a step number taken earlier with [[reserve]] — the form a parallel step uses. `maxTokens` overrides
    * `agents.step.max-tokens` for a step whose answer is long (a whole article); `turns` overrides the agent's
    * `max-turns` for a step whose work grows with its input (the fixer: one more turn per finding, see
    * [[Engine.stepTurns]]).
    */
  def llmAt[A, O: {Encoder, Decoder}](
      seq: com.histmersion.domain.automation.agents.RunCtx.LlmAtSeq,
      name: com.histmersion.domain.automation.agents.RunCtx.LlmAtName,
      agent: StepAgent,
      user: com.histmersion.domain.automation.agents.RunCtx.LlmAtUser,
      schema: ResponseSchema[A],
      maxTokens: Option[com.histmersion.domain.automation.agents.RunCtx.LlmAtMaxTokens] = None,
      turns: Option[com.histmersion.domain.automation.agents.RunCtx.LlmAtTurns] = None
  )(validate: (A, com.histmersion.domain.automation.agents.RunCtx.LlmAtValidateArg2) => Either[List[com.histmersion.domain.automation.agents.RunCtx.LlmAtValidateResultPart1], Checked[O]]): Either[Halt, O] =
    llmAtSeen[A, O](seq, name, agent, user, schema, maxTokens, turns)((a, last, _) => validate(a, last))

  /** [[llmAt]] whose `validate` also sees the tool calls the step has made so far — this attempt's and the earlier
    * ones' of this execution, as the runtime reported them (sttp-ai's loop, Claude Code's stream-json, Codex's and
    * opencode's events), so an answer that says it looked something up can be held to a call that did (#1840). A retry
    * amends its answer without repeating its lookups, so the earlier attempts' calls count.
    */
  def llmAtSeen[A, O: {Encoder, Decoder}](
      seq: com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenSeq,
      name: com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenName,
      agent: StepAgent,
      user: com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenUser,
      schema: ResponseSchema[A],
      maxTokens: Option[com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenMaxTokens] = None,
      turns: Option[com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenTurns] = None
  )(validate: (A, com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenValidateArg2, List[ToolUse]) => Either[List[com.histmersion.domain.automation.agents.RunCtx.LlmAtSeenValidateResultPart1], Checked[O]]): Either[Halt, O] =
    replay[O](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        engine.beat(run.id, Some(seq))
        val defs = engine.agents.definitions
        (for
          d <- defs.agent(agent)
          sys <- defs.system(d, ToolStyle.Bare)
        yield (d, Prompt(sys, user))).left.map(m => Halt.Fail(s"$name: $m")).flatMap { (d, prompt) =>
          attempts(
            seq,
            name,
            d,
            prompt,
            schema,
            maxTokens.getOrElse(cfg.step.maxTokens),
            Engine.stepTurns(d, turns),
            validate,
            Engine.capKey(maxTokens.isDefined)
          )
        }
      }
    }

  /** How many attempts of one step may end on a model's allowance or plan limit (each moving to the chain's next model)
    * before the step gives up — a guard against a chain that never settles.
    */
  private val MaxModelSwitches = 8

  /** Models of a role's chain this run has already been told it passed over (one warning each). */
  private val warnedSkips = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  /** The model for the next call of `role`: the first of its chain with allowance left ([[ModelPicker.pick]]). None
    * left = the run pauses until the earliest window frees.
    */
  private def pickModel(name: com.histmersion.domain.automation.agents.RunCtx.PickModelName, role: Role): Either[Halt, Picked] =
    engine.agents.picker.pick(role, run.modelFor(role), onRefresh = n => warn(s"${role.wire}: $n")) match
      case Left(PickFailure.Unavailable(m))        => Left(Halt.Fail(s"$name: $m"))
      case Left(PickFailure.Exhausted(m, at, why)) => Left(Halt.Pause(why, s"$name: $m", at))
      case Right(p)                                =>
        p.skipped
          .filter(warnedSkips.add)
          .foreach(why => warn(s"${role.wire}: passed over $why; used ${p.client.model.wire}"))
        Right(p)

  private def attempts[A, O: {Encoder, Decoder}](
      seq: com.histmersion.domain.automation.agents.RunCtx.AttemptsSeq,
      name: com.histmersion.domain.automation.agents.RunCtx.AttemptsName,
      agent: AgentDef,
      prompt: Prompt,
      schema: ResponseSchema[A],
      maxTokens: com.histmersion.domain.automation.agents.RunCtx.AttemptsMaxTokens,
      turns: com.histmersion.domain.automation.agents.RunCtx.AttemptsTurns,
      validate: (A, com.histmersion.domain.automation.agents.RunCtx.AttemptsValidateArg2, List[ToolUse]) => Either[List[com.histmersion.domain.automation.agents.RunCtx.AttemptsValidateResultPart1], Checked[O]],
      capKey: com.histmersion.domain.automation.agents.RunCtx.AttemptsCapKey
  ): Either[Halt, O] =
    val role = agent.role
    val maxAttempts = 1 + math.max(0, cfg.step.maxRetries)
    // a resumed step's earlier attempts count as answers judged: its retries are not handed out twice
    val before = engine.agents.runs.attempts(run.id, seq)
    // tail-recursive retry loop over attempts; `problems` are the previous attempt's rejections and
    // `tries` counts the answers judged so far (an attempt cut short by a model's allowance or plan
    // limit is not an answer: it moves on to the chain's next model without using up a retry)
    // `seen`: the tool calls of this step's attempts so far, oldest first (what `validate` is shown)
    @annotation.tailrec
    def go(
        attempt: com.histmersion.domain.automation.agents.RunCtx.AttemptsGoAttempt,
        tries: com.histmersion.domain.automation.agents.RunCtx.AttemptsGoTries,
        problems: List[com.histmersion.domain.automation.agents.RunCtx.AttemptsGoProblems],
        previous: Option[com.histmersion.domain.automation.agents.RunCtx.AttemptsGoPrevious],
        seen: List[ToolUse]
    ): Either[Halt, O] =
      engine.checkControl(run.id).flatMap(_ => engine.checkBudget(run.id)).flatMap(_ => pickModel(name, role)) match
        case Left(h)                                                       => Left(h)
        case Right(_) if attempt - before > maxAttempts + MaxModelSwitches =>
          Left(
            Halt.Fail(
              s"$name: ${attempt - before - 1} attempts without an answer; the chain keeps running out mid-step"
            )
          )
        case Right(picked) =>
          val (client, billing) = (picked.client, picked.billing)
          val last = tries + 1 >= maxAttempts
          val user = if problems.isEmpty then prompt.user else prompt.user + Prompt.retry(problems, previous)
          progress(s"$name (attempt $attempt, ${client.model.wire})")
          val started = java.time.Instant.now(engine.agents.clock)
          val budget = engine.budgetInterceptor(run.id, billing)
          val latency = LatencyInterceptor(engine.llmSlots, () => engine.beat(run.id))(using engine.agents.timer)
          val allowance = AllowanceGuard(picked.headroomTokens, picked.headroomUsd, billing)
          val first = FirstCallProbe()
          // the agent's own `max-turns` (or the step's override): a reviewer that must look up 6–10
          // claims needs more than a one-shot step
          val call = LlmCall[A](
            prompt.system,
            user,
            engine.agents.toolbox.tools(engine.offered(run, agent)),
            schema,
            turns,
            maxTokens,
            List(latency, budget, allowance, first),
            Some(agent),
            Some(RunScope(run.id, seq))
          )
          // a plan pool takes at most its `max-calls-in-flight` calls at once, across every run
          val poolSlot = engine.agents.pools.poolOf(client.model).map(engine.agents.pools.slot)
          AgentsControllers.call(client.model) {
            poolSlot.foreach(engine.acquireBeating(run.id, _))
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
                Some(s"${limit.getMessage} — skipped until $until".take(2000)),
                started,
                Nil
              )
              go(attempt + 1, tries, problems, previous, seen)
            case Left(Right(e)) =>
              record(seq, attempt, name, client, prompt, Usage.Zero, billing, None, Some(e.message), started, Nil)
              Left(Halt.Pause(PauseReason.Provider, s"$name: ${e.message} — fix the provider, then resume"))
            case Right(res) =>
              val use = LlmClient.usage(res.usage)
              val msgs = Trace.messages(res.history, latency.millis, cfg.trace.maxToolResultChars)
              val served = Served(LlmClient.servedBy(res.llmCalls), first.tokens, Some(res.iterations))
              val calls = seen ++ res.toolCalls.toList.map(c => ToolUse(c.toolName, c.input))
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
                    Some("budget exhausted"),
                    started,
                    msgs,
                    served
                  )
                  Left(Halt.Pause(PauseReason.Budget, s"$name: the run's budget is exhausted"))
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
                    Some(
                      s"${client.model.wire} reached its allowance during the step; the chain's next model takes it"
                    ),
                    started,
                    msgs,
                    served
                  )
                  go(attempt + 1, tries, problems, previous, calls)
                case _ if res.finalAnswer.isLeft && Engine.outputCapHit(res).isDefined =>
                  // the same cap fails the same way again: a configuration error, not a retry (#1450)
                  val msg = Engine.outputCapMessage(
                    name,
                    Engine.outputCapHit(res).getOrElse(maxTokens),
                    maxTokens,
                    client.model.wire,
                    capKey
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
                    Some(msg.take(2000)),
                    started,
                    msgs,
                    served
                  )
                  Left(Halt.Pause(PauseReason.Provider, msg))
                case _ =>
                  // #941: the decoder's error (path + message) is recorded and goes back with the retry
                  val verdict = res.finalAnswer match
                    case Left(f)  => Left(List(Engine.answerProblem(f, res.finishReason)))
                    case Right(a) => validate(a, last, calls)
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
                        Some(ps.mkString("; ").take(2000)),
                        started,
                        msgs,
                        served
                      )
                      if last then
                        Left(
                          Halt.Fail(s"$name: no valid answer after $attempt attempt(s): ${ps.take(3).mkString("; ")}")
                        )
                      // an answer that did not decode goes back too, so the model fixes the named field
                      // instead of redoing its lookups (#941); it is never recorded as the output (jsonb)
                      else go(attempt + 1, tries + 1, ps, rejected.orElse(Engine.undecoded(res.finalAnswer)), calls)
    go(before + 1, before, Nil, None, Nil)

  /** What the provider said about an attempt that came back: the model that served it, the input of its first model
    * call, its turns.
    */
  final private case class Served(model: Option[Served.Model], firstCallTokens: Option[Served.FirstCallTokens], turns: Option[Served.Turns])
  private object Served:
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Unknown: Served = Served(None, None, None)

  /** One attempt of an LLM step: done when it carries no `error` (its `output` is then the checkpoint), failed
    * otherwise (an `output` then is the rejected answer). `served` = what the provider reported.
    */
  private def record(
      seq: com.histmersion.domain.automation.agents.RunCtx.RecordSeq,
      attempt: com.histmersion.domain.automation.agents.RunCtx.RecordAttempt,
      name: com.histmersion.domain.automation.agents.RunCtx.RecordName,
      client: LlmClient,
      prompt: Prompt,
      use: Usage,
      billing: Billing,
      output: Option[com.histmersion.domain.automation.agents.RunCtx.RecordOutput],
      error: Option[com.histmersion.domain.automation.agents.RunCtx.RecordError],
      started: java.time.Instant,
      msgs: List[AgentRepo.MessageRecord],
      served: Served = Served.Unknown
  ): Unit =
    val cost = billing.cost(use)
    engine.agents.runs.record(
      run.id,
      AgentRepo.StepRecord(
        seq,
        attempt,
        name,
        StepKind.Llm,
        if output.isDefined && error.isEmpty then StepStatus.Done else StepStatus.Failed,
        output,
        Some(prompt.hash),
        Some(client.model.wire),
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
  type AttemptsGoAttempt = AttemptsGoAttempt.T
  object AttemptsGoAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsGoTries = AttemptsGoTries.T
  object AttemptsGoTries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsGoProblems = AttemptsGoProblems.T
  object AttemptsGoProblems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsGoPrevious = AttemptsGoPrevious.T
  object AttemptsGoPrevious extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ClaimSeqResult = ClaimSeqResult.T
  object ClaimSeqResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReserveResult = ReserveResult.T
  object ReserveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReserveN = ReserveN.T
  object ReserveN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WarnW = WarnW.T
  object WarnW extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRunResult = DryRunResult.T
  object DryRunResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteWhat = WriteWhat.T
  object WriteWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressP = ProgressP.T
  object ProgressP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaySeq = ReplaySeq.T
  object ReplaySeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplayName = ReplayName.T
  object ReplayName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CodeName = CodeName.T
  object CodeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalName = ApprovalName.T
  object ApprovalName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalSubject = ApprovalSubject.T
  object ApprovalSubject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesResult = NotesResult.T
  object NotesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmName = LlmName.T
  object LlmName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmUser = LlmUser.T
  object LlmUser extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmMaxTokens = LlmMaxTokens.T
  object LlmMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmTurns = LlmTurns.T
  object LlmTurns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmValidateArg2 = LlmValidateArg2.T
  object LlmValidateArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmValidateResultPart1 = LlmValidateResultPart1.T
  object LlmValidateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeq = LlmAtSeq.T
  object LlmAtSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtName = LlmAtName.T
  object LlmAtName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtUser = LlmAtUser.T
  object LlmAtUser extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtMaxTokens = LlmAtMaxTokens.T
  object LlmAtMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtTurns = LlmAtTurns.T
  object LlmAtTurns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtValidateArg2 = LlmAtValidateArg2.T
  object LlmAtValidateArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtValidateResultPart1 = LlmAtValidateResultPart1.T
  object LlmAtValidateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenSeq = LlmAtSeenSeq.T
  object LlmAtSeenSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenName = LlmAtSeenName.T
  object LlmAtSeenName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenUser = LlmAtSeenUser.T
  object LlmAtSeenUser extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenMaxTokens = LlmAtSeenMaxTokens.T
  object LlmAtSeenMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenTurns = LlmAtSeenTurns.T
  object LlmAtSeenTurns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenValidateArg2 = LlmAtSeenValidateArg2.T
  object LlmAtSeenValidateArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LlmAtSeenValidateResultPart1 = LlmAtSeenValidateResultPart1.T
  object LlmAtSeenValidateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickModelName = PickModelName.T
  object PickModelName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsSeq = AttemptsSeq.T
  object AttemptsSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsName = AttemptsName.T
  object AttemptsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsMaxTokens = AttemptsMaxTokens.T
  object AttemptsMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsTurns = AttemptsTurns.T
  object AttemptsTurns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsValidateArg2 = AttemptsValidateArg2.T
  object AttemptsValidateArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsValidateResultPart1 = AttemptsValidateResultPart1.T
  object AttemptsValidateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsCapKey = AttemptsCapKey.T
  object AttemptsCapKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordSeq = RecordSeq.T
  object RecordSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordAttempt = RecordAttempt.T
  object RecordAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordName = RecordName.T
  object RecordName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordOutput = RecordOutput.T
  object RecordOutput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordError = RecordError.T
  object RecordError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

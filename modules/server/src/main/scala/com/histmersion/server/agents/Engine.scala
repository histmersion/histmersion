package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.db.{AgentRepo, QueueRepo}
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, Semaphore}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

/** Why a run stopped before its pipeline finished. A value, threaded through the pipeline's
  * for-comprehension: a step either produces its output or halts the run. */
enum Halt:
  /** `resumeAt`: when the run may go on by itself (an allowance window freeing); None = a person resumes it. */
  case Pause(reason: PauseReason, detail: String, resumeAt: Option[java.time.Instant] = None)
  case Cancel
  case Fail(message: String)
  /** The run waits for a person to decide approval `id` (a blocking step); it holds no worker, and
    * goes back to the queue when the approval is decided. */
  case Wait(id: Long, what: String)

/** What a person decided on a blocking approval step — the step's checkpoint. */
final case class Decided(approved: Boolean, approval: Long, note: Option[String] = None)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject

/** An LLM answer after code validation: the value to checkpoint, and what was dropped or doubted on
  * the way (run warnings, not failures). */
final case class Checked[O](value: O, warnings: List[String] = Nil)

/** A pipeline: Scala code that calls [[RunCtx.code]] and [[RunCtx.llm]] steps in a FIXED order (the
  * step number is the order, so a resumed run lines its steps up with its checkpoints). Returns a
  * one-line summary for the run's log entry. */
trait PipelineDef:
  def pipeline: Pipeline
  def run(ctx: RunCtx): Either[Halt, String]

/** What a running pipeline sees: its run, and the two kinds of step. Every step is checkpointed in
  * `agent_steps`; before running step `n` the context looks for a finished attempt of `n` and, if
  * there is one, REPLAYS its output instead — so a resumed run never repeats a completed step, and
  * never pays for an LLM answer twice. */
final class RunCtx private[agents] (val run: RunInfo, engine: Engine):
  private val ds  = engine.ds
  private val cfg = engine.cfg

  /** The next step's number. A `var` because the pipeline is straight-line code calling steps in
    * order; the counter IS that order. Confined to the run's own thread. */
  private var nextSeq = 0

  private def claimSeq(): Int = { nextSeq += 1; nextSeq }

  /** The next `n` step numbers, for steps that then run IN PARALLEL ([[llmAt]] inside one
    * [[RunCtx.parallel]]): numbering happens here, in program order, so a resumed run lines its
    * parallel steps up with their checkpoints whichever of them finished first. */
  def reserve(n: Int): List[Int] = List.fill(math.max(0, n))(claimSeq())

  /** Run steps concurrently (Ox `par`, inside the run's own thread's scope: a cancel interrupts them
    * all). A halt wins over the others' results — cancel first, then a failure (terminal), then a
    * pause — but every step that finished is checkpointed either way, so a resume re-runs only the
    * ones that did not. */
  def parallel[A](steps: List[() => Either[Halt, A]]): Either[Halt, List[A]] =
    val results = ox.par(steps.map(s => () => s()))
    val halts   = results.collect { case Left(h) => h }
    halts.collectFirst { case h @ Halt.Cancel => h }
      .orElse(halts.collectFirst { case h: Halt.Fail => h })
      .orElse(halts.headOption) match
      case Some(h) => Left(h)
      case None    => Right(results.collect { case Right(a) => a }.toList)

  def warn(w: String): Unit = AgentRepo.addWarning(ds, run.id, w.take(500))

  /** A DRY run (#992) reads and calls models as usual but writes nothing to the corpus. */
  def dryRun: Boolean = run.dryRun

  /** The writes this run's current step skipped (a dry run), recorded with the step when it ends. A
    * concurrent queue: a step's body runs on the run's thread, but nothing stops a parallel step from
    * asking. */
  private val skipped = java.util.concurrent.ConcurrentLinkedQueue[WouldWrite]()

  private def drainSkipped(): List[WouldWrite] =
    Iterator.continually(skipped.poll()).takeWhile(_ != null).toList

  /** THE write gate: every write a pipeline makes to the corpus (a patch, an article, a queue item, an
    * approval, a goal, a link, an upstream `--apply`) goes through here. In a dry run `perform` is not
    * called: `what` and `payload` (the whole write, as JSON) are recorded on the step under "dry run:
    * not written", and `simulated` stands in for the result. */
  def write[A](what: String, payload: => io.circe.Json)(perform: => A)(simulated: => A): A =
    if !dryRun then perform
    else
      skipped.add(WouldWrite(what, payload.noSpaces))
      simulated

  def progress(p: String): Unit = AgentRepo.progress(ds, run.id, p)

  /** Replay step `seq` if it finished before; a recorded step of another NAME means the pipeline
    * changed under a paused run, which is refused rather than misread. */
  private def replay[O: Decoder](seq: Int, name: String): Option[Either[Halt, O]] =
    AgentRepo.checkpoint(ds, run.id, seq).map { (recorded, json) =>
      if recorded != name then Left(Halt.Fail(s"step $seq is '$recorded' in the checkpoint but '$name' now — the pipeline changed; start a new run"))
      else decode[O](json).left.map(e => Halt.Fail(s"step $seq ($name): the checkpoint no longer decodes (${e.getMessage})"))
    }

  /** A deterministic step. Its output is checkpointed, so a side effect in `body` (a patch, a queue
    * entry) happens at most once per run. */
  def code[O: {Encoder, Decoder}](name: String)(body: => Either[String, O]): Either[Halt, O] =
    val seq = claimSeq()
    replay[O](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        engine.beat(run.id, Some(seq))
        progress(name)
        val started = java.time.Instant.now()
        val out =
          try body
          catch
            case e: InterruptedException => throw e
            case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        engine.beat(run.id)
        val attempt = AgentRepo.attempts(ds, run.id, seq) + 1
        val would   = drainSkipped()
        out match
          case Right(o) =>
            AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, attempt, name, StepKind.Code, StepStatus.Done,
              Some(o.asJson.noSpaces), None, None, Usage.Zero, BigDecimal(0), None, started, wouldWrite = would), Nil)
            Right(o)
          case Left(m) =>
            AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, attempt, name, StepKind.Code, StepStatus.Failed,
              None, None, None, Usage.Zero, BigDecimal(0), Some(m.take(2000)), started, wouldWrite = would), Nil)
            Left(Halt.Fail(s"$name: $m"))
      }
    }

  /** A BLOCKING approval step (docs/research-embedded-agents.md §14): the first time the run reaches
    * it, `payload` (the proposal as JSON) is recorded as an approval of `kind` about `subject`, and the
    * run stops in `waiting` without holding a worker. Deciding the approval re-queues the run; it
    * replays its finished steps, reaches this step again and gets the decision, which is then
    * checkpointed like any output. In `auto` mode the approval is recorded already approved and the run
    * goes on at once. A resumed run finds the approval it opened, never a second one. A DRY run opens
    * none: the proposal is recorded on the step as not written, and the run goes on as if it were
    * approved, so the steps after it run too (and skip their writes). */
  def approval(name: String, kind: ApprovalKind, subject: String, mode: ApprovalMode)(
      payload: => Either[String, String]): Either[Halt, Decided] =
    val seq = claimSeq()
    replay[Decided](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        val started = java.time.Instant.now()
        def done(d: Decided): Either[Halt, Decided] =
          AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, AgentRepo.attempts(ds, run.id, seq) + 1, name,
            StepKind.Code, StepStatus.Done, Some(d.asJson.noSpaces), None, None, Usage.Zero, BigDecimal(0), None, started,
            wouldWrite = drainSkipped()), Nil)
          Right(d)
        def waitFor(a: ApprovalInfo): Either[Halt, Decided] =
          progress(s"$name: waiting for approval ${a.id}")
          Left(Halt.Wait(a.id, s"$name: approval ${a.id} waits for a person"))
        AgentRepo.forStep(ds, run.id, seq) match
          case Some(a) if a.state == ApprovalState.Open.wire => waitFor(a)
          case Some(a) => done(Decided(a.state == ApprovalState.Approved.wire, a.id, a.note))
          case None =>
            val body =
              try payload
              catch
                case e: InterruptedException => throw e
                case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
            body match
              case Left(m) =>
                AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, AgentRepo.attempts(ds, run.id, seq) + 1, name,
                  StepKind.Code, StepStatus.Failed, None, None, None, Usage.Zero, BigDecimal(0), Some(m.take(2000)), started), Nil)
                Left(Halt.Fail(s"$name: $m"))
              case Right(json) if dryRun =>
                skipped.add(WouldWrite(s"${kind.wire} approval for $subject (treated as approved)", json))
                done(Decided(approved = true, 0L, Some(Engine.DryRunApproval)))
              case Right(json) if mode == ApprovalMode.Auto =>
                val a = AgentRepo.openForStep(ds, run.id, seq, kind, subject, json, ApprovalState.Approved,
                  Some("approved automatically (agents.approvals)"))
                done(Decided(approved = true, a.id))
              case Right(json) => waitFor(AgentRepo.openForStep(ds, run.id, seq, kind, subject, json))
      }
    }

  /** An LLM step run as one canonical agent definition (`agents/<name>.md`): its prompt (rendered for
    * the runtime the model chain picks), its role (which chain), its tools (the ONLY ones offered) —
    * plus `user`, the message carrying the data, the typed answer `A` the model must return, and
    * `validate` — the code check every answer must pass, which turns it into the VERIFIED value `O`
    * that is checkpointed and returned. A rejected answer is retried (up to `agents.step.max-retries`)
    * with the problems appended; on the LAST attempt `validate` is told so (`last = true`) and should
    * keep what passed rather than reject. Each attempt is recorded with its tokens and cost. */
  /** The person's notes for this run (manager hints snapshotted at dispatch, `RunParams.notes`), as the
    * block a step appends to its USER message — never the system prompt: they are fenced, and
    * subordinate to the record. Empty when the run has none. */
  def notes: String = Prompt.notes(run.params.noteList)

  def llm[A, O: {Encoder, Decoder}](name: String, agent: StepAgent, user: String,
      schema: ResponseSchema[A], maxTokens: Option[Int] = None, turns: Option[Int] = None)(
      validate: (A, Boolean) => Either[List[String], Checked[O]]): Either[Halt, O] =
    llmAt(claimSeq(), name, agent, user, schema, maxTokens, turns)(validate)

  /** [[llm]] at a step number taken earlier with [[reserve]] — the form a parallel step uses.
    * `maxTokens` overrides `agents.step.max-tokens` for a step whose answer is long (a whole
    * article); `turns` overrides the agent's `max-turns` for a step whose work grows with its input
    * (the fixer: one more turn per finding, see [[Engine.stepTurns]]). */
  def llmAt[A, O: {Encoder, Decoder}](seq: Int, name: String, agent: StepAgent, user: String,
      schema: ResponseSchema[A], maxTokens: Option[Int] = None, turns: Option[Int] = None)(
      validate: (A, Boolean) => Either[List[String], Checked[O]]): Either[Halt, O] =
    llmAtSeen[A, O](seq, name, agent, user, schema, maxTokens, turns)((a, last, _) => validate(a, last))

  /** [[llmAt]] whose `validate` also sees the tool calls the step has made so far — this attempt's
    * and the earlier ones' of this execution, as the runtime reported them (sttp-ai's loop, Claude
    * Code's stream-json, Codex's and opencode's events), so an answer that says it looked something up
    * can be held to a call that did (#1840). A retry amends its answer without repeating its lookups,
    * so the earlier attempts' calls count. */
  def llmAtSeen[A, O: {Encoder, Decoder}](seq: Int, name: String, agent: StepAgent, user: String,
      schema: ResponseSchema[A], maxTokens: Option[Int] = None, turns: Option[Int] = None)(
      validate: (A, Boolean, List[AgentVerify.ToolUse]) => Either[List[String], Checked[O]]): Either[Halt, O] =
    replay[O](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        engine.beat(run.id, Some(seq))
        val defs = engine.agents.definitions
        (for
          d   <- defs.agent(agent)
          sys <- defs.system(d, ToolStyle.Bare)
        yield (d, Prompt(sys, user))).left.map(m => Halt.Fail(s"$name: $m")).flatMap { (d, prompt) =>
          attempts(seq, name, d, prompt, schema, maxTokens.getOrElse(cfg.step.maxTokens), Engine.stepTurns(d, turns), validate,
            Engine.capKey(maxTokens.isDefined))
        }
      }
    }

  /** How many attempts of one step may end on a model's allowance or plan limit (each moving to the
    * chain's next model) before the step gives up — a guard against a chain that never settles. */
  private val MaxModelSwitches = 8

  /** Models of a role's chain this run has already been told it passed over (one warning each). */
  private val warnedSkips = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  /** The model for the next call of `role`: the first of its chain with allowance left
    * ([[ModelPicker.pick]]). None left = the run pauses until the earliest window frees. */
  private def pickModel(name: String, role: Role): Either[Halt, Picked] =
    engine.agents.picker.pick(role, run.modelFor(role), onRefresh = n => warn(s"${role.wire}: $n")) match
      case Left(PickFailure.Unavailable(m)) => Left(Halt.Fail(s"$name: $m"))
      case Left(PickFailure.Exhausted(m, at, why)) => Left(Halt.Pause(why, s"$name: $m", at))
      case Right(p) =>
        p.skipped.filter(warnedSkips.add).foreach(why => warn(s"${role.wire}: passed over $why; used ${p.client.model.wire}"))
        Right(p)

  private def attempts[A, O: {Encoder, Decoder}](seq: Int, name: String, agent: AgentDef,
      prompt: Prompt, schema: ResponseSchema[A], maxTokens: Int, turns: Int,
      validate: (A, Boolean, List[AgentVerify.ToolUse]) => Either[List[String], Checked[O]], capKey: String): Either[Halt, O] =
    val role        = agent.role
    val maxAttempts = 1 + math.max(0, cfg.step.maxRetries)
    // a resumed step's earlier attempts count as answers judged: its retries are not handed out twice
    val before = AgentRepo.attempts(ds, run.id, seq)
    // tail-recursive retry loop over attempts; `problems` are the previous attempt's rejections and
    // `tries` counts the answers judged so far (an attempt cut short by a model's allowance or plan
    // limit is not an answer: it moves on to the chain's next model without using up a retry)
    // `seen`: the tool calls of this step's attempts so far, oldest first (what `validate` is shown)
    @annotation.tailrec
    def go(attempt: Int, tries: Int, problems: List[String], previous: Option[String],
        seen: List[AgentVerify.ToolUse]): Either[Halt, O] =
      engine.checkControl(run.id).flatMap(_ => engine.checkBudget(run.id)).flatMap(_ => pickModel(name, role)) match
        case Left(h) => Left(h)
        case Right(_) if attempt - before > maxAttempts + MaxModelSwitches =>
          Left(Halt.Fail(s"$name: ${attempt - before - 1} attempts without an answer; the chain keeps running out mid-step"))
        case Right(picked) =>
          val (client, billing) = (picked.client, picked.billing)
          val last    = tries + 1 >= maxAttempts
          val user    = if problems.isEmpty then prompt.user else prompt.user + Prompt.retry(problems, previous)
          progress(s"$name (attempt $attempt, ${client.model.wire})")
          val started = java.time.Instant.now()
          val budget  = engine.budgetInterceptor(run.id, billing)
          val latency = LatencyInterceptor(engine.llmSlots, () => engine.beat(run.id))
          val allowance = AllowanceGuard(picked.headroomTokens, picked.headroomUsd, billing)
          val first   = FirstCallProbe()
          // the agent's own `max-turns` (or the step's override): a reviewer that must look up 6–10
          // claims needs more than a one-shot step
          val call = LlmCall[A](prompt.system, user, engine.agents.toolbox.tools(agent.tools), schema,
            turns, maxTokens, List(latency, budget, allowance, first), Some(agent),
            Some(RunScope(run.id, seq)))
          // a plan pool takes at most its `max-calls-in-flight` calls at once, across every run
          val poolSlot = engine.agents.pools.poolOf(client.model).map(engine.agents.pools.slot)
          AgentsHandlers.call(client.model) {
            poolSlot.foreach(engine.acquireBeating(run.id, _))
            try client.run(call) finally poolSlot.foreach(_.release())
          } match
            case Left(Left(limit)) =>
              val until = engine.agents.picker.cool(client.model, limit.until)
              record(seq, attempt, name, client, prompt, Usage.Zero, billing, None, Some(s"${limit.getMessage} — skipped until $until".take(2000)), started, Nil)
              go(attempt + 1, tries, problems, previous, seen)
            case Left(Right(e)) =>
              record(seq, attempt, name, client, prompt, Usage.Zero, billing, None, Some(e.message), started, Nil)
              Left(Halt.Pause(PauseReason.Provider, s"$name: ${e.message} — fix the provider, then resume"))
            case Right(res) =>
              val use  = LlmClient.usage(res.usage)
              val msgs = Trace.messages(res.history, latency.millis, cfg.trace.maxToolResultChars)
              val served = Served(LlmClient.servedBy(res.llmCalls), first.tokens, Some(res.iterations))
              val calls  = seen ++ res.toolCalls.toList.map(c => AgentVerify.ToolUse(c.toolName, c.input))
              res.finishReason match
                case FinishReason.BudgetExceeded =>
                  record(seq, attempt, name, client, prompt, use, billing, None, Some("budget exhausted"), started, msgs, served)
                  Left(Halt.Pause(PauseReason.Budget, s"$name: the run's budget is exhausted"))
                case FinishReason.Custom(AllowanceGuard.Reason) =>
                  record(seq, attempt, name, client, prompt, use, billing, None,
                    Some(s"${client.model.wire} reached its allowance during the step; the chain's next model takes it"), started, msgs, served)
                  go(attempt + 1, tries, problems, previous, calls)
                case _ if res.finalAnswer.isLeft && Engine.outputCapHit(res).isDefined =>
                  // the same cap fails the same way again: a configuration error, not a retry (#1450)
                  val msg = Engine.outputCapMessage(name, Engine.outputCapHit(res).getOrElse(maxTokens), maxTokens,
                    client.model.wire, capKey)
                  record(seq, attempt, name, client, prompt, use, billing, None, Some(msg.take(2000)), started, msgs, served)
                  Left(Halt.Pause(PauseReason.Provider, msg))
                case _ =>
                  // #941: the decoder's error (path + message) is recorded and goes back with the retry
                  val verdict = res.finalAnswer match
                    case Left(f) => Left(List(Engine.answerProblem(f, res.finishReason)))
                    case Right(a) => validate(a, last, calls)
                  // a well-formed answer that failed a check goes back with the problems, to be amended
                  val rejected = res.finalAnswer.toOption.map(a => schema.codec(a).noSpaces)
                  verdict match
                    case Right(Checked(o, ws)) =>
                      record(seq, attempt, name, client, prompt, use, billing, Some(o.asJson.noSpaces), None, started, msgs, served)
                      ws.foreach(w => warn(s"$name: $w"))
                      Right(o)
                    case Left(ps) =>
                      // the rejected answer is kept on the failed attempt (never a checkpoint: only a
                      // done attempt is replayed) — what `agent compare` reads a rejected answer from
                      record(seq, attempt, name, client, prompt, use, billing, rejected, Some(ps.mkString("; ").take(2000)),
                        started, msgs, served)
                      if last then Left(Halt.Fail(s"$name: no valid answer after $attempt attempt(s): ${ps.take(3).mkString("; ")}"))
                      // an answer that did not decode goes back too, so the model fixes the named field
                      // instead of redoing its lookups (#941); it is never recorded as the output (jsonb)
                      else go(attempt + 1, tries + 1, ps, rejected.orElse(Engine.undecoded(res.finalAnswer)), calls)
    go(before + 1, before, Nil, None, Nil)

  /** What the provider said about an attempt that came back: the model that served it, the input of
    * its first model call, its turns. */
  private final case class Served(model: Option[String], firstCallTokens: Option[Long], turns: Option[Int])
  private object Served:
    val Unknown: Served = Served(None, None, None)

  /** One attempt of an LLM step: done when it carries no `error` (its `output` is then the checkpoint),
    * failed otherwise (an `output` then is the rejected answer). `served` = what the provider
    * reported. */
  private def record(seq: Int, attempt: Int, name: String, client: LlmClient, prompt: Prompt, use: Usage,
      billing: Billing, output: Option[String], error: Option[String], started: java.time.Instant,
      msgs: List[AgentRepo.MessageRecord], served: Served = Served.Unknown): Unit =
    val cost = billing.cost(use)
    AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, attempt, name, StepKind.Llm,
      if output.isDefined && error.isEmpty then StepStatus.Done else StepStatus.Failed, output, Some(prompt.hash),
      Some(client.model.wire), use, cost, error, started, actualModel = served.model,
      firstCallTokens = served.firstCallTokens, turns = served.turns), msgs)

/** A model's allowance, inside one step: once the step's own calls have spent what the model had left
  * above its reserve (tokens or USD, as [[ModelPicker]] measured at the pick), the loop is told to
  * finish, and the engine hands the step to the chain's next model. At most one call overshoots. */
private[agents] final class AllowanceGuard(headroomTokens: Option[Long], headroomUsd: Option[BigDecimal], billing: Billing)
    extends AgentInterceptor[Identity]:
  override def decide(state: AgentRunState): LoopDecision =
    val use = LlmClient.usage(state.usage)
    val overTokens = headroomTokens.exists(use.total >= _)
    val overUsd    = headroomUsd.exists(h => billing.cost(use) >= h)
    if overTokens || overUsd then LoopDecision.FinishNow(FinishReason.Custom(AllowanceGuard.Reason),
      sttp.ai.core.agent.interceptor.BudgetInterceptor.defaultInstruction)
    else LoopDecision.Continue

private[agents] object AllowanceGuard:
  val Reason = "allowance"

/** Times each LLM call of a step and holds one of the server-wide `max-llm-calls-in-flight` slots
  * while it runs, so parallel steps and concurrent runs together never exceed the cap. */
private[agents] final class LatencyInterceptor(slots: Semaphore, beat: () => Unit = () => ()) extends AgentInterceptor[Identity]:
  /** Per-call latencies, in call order. A `var` local to one step's interceptor instance. */
  @volatile var millis: Vector[Int] = Vector.empty
  override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
    // waiting for a slot behind other runs' calls is not a stall: beat while waiting
    while !slots.tryAcquire(Engine.BeatWhileWaitingSeconds, java.util.concurrent.TimeUnit.SECONDS) do beat()
    beat()
    val t0 = System.nanoTime()
    try next
    finally
      millis = millis :+ ((System.nanoTime() - t0) / 1000000).toInt
      slots.release()
      beat()

/** The run's remaining budget, checked by the sttp-ai loop before every iteration: once the spend
  * recorded before this step plus this step's own calls reaches the run's cap (cost or tokens), the
  * loop is told to finish, and the engine pauses the run. Our [[Agents.Billing]] prices the calls (an
  * unpriced metered model never gets this far); a plan model costs 0 USD, so only its tokens count. */
private[agents] final class RunBudget(spentUsd: BigDecimal, spentTokens: Long, capUsd: BigDecimal, capTokens: Long,
    billing: Billing, cacheReadWeight: Double = 1.0) extends AgentInterceptor[Identity]:
  override def decide(state: AgentRunState): LoopDecision =
    val use  = LlmClient.usage(state.usage)
    val cost = billing.cost(use)
    if spentUsd + cost >= capUsd || spentTokens + use.budgeted(cacheReadWeight) >= capTokens then
      LoopDecision.FinishNow(FinishReason.BudgetExceeded, sttp.ai.core.agent.interceptor.BudgetInterceptor.defaultInstruction)
    else LoopDecision.Continue

/** The run pool: `agents.max-concurrent-runs` workers draining a queue of run ids, on one daemon
  * thread hosting an Ox scope (the same arrangement as the OCR [[com.histmersion.server.Jobs]]).
  * Runs are durable — `jobs` + `agent_steps` — so the in-memory queue is rebuilt at [[start]]. */
final class Engine(val ds: DataSource, val cfg: AgentsConfig, val agents: AgentsHandlers,
    pipelines: Map[Pipeline, PipelineDef]):

  private val queue = LinkedBlockingQueue[String]()
  val llmSlots: Semaphore = Semaphore(math.max(1, cfg.maxLlmCallsInFlight))

  /** When the trace was last pruned (epoch ms; 0 = not yet in this process). */
  private val lastPrune = java.util.concurrent.atomic.AtomicLong(0L)

  /** Delete traced messages older than `agents.trace.keep-messages-days`, at most once every
    * `prune-every-minutes` (or now with `force`). Returns how many went. Never fails a run: a failed
    * prune is logged and tried again next time. */
  def pruneTrace(force: Boolean = false, now: Long = System.currentTimeMillis()): Int =
    val every = math.max(1, cfg.trace.pruneEveryMinutes).toLong * 60000L
    val last  = lastPrune.get()
    if cfg.trace.keepMessagesDays <= 0 || (!force && now - last < every) || !lastPrune.compareAndSet(last, now) then 0
    else
      try
        val n = AgentRepo.pruneMessages(ds, cfg.trace.keepMessagesDays)
        if n > 0 then scribe.info(s"agent trace: pruned $n message(s) older than ${cfg.trace.keepMessagesDays} day(s)")
        n
      catch
        case e: Exception =>
          scribe.warn(s"agent trace pruning failed: ${e.getMessage}")
          0

  /** What a person asked of a RUNNING run, read by the run between steps — and what the stall watchdog
    * watches: the last sign of life, the step it was in, and the CLI processes it has alive. */
  private final class Control(val thread: Thread):
    @volatile var pause: Boolean  = false
    @volatile var cancel: Boolean = false
    @volatile var lastBeat: Long  = System.currentTimeMillis()
    /** When `lastBeat` was last written to `jobs.heartbeat_at` (at most every heartbeat-seconds). */
    @volatile var persisted: Long = 0L
    @volatile var seq: Int        = 0
    /** Set by the watchdog before it interrupts the run: how the run settles. */
    @volatile var stalled: Option[Halt] = None
    val procs: java.util.Set[Process] = ConcurrentHashMap.newKeySet[Process]()
  private val running = ConcurrentHashMap[String, Control]()

  /** A sign of life of run `id` (a step boundary, a model call, a CLI line); `seq` = the step it is in. */
  private[agents] def beat(id: String, seq: Option[Int] = None): Unit =
    Option(running.get(id)).foreach { c =>
      val now = System.currentTimeMillis()
      c.lastBeat = now
      seq.foreach(c.seq = _)
      if now - c.persisted >= math.max(1, cfg.watchdog.heartbeatSeconds).toLong * 1000L then
        c.persisted = now
        try AgentRepo.heartbeat(ds, id)
        catch case e: Exception => scribe.warn(s"agent run $id: heartbeat not written: ${e.getMessage}")
    }

  /** Take a plan pool's slot, beating while waiting for it (other runs' calls hold it). */
  private[agents] def acquireBeating(id: String, slot: Semaphore): Unit =
    while !slot.tryAcquire(Engine.BeatWhileWaitingSeconds, java.util.concurrent.TimeUnit.SECONDS) do beat(id)

  /** Called with every run that settles (done, failed, cancelled, paused, waiting) — the managers'
    * nudge. A `var` because the supervisor is attached after construction. */
  @volatile private var settledHooks: List[RunInfo => Unit] = Nil

  def onSettled(f: RunInfo => Unit): Unit = settledHooks = settledHooks :+ f

  /** The stall watchdog's pass at `now` (epoch ms): every running run whose last sign of life is older
    * than `agents.watchdog.stall-minutes` has its processes killed and is interrupted; it settles
    * paused `stalled` (resuming after resume-minutes), or failed on its max-stalls-th stall of one
    * step. Returns the ids. */
  def checkStalls(now: Long = System.currentTimeMillis()): List[String] =
    val limit = cfg.watchdog.stallMinutesFor(cfg.providers).toLong * 60000L
    running.asScala.toList.filter((_, c) => c.stalled.isEmpty && !c.cancel && now - c.lastBeat > limit).map { (id, c) =>
      val mins  = (now - c.lastBeat) / 60000L
      val mark  = s"${Engine.StallMark} ${c.seq}"
      val prior = AgentRepo.get(ds, id).toList.flatMap(_.warnings).count(_.startsWith(mark + " "))
      val n     = prior + 1
      AgentRepo.addWarning(ds, id, s"$mark (${n}x): no sign of life for $mins min; its processes were killed".take(500))
      c.stalled = Some(
        if n >= math.max(1, cfg.watchdog.maxStalls) then
          Halt.Fail(s"stalled $n times at step ${c.seq} (no sign of life for $mins min each time; agents.watchdog)")
        else Halt.Pause(PauseReason.Stalled, s"no sign of life for $mins min at step ${c.seq} (stall $n of ${cfg.watchdog.maxStalls})",
          Some(java.time.Instant.ofEpochMilli(now).plusSeconds(math.max(0, cfg.watchdog.resumeMinutes).toLong * 60L))))
      scribe.warn(s"agent run $id stalled at step ${c.seq} (no sign of life for $mins min); killing its processes")
      c.procs.asScala.toList.foreach(p => try CliProcess.kill(p) catch case _: Exception => ())
      c.thread.interrupt()
      id
    }

  /** The scope thread; a `var` because the pool starts after construction (no side effects in
    * initialisers) and stops in tests. */
  private var scope: Option[Thread] = None

  /** Reconcile (an interrupted run is paused, not failed), re-enqueue queued runs, start the
    * workers. Idempotent. Returns how many runs were paused as interrupted. */
  def start(): Int =
    val (paused, queued) = reconcile()
    val _ = pruneTrace(force = true)
    queued.foreach(queue.put)
    if scope.isEmpty then
      stopping = false
      val t = Thread.ofPlatform().daemon(true).name("histmersion-agents").unstarted(() => pool())
      scope = Some(t)
      t.start()
      val w = Thread.ofPlatform().daemon(true).name("histmersion-agents-resume").unstarted(() => resumer())
      waker = Some(w)
      w.start()
    paused

  /** [[start]]'s first half: a run left `running` is paused as interrupted (and, when nobody watches
    * it, set to resume by itself shortly). Returns how many were paused, and the queued runs. */
  private[agents] def reconcile(): (Int, Vector[String]) =
    val (paused, queued) = AgentRepo.reconcile(ds)
    // a run nobody is watching goes on by itself after a restart; a person's waits for them
    val at = java.time.Instant.now().plusSeconds(math.max(0, cfg.watchdog.interruptedResumeMinutes).toLong * 60L)
    paused.foreach { (id, origin) =>
      if origin.flatMap(Origin.parse).exists(_.unattended) then
        val _ = AgentRepo.resumeAt(ds, id, at)
    }
    (paused.size, queued)

  /** The thread that resumes runs paused for an allowance; a `var` for the same reason as [[scope]]. */
  private var waker: Option[Thread] = None

  private def resumer(): Unit =
    // the stall check shares this loop: at least once a minute
    val every = math.min(60, math.max(5, cfg.allowanceCheckSeconds)).toLong * 1000L
    try
      while !stopping do
        Thread.sleep(every)
        try
          val now = java.time.Instant.now()
          val _ = checkStalls(now.toEpochMilli)
          val _ = resumeDue(now)
          val _ = resumeFreed(now)
        catch
          case e: InterruptedException => throw e
          case e: Exception            => scribe.warn(s"agent resume check failed: ${e.getMessage}")
    catch case _: InterruptedException => ()

  /** Re-queue every run paused for an allowance whose window has freed (`resume_at` ≤ now). Returns
    * the ids. When the window has not freed enough after all (another run spent it meanwhile), the run
    * simply pauses again with the next time. */
  def resumeDue(now: java.time.Instant): List[String] =
    AgentRepo.dueToResume(ds, now).toList.filter { id =>
      AgentRepo.transition(ds, id, RunState.Paused, RunState.Queued).map(_ => submit(id)).isDefined
    }

  /** Re-queue every run paused on a plan pool (`plan-quota`) that would get a model NOW, before the
    * reset it waits for: its role is picked again — which takes a fresh reading of a pool whose
    * verdict hangs only on a stale one (at most once per `stale-minutes` per pool), and sees a hold
    * released. A run whose pool is still below its floor stays paused; nothing is read for a server
    * with no such run. Returns the ids. */
  def resumeFreed(now: java.time.Instant): List[String] =
    AgentRepo.pausedFor(ds, PauseReason.PlanQuota, now).toList.flatMap { (id, pipeline, model, error) =>
      val roles = ModelPicker.pausedRole(error).map(List(_))
        .getOrElse(Pipeline.parse(pipeline).map(_.roles).getOrElse(Nil))
      val notes = List.newBuilder[String]
      // the run's own model for the role: its --model-for, else its --model
      val run = AgentRepo.get(ds, id)
      def modelOf(r: Role) = run.fold(model)(_.modelFor(r))
      val free = roles.find(r => agents.picker.pick(r, modelOf(r), now.toEpochMilli, Some(PlanQuota.RefreshTrigger.Paused),
        n => notes += s"${r.wire}: $n").isRight)
      free.flatMap { r =>
        AgentRepo.transition(ds, id, RunState.Paused, RunState.Queued).map { _ =>
          (notes.result() :+ s"resumed before its reset: the ${r.wire} chain has a usable model again")
            .foreach(w => AgentRepo.addWarning(ds, id, w.take(500)))
          scribe.info(s"agent run $id resumed: the ${r.wire} chain has a usable model again")
          submit(id)
          id
        }
      }
    }

  /** Set by [[stop]] before the pool is interrupted. A worker clears its thread's interrupt after each
    * run (a cancel aimed at one run must not leak into the next), which also swallowed the pool's own
    * shutdown interrupt when it arrived mid-run: the worker went back to `queue.take()`, the
    * supervised scope never finished, and the pool thread lived forever (#865). Workers check this
    * flag instead of relying on the interrupt surviving. */
  @volatile private var stopping = false

  def stop(): Unit =
    stopping = true
    scope.foreach(_.interrupt())
    scope = None
    waker.foreach(_.interrupt())
    waker = None

  def submit(id: String): Unit = queue.put(id)

  private def pool(): Unit =
    try
      ox.supervised {
        (1 to math.max(1, cfg.maxConcurrentRuns)).foreach(_ => { val _ = ox.forkUser(worker()) })
        ()
      }
    catch
      case _: InterruptedException => ()
      case e: Throwable            => scribe.error(s"agent pool died: ${e.getMessage}", e)

  private def worker(): Unit =
    var alive = true // the loop's exit flag; interruption of the pool clears it
    while alive && !stopping do
      try
        val id = queue.take()
        Thread.interrupted() // a cancel aimed at the previous run must not leak into this one
        if !stopping then
          execute(id)
          val _ = pruneTrace()
      catch case _: InterruptedException => alive = false

  /** Run one queued run to its next resting state. */
  private[agents] def execute(id: String): Unit =
    AgentRepo.get(ds, id) match
      case None => ()
      case Some(run) =>
        val pipe = Pipeline.parse(run.pipeline).flatMap(pipelines.get)
        val sameVersion = Pipeline.parse(run.pipeline).forall(p => AgentRepo.pipelineVersion(ds, id).forall(_ == p.version))
        if AgentRepo.transition(ds, id, RunState.Queued, RunState.Running).isDefined then
          val ctl = Control(Thread.currentThread())
          running.put(id, ctl)
          val outcome: Either[Halt, String] =
            try
              pipe match
                case None => Left(Halt.Fail(s"no pipeline '${run.pipeline}' in this build"))
                case Some(_) if !sameVersion => Left(Halt.Fail("the pipeline's version changed since this run started; start a new run"))
                case Some(p) =>
                  val hooks = CliProcess.Hooks(pr => { val _ = ctl.procs.add(pr) }, pr => { val _ = ctl.procs.remove(pr) },
                    () => beat(id))
                  CliProcess.withHooks(hooks)(p.run(RunCtx(run.copy(status = RunState.Running.wire), this)))
            catch
              case _: InterruptedException if ctl.cancel => Left(Halt.Cancel)
              case _: InterruptedException if ctl.stalled.isDefined => Left(ctl.stalled.get)
              case e: InterruptedException =>
                // the pool stopping under a run nobody watches: it goes on after the restart by itself
                val at = Option.when(run.origin.flatMap(Origin.parse).exists(_.unattended))(
                  java.time.Instant.now().plusSeconds(math.max(0, cfg.watchdog.interruptedResumeMinutes).toLong * 60L))
                Left(Halt.Pause(PauseReason.Interrupted, "interrupted", at))
              case e: Exception =>
                scribe.error(s"agent run $id failed unexpectedly: ${e.getMessage}", e)
                Left(Halt.Fail(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
            finally
              running.remove(id)
              Thread.interrupted()
          // a cancel or a stall decides how the run settles, whatever the interrupted step made of it
          val settled = if ctl.cancel then Left(Halt.Cancel) else ctl.stalled.map(Left(_)).getOrElse(outcome)
          val _ = settled match
            case Right(summary)                => AgentRepo.transition(ds, id, RunState.Running, RunState.Done, progress = Some(summary.take(500)))
            case Left(Halt.Cancel)             => AgentRepo.transition(ds, id, RunState.Running, RunState.Cancelled, error = Some("cancelled"))
            case Left(Halt.Fail(m))            => AgentRepo.transition(ds, id, RunState.Running, RunState.Failed, error = Some(m.take(2000)))
            case Left(Halt.Pause(reason, why, at)) =>
              AgentRepo.transition(ds, id, RunState.Running, RunState.Paused, error = Some(s"${reason.wire}: ${why.take(1000)}"), resumeAt = at)
            case Left(Halt.Wait(approval, why)) =>
              val waiting = AgentRepo.transition(ds, id, RunState.Running, RunState.WaitingApproval, progress = Some(why.take(500)))
              // decided in the instant between opening it and this transition: go straight back to the queue
              if waiting.isDefined && AgentRepo.approval(ds, approval).exists(_.state != ApprovalState.Open.wire) then
                val _ = wake(id)
              waiting
          notify(run, settled)
          AgentRepo.get(ds, id).foreach(r => settledHooks.foreach(f =>
            try f(r) catch case e: Exception => scribe.warn(s"agent run $id: a settle hook failed: ${e.getMessage}")))

  /** A run nobody is watching (started by a schedule or an ingest) that fails or pauses leaves a
    * backlog item — the notification of §5.2, without an external service (never a dry run). An ingest's run paused
    * for an allowance or a plan floor resumes by itself, so it leaves none (a batch of imports would
    * otherwise leave one per source each time a plan dips below its floor). */
  private def notify(run: RunInfo, settled: Either[Halt, String]): Unit =
    val scheduled = run.origin.contains(Origin.Scheduler.wire)
    val ingested  = run.origin.contains(Origin.Ingest.wire)
    // a dry run writes nothing, its backlog item included
    if (scheduled || ingested) && !run.dryRun then
      val why = settled match
        case Left(Halt.Fail(m))         => Some(s"failed: ${m.take(300)}")
        case Left(Halt.Pause(r, _, _)) if ingested && (r == PauseReason.Allowance || r == PauseReason.PlanQuota) => None
        case Left(Halt.Pause(r, d, _))  => Some(s"paused (${r.wire}): ${d.take(300)}")
        case _                          => None
      why.foreach { w =>
        val text =
          if scheduled then s"scheduled ${run.pipeline}${run.goal.fold("")(g => s" for goal $g")} (agent run ${run.id.take(8)}) $w"
          else s"${run.pipeline} of [[${run.target}]] queued by its import (agent run ${run.id.take(8)}) $w"
        val _ = QueueRepo.add(ds, com.histmersion.core.Queue.Kind.Tasks, com.histmersion.core.Queue.QueueAdd(
          text, topic = Some(if scheduled then "agent schedules" else "auto-enrich")))
      }

  /** A waiting run whose approval was decided goes back to the queue. `false` = it was not waiting. */
  def wake(id: String): Boolean =
    AgentRepo.transition(ds, id, RunState.WaitingApproval, RunState.Queued).map(_ => submit(id)).isDefined

  /** Between steps: a requested pause or cancel takes effect here. */
  private[agents] def checkControl(id: String): Either[Halt, Unit] =
    Option(running.get(id)) match
      case Some(c) if c.cancel => Left(Halt.Cancel)
      case Some(c) if c.pause  => Left(Halt.Pause(PauseReason.Requested, "paused on request"))
      case _                   => Right(())

  /** Before an LLM call: the run's own budget, and the global daily and monthly caps. */
  private[agents] def checkBudget(id: String): Either[Halt, Unit] =
    AgentRepo.get(ds, id) match
      case None => Left(Halt.Fail("the run disappeared"))
      case Some(r) =>
        val (day, month) = AgentRepo.spendDayMonth(ds)
        if r.costUsd >= r.budgetUsd then Left(Halt.Pause(PauseReason.Budget, s"the run spent ${AgentsMarkdown.usd(r.costUsd)} of its ${AgentsMarkdown.usd(r.budgetUsd)} USD"))
        else if r.usage.budgeted(cfg.budgets.cacheReadWeight) >= r.budgetTokens then Left(Halt.Pause(PauseReason.Budget,
          s"the run used ${r.usage.budgeted(cfg.budgets.cacheReadWeight)} of its ${r.budgetTokens} budget tokens " +
            s"(cache reads count ${cfg.budgets.cacheReadWeight}: agents.budgets.cache-read-weight)"))
        else if day >= BigDecimal(cfg.budgets.dailyUsd) then Left(Halt.Pause(PauseReason.Budget, s"today's agent spend reached agents.budgets.daily-usd (${cfg.budgets.dailyUsd})"))
        else if month >= BigDecimal(cfg.budgets.monthlyUsd) then Left(Halt.Pause(PauseReason.Budget, s"this month's agent spend reached agents.budgets.monthly-usd (${cfg.budgets.monthlyUsd})"))
        else Right(())

  private[agents] def budgetInterceptor(id: String, billing: Billing): RunBudget =
    val r = AgentRepo.get(ds, id)
    val (use, cost) = AgentRepo.runSpend(ds, id)
    RunBudget(cost, use.budgeted(cfg.budgets.cacheReadWeight), r.map(x => BigDecimal(x.budgetUsd)).getOrElse(BigDecimal(0)),
      r.map(_.budgetTokens).getOrElse(0L), billing, cfg.budgets.cacheReadWeight)

  // ── control ────────────────────────────────────────────────────────────────────────────────────

  private def conflict(m: String) = ApiError(m, Some(AgentsApi.CodeConflict))

  def pause(id: String): Either[ApiError, RunInfo] =
    withRun(id) { r =>
      RunState.parse(r.status) match
        case Some(RunState.Running) =>
          Option(running.get(id)).foreach(_.pause = true)
          Right(r)
        case Some(RunState.Queued) =>
          AgentRepo.transition(ds, id, RunState.Queued, RunState.Paused, error = Some(s"${PauseReason.Requested.wire}: paused before it started"))
            .toRight(conflict(s"run $id moved on; look again")).flatMap(_ => get(id))
        case _ => Left(conflict(s"run $id is ${r.status}; only a queued or running run can be paused"))
    }

  def resume(id: String, budgetUsd: Option[Double], budgetTokens: Option[Long]): Either[ApiError, RunInfo] =
    withRun(id) { r =>
      if !RunState.parse(r.status).contains(RunState.Paused) then Left(conflict(s"run $id is ${r.status}; only a paused run can be resumed"))
      else
        AgentRepo.setBudget(ds, id, budgetUsd.map(BigDecimal(_)), budgetTokens)
        AgentRepo.transition(ds, id, RunState.Paused, RunState.Queued)
          .toRight(conflict(s"run $id moved on; look again"))
          .flatMap { _ => submit(id); get(id) }
    }

  def cancel(id: String): Either[ApiError, RunInfo] =
    withRun(id) { r =>
      RunState.parse(r.status) match
        case Some(RunState.Running) =>
          Option(running.get(id)).foreach { c => c.cancel = true; c.thread.interrupt() }
          Right(r)
        case Some(s @ (RunState.Queued | RunState.Paused | RunState.WaitingApproval)) =>
          AgentRepo.transition(ds, id, s, RunState.Cancelled, error = Some("cancelled"))
            .toRight(conflict(s"run $id moved on; look again"))
            .flatMap { _ =>
              val _ = AgentRepo.closeForRun(ds, id, "rejected: the run was cancelled")
              get(id)
            }
        case _ => Left(conflict(s"run $id is ${r.status}; it has already finished"))
    }

  private def get(id: String): Either[ApiError, RunInfo] =
    AgentRepo.get(ds, id).toRight(ApiError(s"No agent run '$id'", Some(Write.CodeNotFound)))

  private def withRun[A](id: String)(f: RunInfo => Either[ApiError, A]): Either[ApiError, A] = get(id).flatMap(f)

/** The trace of one LLM step, from the conversation sttp-ai's loop built: prompts, assistant turns,
  * tool calls with their results — every body truncated to `max` characters. */
object Engine:
  /** How long a run waits for a slot before it beats again (waiting behind other runs is not a stall). */
  val BeatWhileWaitingSeconds = 30L

  /** The warning a stall leaves on its run, `<mark> <seq> (…)`; counting them per step decides the fail. */
  val StallMark = "stalled at step"

  /** The tool-loop turns of one step: its agent's `max-turns`, never the global `agents.step.max-iterations`
    * (which once capped a reviewer meant for 20 lookups at 6). */
  def stepTurns(agent: AgentDef): Int = math.max(1, agent.maxTurns)

  /** [[stepTurns]], unless the pipeline sized this one call itself (`review-article`'s fixer gets more
    * turns the more findings it has to settle). */
  def stepTurns(agent: AgentDef, overridden: Option[Int]): Int = overridden.fold(stepTurns(agent))(math.max(1, _))

  /** The note of a blocking approval a dry run did not open (it goes on as if approved). */
  val DryRunApproval = "dry run: not recorded; treated as approved"

  /** The config key that sets a step's output cap: `agents.step.max-tokens`, unless the pipeline
    * passed the step its own cap (the fixer's and the writer's). */
  def capKey(overridden: Boolean): String =
    if overridden then "the step's own cap (agents.review.fixer-max-tokens or agents.compile.draft-max-tokens)"
    else "agents.step.max-tokens (HISTMERSION_AGENTS_MAX_TOKENS)"

  private val OutputCap = "exceeded the (\\d+) output token maximum".r

  /** #1450: the output cap a provider says the answer ran into — Claude Code's "Claude's response
    * exceeded the 4096 output token maximum", in its error result or the failure's text. The CLI has
    * already retried the call itself by then (a failed cold-reader attempt recorded 16384 output
    * tokens: four 4096-token tries), and the same cap fails the same way again, so this is a
    * CONFIGURATION error: the step stops at once rather than spending its retries. */
  def outputCapHit[Out](res: AgentResult[Either[AgentFailure, Out]]): Option[Int] =
    val texts = (res.finishReason match
      case FinishReason.Error(m) => List(m)
      case _                     => Nil) ++ res.finalAnswer.left.toOption.map(_.rawAnswer).toList
    texts.iterator.flatMap(t => OutputCap.findFirstMatchIn(t)).flatMap(_.group(1).toIntOption).nextOption()

  /** What the run says when [[outputCapHit]]: the cap, the model, the key to raise. */
  def outputCapMessage(name: String, cap: Int, sent: Int, model: String, key: String): String =
    s"$name: $model's answer exceeded the $cap output-token cap (the step sent $sent) — raise $key " +
      "(a thinking model's reasoning counts toward it), restart the server, then resume the run"

  /** The raw text of an answer that was JSON but not the typed answer — what a retry hands back to
    * be amended. None for no answer at all, or a decoded one. */
  def undecoded(a: Either[AgentFailure, ?]): Option[String] =
    val raw = a match
      case Left(AgentParseError(r, _))         => Some(r)
      case Left(AgentIncomplete(r, _, Some(_))) => Some(r) // an HTTP loop that gave up on undecodable answers
      case _                                    => None
    raw.map(_.trim).filter(_.nonEmpty)

  /** How much of the raw answer a decode problem quotes. */
  val AnswerExcerpt = 300

  /** #941: why an answer is not the step's typed answer, for the step's record and the retry prompt:
    * the decoder's own error — WHERE in the answer (the JSON path) and WHAT (missing field, wrong
    * type) — then the start of the answer; or, for no answer at all, how the call ended. */
  def answerProblem(f: AgentFailure, finish: FinishReason): String =
    def excerpt(raw: String) =
      val r = raw.trim
      if r.isEmpty then "" else s" — the answer began: ${r.take(AnswerExcerpt)}"
    def why(cause: Throwable): String = cause match
      case d: io.circe.DecodingFailure =>
        val path = io.circe.CursorOp.opsToPath(d.history)
        s"the answer does not match the schema at ${if path.isEmpty then "the top level" else path}: ${d.message}"
      case p: io.circe.ParsingFailure => s"the answer is not valid JSON: ${p.message}"
      case e => s"the answer could not be decoded: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}"
    f match
      case AgentParseError(raw, cause) => why(cause) + excerpt(raw)
      // sttp-ai's own loop re-asks after an undecodable answer and, out of turns, keeps the last error
      case AgentIncomplete(raw, fin, Some(cause)) => s"${why(cause)} (${LlmClient.finish(fin)})${excerpt(raw)}"
      case other =>
        s"the answer is not the JSON the schema asks for (${LlmClient.finish(finish)})${excerpt(other.rawAnswer)}"

object Trace:
  def messages(h: ConversationHistory, latencies: Vector[Int], max: Int): List[AgentRepo.MessageRecord] =
    def cut(s: String) = if s.length <= max then s else s.take(max) + s"… (${s.length - max} more chars)"
    // an assistant entry closes one LLM call: hand it that call's latency, in order
    val lat = latencies.iterator
    h.entries.toList.flatMap {
      case ConversationEntry.UserPrompt(c) =>
        List(("user", Some(cut(c)), None, None, None, None))
      case ConversationEntry.AssistantResponse(c, calls) =>
        val ms = Option.when(lat.hasNext)(lat.next())
        ("assistant", Option(c).filter(_.nonEmpty).map(cut), None, None, None, ms) ::
          calls.toList.map(tc => ("tool-call", None, Some(tc.toolName), Some(cut(tc.input)), None, None))
      case ConversationEntry.ToolResult(_, name, result) =>
        List(("tool", None, Some(name), None, Some(cut(result)), None))
      case _ => Nil
    }.zipWithIndex.map { case ((role, content, tool, input, result, ms), n) =>
      AgentRepo.MessageRecord(n + 1, role, content, tool, input, result, ms)
    }

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
        progress(name)
        val started = java.time.Instant.now()
        val out =
          try body
          catch
            case e: InterruptedException => throw e
            case e: Exception => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
        val attempt = AgentRepo.attempts(ds, run.id, seq) + 1
        out match
          case Right(o) =>
            AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, attempt, name, StepKind.Code, StepStatus.Done,
              Some(o.asJson.noSpaces), None, None, Usage.Zero, BigDecimal(0), None, started), Nil)
            Right(o)
          case Left(m) =>
            AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, attempt, name, StepKind.Code, StepStatus.Failed,
              None, None, None, Usage.Zero, BigDecimal(0), Some(m.take(2000)), started), Nil)
            Left(Halt.Fail(s"$name: $m"))
      }
    }

  /** A BLOCKING approval step (docs/research-embedded-agents.md §14): the first time the run reaches
    * it, `payload` (the proposal as JSON) is recorded as an approval of `kind` about `subject`, and the
    * run stops in `waiting` without holding a worker. Deciding the approval re-queues the run; it
    * replays its finished steps, reaches this step again and gets the decision, which is then
    * checkpointed like any output. In `auto` mode the approval is recorded already approved and the run
    * goes on at once. A resumed run finds the approval it opened, never a second one. */
  def approval(name: String, kind: ApprovalKind, subject: String, mode: ApprovalMode)(
      payload: => Either[String, String]): Either[Halt, Decided] =
    val seq = claimSeq()
    replay[Decided](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        val started = java.time.Instant.now()
        def done(d: Decided): Either[Halt, Decided] =
          AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, AgentRepo.attempts(ds, run.id, seq) + 1, name,
            StepKind.Code, StepStatus.Done, Some(d.asJson.noSpaces), None, None, Usage.Zero, BigDecimal(0), None, started), Nil)
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
    replay[O](seq, name).getOrElse {
      engine.checkControl(run.id).flatMap { _ =>
        val defs = engine.agents.definitions
        (for
          d   <- defs.agent(agent)
          sys <- defs.system(d, ToolStyle.Bare)
        yield (d, Prompt(sys, user))).left.map(m => Halt.Fail(s"$name: $m")).flatMap { (d, prompt) =>
          attempts(seq, name, d, prompt, schema, maxTokens.getOrElse(cfg.step.maxTokens), Engine.stepTurns(d, turns), validate)
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
    engine.agents.picker.pick(role, run.model) match
      case Left(PickFailure.Unavailable(m)) => Left(Halt.Fail(s"$name: $m"))
      case Left(PickFailure.Exhausted(m, at, why)) => Left(Halt.Pause(why, s"$name: $m", at))
      case Right(p) =>
        p.skipped.filter(warnedSkips.add).foreach(why => warn(s"${role.wire}: passed over $why; used ${p.client.model.wire}"))
        Right(p)

  private def attempts[A, O: {Encoder, Decoder}](seq: Int, name: String, agent: AgentDef,
      prompt: Prompt, schema: ResponseSchema[A], maxTokens: Int, turns: Int,
      validate: (A, Boolean) => Either[List[String], Checked[O]]): Either[Halt, O] =
    val role        = agent.role
    val maxAttempts = 1 + math.max(0, cfg.step.maxRetries)
    // a resumed step's earlier attempts count as answers judged: its retries are not handed out twice
    val before = AgentRepo.attempts(ds, run.id, seq)
    // tail-recursive retry loop over attempts; `problems` are the previous attempt's rejections and
    // `tries` counts the answers judged so far (an attempt cut short by a model's allowance or plan
    // limit is not an answer: it moves on to the chain's next model without using up a retry)
    @annotation.tailrec
    def go(attempt: Int, tries: Int, problems: List[String], previous: Option[String]): Either[Halt, O] =
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
          val latency = LatencyInterceptor(engine.llmSlots)
          val allowance = AllowanceGuard(picked.headroomTokens, picked.headroomUsd, billing)
          // the agent's own `max-turns` (or the step's override): a reviewer that must look up 6–10
          // claims needs more than a one-shot step
          val call = LlmCall[A](prompt.system, user, engine.agents.toolbox.tools(agent.tools), schema,
            turns, maxTokens, List(latency, budget, allowance), Some(agent),
            Some(RunScope(run.id, seq)))
          // a plan pool takes at most its `max-calls-in-flight` calls at once, across every run
          val poolSlot = engine.agents.pools.poolOf(client.model).map(engine.agents.pools.slot)
          AgentsHandlers.call(client.model) {
            poolSlot.foreach(_.acquire())
            try client.run(call) finally poolSlot.foreach(_.release())
          } match
            case Left(Left(limit)) =>
              val until = engine.agents.picker.cool(client.model, limit.until)
              record(seq, attempt, name, client, prompt, Usage.Zero, billing, None, Some(s"${limit.getMessage} — skipped until $until".take(2000)), started, Nil)
              go(attempt + 1, tries, problems, previous)
            case Left(Right(e)) =>
              record(seq, attempt, name, client, prompt, Usage.Zero, billing, None, Some(e.message), started, Nil)
              Left(Halt.Pause(PauseReason.Provider, s"$name: ${e.message} — fix the provider, then resume"))
            case Right(res) =>
              val use  = LlmClient.usage(res.usage)
              val msgs = Trace.messages(res.history, latency.millis, cfg.trace.maxToolResultChars)
              res.finishReason match
                case FinishReason.BudgetExceeded =>
                  record(seq, attempt, name, client, prompt, use, billing, None, Some("budget exhausted"), started, msgs)
                  Left(Halt.Pause(PauseReason.Budget, s"$name: the run's budget is exhausted"))
                case FinishReason.Custom(AllowanceGuard.Reason) =>
                  record(seq, attempt, name, client, prompt, use, billing, None,
                    Some(s"${client.model.wire} reached its allowance during the step; the chain's next model takes it"), started, msgs)
                  go(attempt + 1, tries, problems, previous)
                case _ =>
                  val verdict = res.finalAnswer match
                    case Left(f) => Left(List(s"the answer is not the JSON the schema asks for (${LlmClient.finish(res.finishReason)}): ${f.rawAnswer.take(300)}"))
                    case Right(a) => validate(a, last)
                  // a well-formed answer that failed a check goes back with the problems, to be amended
                  val rejected = res.finalAnswer.toOption.map(a => schema.codec(a).noSpaces)
                  verdict match
                    case Right(Checked(o, ws)) =>
                      record(seq, attempt, name, client, prompt, use, billing, Some(o.asJson.noSpaces), None, started, msgs)
                      ws.foreach(w => warn(s"$name: $w"))
                      Right(o)
                    case Left(ps) =>
                      record(seq, attempt, name, client, prompt, use, billing, None, Some(ps.mkString("; ").take(2000)), started, msgs)
                      if last then Left(Halt.Fail(s"$name: no valid answer after $attempt attempt(s): ${ps.take(3).mkString("; ")}"))
                      else go(attempt + 1, tries + 1, ps, rejected)
    go(before + 1, before, Nil, None)

  private def record(seq: Int, attempt: Int, name: String, client: LlmClient, prompt: Prompt, use: Usage,
      billing: Billing, output: Option[String], error: Option[String], started: java.time.Instant,
      msgs: List[AgentRepo.MessageRecord]): Unit =
    val cost = billing.cost(use)
    AgentRepo.record(ds, run.id, AgentRepo.StepRecord(seq, attempt, name, StepKind.Llm,
      if output.isDefined then StepStatus.Done else StepStatus.Failed, output, Some(prompt.hash),
      Some(client.model.wire), use, cost, error, started), msgs)

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
private[agents] final class LatencyInterceptor(slots: Semaphore) extends AgentInterceptor[Identity]:
  /** Per-call latencies, in call order. A `var` local to one step's interceptor instance. */
  @volatile var millis: Vector[Int] = Vector.empty
  override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
    slots.acquire()
    val t0 = System.nanoTime()
    try next
    finally
      millis = millis :+ ((System.nanoTime() - t0) / 1000000).toInt
      slots.release()

/** The run's remaining budget, checked by the sttp-ai loop before every iteration: once the spend
  * recorded before this step plus this step's own calls reaches the run's cap (cost or tokens), the
  * loop is told to finish, and the engine pauses the run. Our [[Agents.Billing]] prices the calls (an
  * unpriced metered model never gets this far); a plan model costs 0 USD, so only its tokens count. */
private[agents] final class RunBudget(spentUsd: BigDecimal, spentTokens: Long, capUsd: BigDecimal, capTokens: Long,
    billing: Billing) extends AgentInterceptor[Identity]:
  override def decide(state: AgentRunState): LoopDecision =
    val use  = LlmClient.usage(state.usage)
    val cost = billing.cost(use)
    if spentUsd + cost >= capUsd || spentTokens + use.total >= capTokens then
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

  /** What a person asked of a RUNNING run, read by the run between steps. */
  private final class Control(val thread: Thread):
    @volatile var pause: Boolean  = false
    @volatile var cancel: Boolean = false
  private val running = ConcurrentHashMap[String, Control]()

  /** The scope thread; a `var` because the pool starts after construction (no side effects in
    * initialisers) and stops in tests. */
  private var scope: Option[Thread] = None

  /** Reconcile (an interrupted run is paused, not failed), re-enqueue queued runs, start the
    * workers. Idempotent. Returns how many runs were paused as interrupted. */
  def start(): Int =
    val (paused, queued) = AgentRepo.reconcile(ds)
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

  /** The thread that resumes runs paused for an allowance; a `var` for the same reason as [[scope]]. */
  private var waker: Option[Thread] = None

  private def resumer(): Unit =
    val every = math.max(5, cfg.allowanceCheckSeconds).toLong * 1000L
    try
      while !stopping do
        Thread.sleep(every)
        try { val _ = resumeDue(java.time.Instant.now()) }
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
                case Some(p) => p.run(RunCtx(run.copy(status = RunState.Running.wire), this))
            catch
              case _: InterruptedException if ctl.cancel => Left(Halt.Cancel)
              case e: InterruptedException => Left(Halt.Pause(PauseReason.Interrupted, "interrupted"))
              case e: Exception =>
                scribe.error(s"agent run $id failed unexpectedly: ${e.getMessage}", e)
                Left(Halt.Fail(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
            finally
              running.remove(id)
              Thread.interrupted()
          val settled = if ctl.cancel then Left(Halt.Cancel) else outcome
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

  /** A run nobody is watching (started by a schedule or an ingest) that fails or pauses leaves a
    * backlog item — the notification of §5.2, without an external service. An ingest's run paused
    * for an allowance or a plan floor resumes by itself, so it leaves none (a batch of imports would
    * otherwise leave one per source each time a plan dips below its floor). */
  private def notify(run: RunInfo, settled: Either[Halt, String]): Unit =
    val scheduled = run.origin.contains(Origin.Scheduler.wire)
    val ingested  = run.origin.contains(Origin.Ingest.wire)
    if scheduled || ingested then
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
        else if r.usage.total >= r.budgetTokens then Left(Halt.Pause(PauseReason.Budget, s"the run used ${r.usage.total} of its ${r.budgetTokens} tokens"))
        else if day >= BigDecimal(cfg.budgets.dailyUsd) then Left(Halt.Pause(PauseReason.Budget, s"today's agent spend reached agents.budgets.daily-usd (${cfg.budgets.dailyUsd})"))
        else if month >= BigDecimal(cfg.budgets.monthlyUsd) then Left(Halt.Pause(PauseReason.Budget, s"this month's agent spend reached agents.budgets.monthly-usd (${cfg.budgets.monthlyUsd})"))
        else Right(())

  private[agents] def budgetInterceptor(id: String, billing: Billing): RunBudget =
    val r = AgentRepo.get(ds, id)
    val (use, cost) = AgentRepo.runSpend(ds, id)
    RunBudget(cost, use.total, r.map(x => BigDecimal(x.budgetUsd)).getOrElse(BigDecimal(0)),
      r.map(_.budgetTokens).getOrElse(0L), billing)

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
  /** The tool-loop turns of one step: its agent's `max-turns`, never the global `agents.step.max-iterations`
    * (which once capped a reviewer meant for 20 lookups at 6). */
  def stepTurns(agent: AgentDef): Int = math.max(1, agent.maxTurns)

  /** [[stepTurns]], unless the pipeline sized this one call itself (`review-article`'s fixer gets more
    * turns the more findings it has to settle). */
  def stepTurns(agent: AgentDef, overridden: Option[Int]): Int = overridden.fold(stepTurns(agent))(math.max(1, _))

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

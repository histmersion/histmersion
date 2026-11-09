package com.histmersion
package api.automation
import com.histmersion.api.Problem
import hearth.kindlings.optics.syntax.*

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year

object Agents:
  type Action = Action.T
  object Action extends Newtype[String, Pure]

  export Approval.Kind as ApprovalKind
  export Approval.Mode as ApprovalMode
  export Approval.State as ApprovalState
  export Run.State as RunState
  export Step.Status as StepStatus
  export com.histmersion.api.automation.{AgentRole as Role, Pipeline, ProviderKind, TargetKind}

  /** The model id may itself contain colons (`local:qwen2.5-coder:32b`), so only the first colon separates the
    * provider.
    */
  final case class ModelRef(provider: ProviderKind, model: ModelRef.Model) derives CanEqual:
    def wire: ModelRef.Wire = ModelRef.Wire(s"${provider.wire}:$model")

  object ModelRef:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    type Model = Model.T
    object Model extends Newtype[String, Pure]:
      type Spec = Spec.T
      object Spec extends Newtype[String, Pure]

    def parse(
        s: Wire,
        known: Set[com.histmersion.api.automation.ProviderKind.Plugin.Name] = Set.empty
    ): Either[Problem, ModelRef] =
      val t = (s).value.trim
      t.indexOf(':') match
        case -1 => Left(Problem(s"'$t' is not provider:model (provider: ${ProviderKind.names(known)})"))
        case i  =>
          val (p, m) = (t.substring(0, i), t.substring(i + 1).trim)
          ProviderKind.parse(com.histmersion.api.automation.ProviderKind.Wire(p), known) match
            case None => Left(Problem(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.names(known)})"))
            case Some(_) if m.isEmpty => Left(Problem(s"'$t' names no model"))
            case Some(k)              => Right(ModelRef(k, Model(m)))

  /** `input` counts all prompt tokens, cached ones included (the provider-normalised convention sttp-ai uses);
    * `cacheRead`/`cacheWrite` are subsets of it.
    */
  final case class Usage(
      input: Usage.Input = Usage.Input((0).toLong),
      output: Usage.Output = Usage.Output((0).toLong),
      cacheRead: Usage.CacheRead = Usage.CacheRead((0).toLong),
      cacheWrite: Usage.CacheWrite = Usage.CacheWrite((0).toLong)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def +(o: Usage): Usage =
      Usage(
        Usage.Input((input).value + (o.input).value),
        Usage.Output((output).value + (o.output).value),
        Usage.CacheRead((cacheRead).value + (o.cacheRead).value),
        Usage.CacheWrite((cacheWrite).value + (o.cacheWrite).value)
      )
    def total: Usage.TotalTokens = Usage.TotalTokens((input).value + (output).value)

    def budgeted(cacheReadWeight: Usage.CacheReadWeight): Usage.BudgetedTokens =
      Usage.BudgetedTokens(
        math.max(0L, (input).value - (cacheRead).value) + (output).value + math.round(
          (cacheRead).value.toDouble * math.max(0.0, cacheReadWeight.value)
        )
      )

  object Usage:
    type BudgetedTokens = BudgetedTokens.T
    object BudgetedTokens extends Newtype[Long, Pure]
    type CacheReadWeight = CacheReadWeight.T
    object CacheReadWeight extends Newtype[Double, Pure]
    type TotalTokens = TotalTokens.T
    object TotalTokens extends Newtype[Long, Pure]

    type Input = Input.T
    object Input extends Newtype[Long, Pure]
    type Output = Output.T
    object Output extends Newtype[Long, Pure]
    type CacheRead = CacheRead.T
    object CacheRead extends Newtype[Long, Pure]
    type CacheWrite = CacheWrite.T
    object CacheWrite extends Newtype[Long, Pure]

    val Zero: Usage = Usage()

  final case class Price(
      input: BigDecimal,
      output: BigDecimal,
      cacheRead: Option[BigDecimal] = None,
      cacheWrite: Option[BigDecimal] = None
  ):
    def cost(u: Usage): BigDecimal =
      val plain = math.max(u.input.value - u.cacheRead.value - u.cacheWrite.value, 0L)
      (BigDecimal(plain) * input + BigDecimal(u.cacheRead.value) * cacheRead.getOrElse(input) +
        BigDecimal(u.cacheWrite.value) * cacheWrite.getOrElse(input) + BigDecimal(
          u.output.value
        ) * output) / Prices.Million

    def text: String =
      val n = (d: BigDecimal) => d.bigDecimal.stripTrailingZeros.toPlainString
      ((List(input, output) ++ cacheRead.zip(cacheWrite).toList.flatMap((r, w) => List(r, w))).map(n).mkString("/"))

  enum Billing derives CanEqual:
    case Plan(pool: PlanQuota.PoolId)
    case Metered(price: Price)

    def cost(u: Usage): BigDecimal = this match
      case Plan(_)    => BigDecimal(0)
      case Metered(p) => p.cost(u)

    def describe: String = this match
      case Plan(p)    => (s"plan pool: ${p.wire}")
      case Metered(p) => (s"price: ${p.text} USD per million tokens")

  object Prices:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    export ModelRef.Model as CostModel

    private[Agents] val Million = BigDecimal(1000000)

    def parse(s: Wire): Either[Problem, Map[ModelRef.Model, Price]] =
      val entries = (s).value.split(";").toList.map(_.trim).filter(_.nonEmpty)
      val parsed = entries.map { e =>
        e.lastIndexOf('=') match
          case -1 => Left(s"'$e' is not <model>=<input>/<output>")
          case i  =>
            val model = e.substring(0, i).trim
            val nums = e.substring(i + 1).split("/").toList.map(_.trim)
            val dec = nums.map(n => scala.util.Try(BigDecimal(n)).toOption.filter(_ >= 0))
            if model.isEmpty then Left(s"'$e' names no model")
            else if dec.exists(_.isEmpty) then Left(s"'$e': prices must be non-negative numbers")
            else
              dec.flatten match
                case List(in, out)         => Right(ModelRef.Model(model) -> Price(in, out))
                case List(in, out, cr, cw) => Right(ModelRef.Model(model) -> Price(in, out, Some(cr), Some(cw)))
                case _ => Left(s"'$e': give 2 prices (input/output) or 4 (input/output/cache-read/cache-write)")
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(parsed.collect { case Right(kv) => kv }.toMap)
        case errs => Left(Problem(errs.mkString("; ")))

    /** An unpriced model is an error, never zero: sttp-ai's `PriceTable` counts an unknown model as free, so it would
      * never trip a cost budget.
      */
    def cost(prices: Map[ModelRef.Model, Price], model: Prices.CostModel, u: Usage): Either[Problem, BigDecimal] =
      (prices.get(model).toRight(unpriced(model)).map(_.cost(u))).left.map(refactorError => Problem(refactorError))

    def billing(
        prices: Map[ModelRef.Model, Price],
        m: ModelRef,
        pool: Option[PlanQuota.PoolId]
    ): Either[Problem, Billing] =
      pool match
        case Some(p) => Right(Billing.Plan(p))
        case None    => prices.get(m.model).map(Billing.Metered(_)).toRight(Problem(unpriced(m.model) + metered(m)))

    private def unpriced(model: ModelRef.Model): String =
      (s"model '$model' has no price — add it to agents.prices (HISTMERSION_AGENTS_PRICES), e.g. \"$model=0/0\" for a free local model")

    private def metered(m: ModelRef): String = m.provider match
      case ProviderKind.ClaudeCode => (" (claude-code is set to use an API key, so it bills the API, not the plan)")
      case ProviderKind.Codex      => (" (codex is set to use an API key, so it bills the API, not the plan)")
      case ProviderKind.Opencode   => (" (an opencode model is metered unless it is openai/… on a ChatGPT login)")
      case _                       => ("")

  val JobKind = AgentJobKinds.Agent.kind.value

  enum PauseReason(val wire: PauseReason.Wire) derives CanEqual:
    case Budget extends PauseReason(PauseReason.Wire("budget"))
    case Interrupted extends PauseReason(PauseReason.Wire("interrupted"))
    case Requested extends PauseReason(PauseReason.Wire("requested"))
    case Provider extends PauseReason(PauseReason.Wire("provider"))

    case Allowance extends PauseReason(PauseReason.Wire("allowance"))

    case PlanQuota extends PauseReason(PauseReason.Wire("plan-quota"))

    case Stalled extends PauseReason(PauseReason.Wire("stalled"))

  object PauseReason:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

  enum StepKind(val wire: StepKind.Wire) derives CanEqual:
    case Code extends StepKind(StepKind.Wire("code"))
    case Llm extends StepKind(StepKind.Wire("llm"))

  object StepKind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[StepKind] = values.find(_.wire == s)

  enum Origin(val wire: Origin.Wire) derives CanEqual:
    case Person extends Origin(Origin.Wire("person"))
    case Scheduler extends Origin(Origin.Wire("scheduler"))
    case Run extends Origin(Origin.Wire("run"))

    /** A run an ingest started, by a name no other type takes. */
    case FromIngest extends Origin(Origin.Wire("ingest"))

    case Manager extends Origin(Origin.Wire("manager"))

    def unattended: Boolean = (this != Person)

  object Origin:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Origin] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  final case class ProbeRequest(citekey: Citekey, model: Option[ProbeRequest.Model] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ProbeRequest:
    type Model = Model.T
    object Model extends Newtype[String, Pure]

  final case class ProbeAnswer(citekey: Citekey, title: com.histmersion.api.library.Title, year: Option[Year])
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    /** A strict-schema provider sends `""` and `null` where it has nothing; they must not pass. */
    def problems(recordYear: Option[ProbeAnswer.RecordYear]): List[Problem] = List(
      Option.when(Option(citekey).forall(_.value.trim.isEmpty))(
        Problem("the citekey is empty — give the source's citekey")
      ),
      Option.when(Option(title).forall(_.value.trim.isEmpty))(
        Problem("the title is empty — copy the source's exact title from get_source")
      ),
      Option.when(year.isEmpty && recordYear.isDefined)(
        Problem("the year is missing — give the source's publication year from get_source")
      )
    ).flatten

  object ProbeAnswer:
    /** The year asked of a model stays the 32-bit integer its schema has always said. */
    given Schema[ProbeAnswer] = com.histmersion.api.json.Schema.derived[ProbeAnswer].modify(_.year)(_.format("int32"))
    type RecordYear = RecordYear.T
    object RecordYear extends Newtype[Int, Pure]

  final case class ProbeResult(
      model: ProbeResult.Model,
      answer: Option[ProbeAnswer],
      rawAnswer: ProbeResult.RawAnswer,
      matchesRecord: ProbeResult.MatchesRecord,
      toolCalls: List[ProbeResult.ToolCalls],
      iterations: ProbeResult.Iterations,
      finish: ProbeResult.Finish,
      usage: Usage,
      costUsd: Option[ProbeResult.CostUsd],
      millis: ProbeResult.Millis,
      problems: List[ProbeResult.Problems] = Nil,
      attempts: ProbeResult.Attempts = ProbeResult.Attempts(1)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ProbeResult:
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type RawAnswer = RawAnswer.T
    object RawAnswer extends Newtype[String, Pure]
    type MatchesRecord = MatchesRecord.T
    object MatchesRecord extends Newtype[Boolean, Pure]
    type ToolCalls = ToolCalls.T
    object ToolCalls extends Newtype[String, Pure]
    type Iterations = Iterations.T
    object Iterations extends Newtype[Int, Pure]
    type Finish = Finish.T
    object Finish extends Newtype[String, Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends Newtype[Double, Pure]
    type Millis = Millis.T
    object Millis extends Newtype[Long, Pure]
    type Problems = Problems.T
    object Problems extends Newtype[String, Pure]
    type Attempts = Attempts.T
    object Attempts extends Newtype[Int, Pure]

  final case class RunRequest(
      pipeline: RunRequest.Pipeline,
      target: RunRequest.Target,
      model: Option[RunRequest.Model] = None,
      budgetUsd: Option[RunRequest.BudgetUsd] = None,
      budgetTokens: Option[RunRequest.BudgetTokens] = None,
      fix: Option[RunRequest.Fix] = None,
      rounds: Option[RunRequest.Rounds] = None,
      acquireItem: Option[Queue.QueueItem.Id] = None,
      title: Option[RunRequest.Title] = None,
      kind: Option[RunRequest.Kind] = None,
      query: Option[RunRequest.Query] = None,
      topics: List[RunRequest.Topics] = Nil,
      tags: List[RunRequest.Tags] = Nil,
      /** Every field is optional: a run stored before an option existed must still decode. */
      goal: Option[Goal.Slug] = None,
      dryRun: Option[RunRequest.DryRun] = None,
      modelFor: List[ModelOverride] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object RunRequest:
    type Pipeline = Pipeline.T
    object Pipeline extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends Newtype[Double, Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends Newtype[Long, Pure]
    type Fix = Fix.T
    object Fix extends Newtype[Boolean, Pure]
    type Rounds = Rounds.T
    object Rounds extends Newtype[Int, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Query = Query.T
    object Query extends Newtype[String, Pure]
    type Topics = Topics.T
    object Topics extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]

  final case class ModelOverride(role: ModelOverride.Role, model: ModelOverride.Model)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def text: ModelOverride.Wire = ModelOverride.Wire(s"$role=$model")
  object ModelOverride:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    export AgentRole.Wire as Role
    type Model = Model.T
    object Model extends Newtype[String, Pure]

    def parse(s: Wire): Either[Problem, ModelOverride] =
      (s).value.indexOf('=') match
        case -1 => Left(Problem(s"'${(s).value.trim}' is not <role>=<provider:model>"))
        case i  =>
          val (r, m) =
            ((s).value.substring(0, i).trim.toLowerCase(java.util.Locale.ROOT), (s).value.substring(i + 1).trim)
          if r.isEmpty then Left(Problem(s"'${(s).value.trim}' names no role"))
          else if m.isEmpty then Left(Problem(s"'${(s).value.trim}' names no model"))
          else Right(ModelOverride(AgentRole.Wire(r), Model(m)))

  final case class RunParams(
      fix: Option[RunRequest.Fix] = None,
      rounds: Option[RunRequest.Rounds] = None,
      title: Option[RunRequest.Title] = None,
      kind: Option[RunRequest.Kind] = None,
      query: Option[RunRequest.Query] = None,
      topics: Option[List[RunRequest.Topics]] = None,
      tags: Option[List[RunRequest.Tags]] = None,
      modelFor: Option[List[ModelOverride]] = None,
      notes: Option[List[RunParams.Notes]] = None,
      chainReview: Option[RunParams.ChainReview] = None,
      minSources: Option[RunParams.MinSources] = None,
      maxArticles: Option[RunParams.MaxArticles] = None,
      autoIngest: Option[RunParams.AutoIngest] = None,
      hint: Option[RunParams.Hint] = None,
      subGoals: Option[RunParams.SubGoals] = None,
      oaBacklog: Option[RunParams.OaBacklog] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def fixes: Boolean = (fix.contains(RunRequest.Fix(true)))
    def noteList: List[RunParams.Notes] = (notes
      .getOrElse(Nil)
      .map((_).value.trim)
      .filter(_.nonEmpty))
      .map(refactorValue4 => RunParams.Notes(refactorValue4))
    def overrides: List[ModelOverride] = modelFor.getOrElse(Nil)

  object RunParams:

    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type ChainReview = ChainReview.T
    object ChainReview extends Newtype[Boolean, Pure]
    type MinSources = MinSources.T
    object MinSources extends Newtype[Int, Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends Newtype[Int, Pure]
    type AutoIngest = AutoIngest.T
    object AutoIngest extends Newtype[Int, Pure]
    type Hint = Hint.T
    object Hint extends Newtype[Long, Pure]
    type SubGoals = SubGoals.T
    object SubGoals extends Newtype[Int, Pure]
    type OaBacklog = OaBacklog.T
    object OaBacklog extends Newtype[Boolean, Pure]

  final case class RunInfo(
      id: RunInfo.Id,
      pipeline: RunInfo.Pipeline,
      target: RunInfo.Target,
      status: RunInfo.Status,
      progress: Option[RunInfo.Progress] = None,
      error: Option[Problem] = None,
      model: Option[RunInfo.Model] = None,
      usage: Usage = Usage.Zero,
      costUsd: RunInfo.CostUsd = RunInfo.CostUsd(0.0),
      budgetUsd: RunInfo.BudgetUsd = RunInfo.BudgetUsd(0.0),
      budgetTokens: RunInfo.BudgetTokens = RunInfo.BudgetTokens((0).toLong),
      warnings: List[RunInfo.Warnings] = Nil,
      createdAt: RunInfo.CreatedAt = RunInfo.CreatedAt(""),
      updatedAt: RunInfo.UpdatedAt = RunInfo.UpdatedAt(""),
      targetKind: RunInfo.TargetKind = RunInfo.TargetKind(""),
      params: RunParams = RunParams(),
      acquireItem: Option[Queue.QueueItem.Id] = None,
      goal: Option[Goal.Slug] = None,
      parent: Option[RunInfo.Id] = None,
      origin: Option[RunInfo.Origin] = None,
      resumeAt: Option[RunInfo.ResumeAt] = None,
      dryRun: RunInfo.DryRun = RunInfo.DryRun(false),
      managerId: Option[Managers.ManagerInfo.Id] = None,
      managerItem: Option[Managers.ManagerItem.Id] = None,
      heartbeatAt: Option[RunInfo.HeartbeatAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema,
        CanEqual:
    def isWaiting: Boolean = (status == Run.State.WaitingApproval.wire)

    def modelFor(role: Role): Option[RunInfo.Model] =
      params.overrides.find(o => Role.parse(o.role).contains(role)).map(o => RunInfo.Model(o.model.value)).orElse(model)

  object RunInfo:

    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Pipeline = Pipeline.T
    object Pipeline extends Newtype[String, Pure]
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    export Run.State.Wire as Status
    type Progress = Progress.T
    object Progress extends Newtype[String, Pure]
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends Newtype[Double, Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends Newtype[Double, Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends Newtype[Long, Pure]
    type Warnings = Warnings.T
    object Warnings extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]
    type TargetKind = TargetKind.T
    object TargetKind extends Newtype[String, Pure]
    export Agents.Origin.Wire as Origin
    type ResumeAt = ResumeAt.T
    object ResumeAt extends Newtype[String, Pure]
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    type HeartbeatAt = HeartbeatAt.T
    object HeartbeatAt extends Newtype[String, Pure]

  final case class WouldWrite(what: WouldWrite.What, payload: WouldWrite.Payload)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object WouldWrite:
    type What = What.T
    object What extends Newtype[String, Pure]
    type Payload = Payload.T
    object Payload extends Newtype[String, Pure]

  final case class StepInfo(
      seq: StepInfo.Seq,
      name: StepInfo.Name,
      kind: StepInfo.Kind,
      attempt: StepInfo.Attempt,
      status: StepInfo.Status,
      model: Option[StepInfo.Model] = None,
      promptHash: Option[StepInfo.PromptHash] = None,
      usage: Usage = Usage.Zero,
      costUsd: StepInfo.CostUsd = StepInfo.CostUsd(0.0),
      error: Option[Problem] = None,
      output: Option[StepInfo.Output] = None,
      startedAt: StepInfo.StartedAt = StepInfo.StartedAt(""),
      finishedAt: StepInfo.FinishedAt = StepInfo.FinishedAt(""),
      actualModel: Option[StepInfo.ActualModel] = None,
      wouldWrite: List[WouldWrite] = Nil,
      firstCallTokens: Option[StepInfo.FirstCallTokens] = None,
      turns: Option[StepInfo.Turns] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def modelText: String = (model, actualModel) match
      case (Some(m), Some(a)) if StepInfo.ActualModel((m).value.drop((m).value.indexOf(':') + 1)) != a => (s"$m → $a")
      case (Some(m), _)                                                                                => ((m).value)
      case (None, Some(a))                                                                             => ((a).value)
      case (None, None)                                                                                => ("")

  object StepInfo:

    type Seq = Seq.T
    object Seq extends Newtype[Int, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Attempt = Attempt.T
    object Attempt extends Newtype[Int, Pure]
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type PromptHash = PromptHash.T
    object PromptHash extends Newtype[String, Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends Newtype[Double, Pure]
    type Output = Output.T
    object Output extends Newtype[String, Pure]
    type StartedAt = StartedAt.T
    object StartedAt extends Newtype[String, Pure]
    type FinishedAt = FinishedAt.T
    object FinishedAt extends Newtype[String, Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends Newtype[String, Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends Newtype[Long, Pure]
    type Turns = Turns.T
    object Turns extends Newtype[Int, Pure]

  final case class MessageInfo(
      seq: StepInfo.Seq,
      n: MessageInfo.N,
      role: MessageInfo.Role,
      content: Option[MessageInfo.Content] = None,
      toolName: Option[MessageInfo.ToolName] = None,
      toolInput: Option[MessageInfo.ToolInput] = None,
      toolResult: Option[MessageInfo.ToolOutput] = None,
      latencyMs: Option[MessageInfo.LatencyMs] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object MessageInfo:
    type N = N.T
    object N extends Newtype[Int, Pure]
    type Role = Role.T
    object Role extends Newtype[String, Pure]
    type Content = Content.T
    object Content extends Newtype[String, Pure]
    type ToolName = ToolName.T
    object ToolName extends Newtype[String, Pure]
    type ToolInput = ToolInput.T
    object ToolInput extends Newtype[String, Pure]
    type ToolOutput = ToolOutput.T
    object ToolOutput extends Newtype[String, Pure]
    type LatencyMs = LatencyMs.T
    object LatencyMs extends Newtype[Int, Pure]

  final case class RunDetail(run: RunInfo, steps: List[StepInfo], messages: List[MessageInfo])
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema,
        CanEqual

  final case class RoleModel(
      role: RoleModel.Role,
      model: Option[RoleModel.Model],
      ready: RoleModel.Ready,
      problem: Option[Problem]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object RoleModel:
    export AgentRole.Wire as Role
    type Model = Model.T
    object Model extends Newtype[String, Pure]
    type Ready = Ready.T
    object Ready extends Newtype[Boolean, Pure]

  final case class ModelsInfo(
      enabled: ModelsInfo.Enabled,
      roles: List[RoleModel],
      pipelines: List[ModelsInfo.Pipelines],
      maxConcurrentRuns: ModelsInfo.MaxConcurrentRuns,
      problems: List[ModelsInfo.Problems],
      chains: List[ModelChains.RoleChain] = Nil,
      allowances: List[ModelChains.AllowanceStatus] = Nil,
      now: ModelsInfo.Now = ModelsInfo.Now("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ModelsInfo:
    type Enabled = Enabled.T
    object Enabled extends Newtype[Boolean, Pure]
    type Pipelines = Pipelines.T
    object Pipelines extends Newtype[String, Pure]
    type MaxConcurrentRuns = MaxConcurrentRuns.T
    object MaxConcurrentRuns extends Newtype[Int, Pure]
    type Problems = Problems.T
    object Problems extends Newtype[String, Pure]
    type Now = Now.T
    object Now extends Newtype[String, Pure]

  enum CheckLevel(val wire: CheckLevel.Wire) derives CanEqual:
    case Ok extends CheckLevel(CheckLevel.Wire("ok"))
    case Warn extends CheckLevel(CheckLevel.Wire("warn"))
    case Fail extends CheckLevel(CheckLevel.Wire("fail"))

  object CheckLevel:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[CheckLevel] = values.find(_.wire == s)
    given WireEnumValues[CheckLevel]:
      def all: Array[CheckLevel] = values
      def wire(e: CheckLevel): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[CheckLevel] = parse(Wire((s).value))
    given io.circe.Encoder[CheckLevel] = WireEnum.encoder
    given io.circe.Decoder[CheckLevel] = WireEnum.decoder
    given Schema[CheckLevel] = WireEnum.schema

  final case class DoctorCheck(name: DoctorCheck.Name, level: CheckLevel, detail: DoctorCheck.Detail)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object DoctorCheck:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]

  final case class ProviderDoctor(
      provider: ProviderDoctor.Provider,
      used: ProviderDoctor.Used,
      models: List[ProviderDoctor.Models],
      checks: List[DoctorCheck],
      upstreamModels: List[ProviderDoctor.UpstreamModels] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def level: CheckLevel =
      if checks.exists(_.level == CheckLevel.Fail) then CheckLevel.Fail
      else if checks.exists(_.level == CheckLevel.Warn) then CheckLevel.Warn
      else CheckLevel.Ok

  object ProviderDoctor:
    type Provider = Provider.T
    object Provider extends Newtype[String, Pure]
    type Used = Used.T
    object Used extends Newtype[Boolean, Pure]
    type Models = Models.T
    object Models extends Newtype[String, Pure]
    type UpstreamModels = UpstreamModels.T
    object UpstreamModels extends Newtype[String, Pure]

  final case class DoctorReport(
      enabled: DoctorReport.Enabled,
      online: DoctorReport.Online,
      providers: List[ProviderDoctor],
      checks: List[DoctorCheck]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object DoctorReport:
    type Enabled = Enabled.T
    object Enabled extends Newtype[Boolean, Pure]
    type Online = Online.T
    object Online extends Newtype[Boolean, Pure]

  final case class GenerateRequest(
      runtimes: List[GenerateRequest.Runtimes] = Nil,
      out: Option[GenerateRequest.Out] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object GenerateRequest:
    type Runtimes = Runtimes.T
    object Runtimes extends Newtype[String, Pure]
    type Out = Out.T
    object Out extends Newtype[String, Pure]

  final case class GenerateResult(
      dir: GenerateResult.Dir,
      runtimes: List[GenerateResult.Runtimes],
      files: GenerateResult.Files,
      written: GenerateResult.Written,
      removed: GenerateResult.Removed,
      definitions: GenerateResult.Definitions
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object GenerateResult:
    type Dir = Dir.T
    object Dir extends Newtype[String, Pure]
    type Runtimes = Runtimes.T
    object Runtimes extends Newtype[String, Pure]
    type Files = Files.T
    object Files extends Newtype[Int, Pure]
    type Written = Written.T
    object Written extends Newtype[Int, Pure]
    type Removed = Removed.T
    object Removed extends Newtype[Int, Pure]
    type Definitions = Definitions.T
    object Definitions extends Newtype[String, Pure]

  enum Decision(val wire: Decision.Wire) derives CanEqual:
    case Approve extends Decision(Decision.Wire("approve"))
    case Reject extends Decision(Decision.Wire("reject"))

  object Decision:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Decision] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  final case class ApprovalInfo(
      id: ApprovalInfo.Id,
      runId: Option[RunInfo.Id],
      kind: ApprovalInfo.Kind,
      citekey: Citekey,
      status: Option[ApprovalInfo.Status] = None,
      current: Option[ApprovalInfo.Current] = None,
      edgeKind: Option[ApprovalInfo.EdgeKind] = None,
      target: Option[ApprovalInfo.Target] = None,
      evidence: ApprovalInfo.Evidence = ApprovalInfo.Evidence(""),
      quote: Option[ApprovalInfo.Quote] = None,
      state: ApprovalInfo.State = Approval.State.Open.wire,
      note: Option[ApprovalInfo.Note] = None,
      result: Option[ApprovalInfo.Result] = None,
      createdAt: ApprovalInfo.CreatedAt = ApprovalInfo.CreatedAt(""),
      decidedAt: Option[ApprovalInfo.DecidedAt] = None,
      subject: Option[ApprovalInfo.Subject] = None,
      plan: Option[Goal.Plans.Plan] = None,
      article: Option[Goal.Plans.ArticleDraft] = None,
      filter: Option[Article.Plans.FilterProposal] = None,
      articles: Option[Article.Plans.ArticlePlan] = None,
      hintEffect: Option[Managers.HintProposal] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema,
        CanEqual:
    def approvalKind: Option[Approval.Kind] = Approval.Kind.parse(kind)

    /** The approval with its payload read as the article proposal its kind carries (an article's coverage filter, a
      * plan of articles), its rationale as the evidence; None for a kind that carries none.
      */
    def withArticleProposal(payload: Option[io.circe.Json]): Option[ApprovalInfo] =
      if approvalKind.exists((refactorCallback => ((refactorCallback.proposesFilter) == (true)))) then
        val f = payload.flatMap(_.as[Article.Plans.FilterProposal].toOption)
        Some(
          this
            .modify(_.filter)
            .setTo(f)
            .modify(_.evidence)
            .setTo(ApprovalInfo.Evidence(f.map(_.rationale.value).getOrElse("")))
        )
      else if approvalKind.exists((refactorCallback => ((refactorCallback.proposesArticles) == (true)))) then
        val p = payload.flatMap(_.as[Article.Plans.ArticlePlan].toOption)
        Some(
          this
            .modify(_.articles)
            .setTo(p)
            .modify(_.evidence)
            .setTo(ApprovalInfo.Evidence(p.map(_.rationale.value).getOrElse("")))
        )
      else None

  object ApprovalInfo:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    export Approval.Kind.Wire as Kind
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type Current = Current.T
    object Current extends Newtype[String, Pure]
    export com.histmersion.api.library.Edge.Kind.Wire as EdgeKind
    type Target = Target.T
    object Target extends Newtype[String, Pure]
    type Evidence = Evidence.T
    object Evidence extends Newtype[String, Pure]
    type Quote = Quote.T
    object Quote extends Newtype[String, Pure]
    export Approval.State.Wire as State
    export Approval.Note
    type Result = Result.T
    object Result extends Newtype[String, Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]
    type DecidedAt = DecidedAt.T
    object DecidedAt extends Newtype[String, Pure]
    type Subject = Subject.T
    object Subject extends Newtype[String, Pure]

  final case class DecisionRequest(note: Option[DecisionRequest.Note] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object DecisionRequest:
    export Approval.Note

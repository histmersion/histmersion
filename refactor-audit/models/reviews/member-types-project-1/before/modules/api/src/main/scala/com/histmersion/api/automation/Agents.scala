package com.histmersion.api.automation
import hearth.kindlings.optics.syntax.*

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import com.histmersion.api.research.Article

object Agents:

  export com.histmersion.api.automation.Approval.Kind as ApprovalKind
  export com.histmersion.api.automation.Approval.Mode as ApprovalMode
  export com.histmersion.api.automation.Approval.State as ApprovalState
  export com.histmersion.api.automation.Run.State as RunState
  export com.histmersion.api.automation.Step.Status as StepStatus
  export com.histmersion.api.automation.{AgentRole as Role, Pipeline, ProviderKind, TargetKind}

  /** The model id may itself contain colons (`local:qwen2.5-coder:32b`), so only the first colon separates the
    * provider.
    */
  final case class ModelRef(provider: ProviderKind, model: ModelRef.Model):
    def wire: String = s"${provider.wire}:$model"

  object ModelRef:
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: String, known: Set[String] = Set.empty): Either[String, ModelRef] =
      val t = s.trim
      t.indexOf(':') match
        case -1 => Left(s"'$t' is not provider:model (provider: ${ProviderKind.names(known)})")
        case i  =>
          val (p, m) = (t.substring(0, i), t.substring(i + 1).trim)
          ProviderKind.parse(p, known) match
            case None                 => Left(s"unknown provider '$p' in '$t' (one of: ${ProviderKind.names(known)})")
            case Some(_) if m.isEmpty => Left(s"'$t' names no model")
            case Some(k)              => Right(ModelRef(k, m))

  /** `input` counts all prompt tokens, cached ones included (the provider-normalised convention sttp-ai uses);
    * `cacheRead`/`cacheWrite` are subsets of it.
    */
  final case class Usage(input: Usage.Input = 0, output: Usage.Output = 0, cacheRead: Usage.CacheRead = 0, cacheWrite: Usage.CacheWrite = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def +(o: Usage): Usage =
      Usage(input + o.input, output + o.output, cacheRead + o.cacheRead, cacheWrite + o.cacheWrite)
    def total: Long = input + output

    def budgeted(cacheReadWeight: Double): Long =
      math.max(0L, input - cacheRead) + output + math.round(cacheRead.toDouble * math.max(0.0, cacheReadWeight))

  object Usage:
    type Input = Input.T
    object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CacheRead = CacheRead.T
    object CacheRead extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CacheWrite = CacheWrite.T
    object CacheWrite extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Zero: Usage = Usage()


  final case class Price(
      input: BigDecimal,
      output: BigDecimal,
      cacheRead: Option[BigDecimal] = None,
      cacheWrite: Option[BigDecimal] = None
  ):
    def cost(u: Usage): BigDecimal =
      val plain = math.max(u.input - u.cacheRead - u.cacheWrite, 0L)
      (BigDecimal(plain) * input + BigDecimal(u.cacheRead) * cacheRead.getOrElse(input) +
        BigDecimal(u.cacheWrite) * cacheWrite.getOrElse(input) + BigDecimal(u.output) * output) / Prices.Million

    def text: String =
      val n = (d: BigDecimal) => d.bigDecimal.stripTrailingZeros.toPlainString
      (List(input, output) ++ cacheRead.zip(cacheWrite).toList.flatMap((r, w) => List(r, w))).map(n).mkString("/")

  enum Billing:
    case Plan(pool: PlanQuota.PoolId)
    case Metered(price: Price)

    def cost(u: Usage): BigDecimal = this match
      case Plan(_)    => BigDecimal(0)
      case Metered(p) => p.cost(u)

    def describe: String = this match
      case Plan(p)    => s"plan pool: ${p.wire}"
      case Metered(p) => s"price: ${p.text} USD per million tokens"

  object Prices:
    private[Agents] val Million = BigDecimal(1000000)

    def parse(s: String): Either[String, Map[String, Price]] =
      val entries = s.split(";").toList.map(_.trim).filter(_.nonEmpty)
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
                case List(in, out)         => Right(model -> Price(in, out))
                case List(in, out, cr, cw) => Right(model -> Price(in, out, Some(cr), Some(cw)))
                case _ => Left(s"'$e': give 2 prices (input/output) or 4 (input/output/cache-read/cache-write)")
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(parsed.collect { case Right(kv) => kv }.toMap)
        case errs => Left(errs.mkString("; "))

    /** An unpriced model is an error, never zero: sttp-ai's `PriceTable` counts an unknown model as free, so it would
      * never trip a cost budget.
      */
    def cost(prices: Map[String, Price], model: String, u: Usage): Either[String, BigDecimal] =
      prices.get(model).toRight(unpriced(model)).map(_.cost(u))

    def billing(prices: Map[String, Price], m: ModelRef, pool: Option[PlanQuota.PoolId]): Either[String, Billing] =
      pool match
        case Some(p) => Right(Billing.Plan(p))
        case None    => prices.get(m.model).map(Billing.Metered(_)).toRight(unpriced(m.model) + metered(m))

    private def unpriced(model: String): String =
      s"model '$model' has no price — add it to agents.prices (HISTMERSION_AGENTS_PRICES), e.g. \"$model=0/0\" for a free local model"

    private def metered(m: ModelRef): String = m.provider match
      case ProviderKind.ClaudeCode => " (claude-code is set to use an API key, so it bills the API, not the plan)"
      case ProviderKind.Codex      => " (codex is set to use an API key, so it bills the API, not the plan)"
      case ProviderKind.Opencode   => " (an opencode model is metered unless it is openai/… on a ChatGPT login)"
      case _                       => ""

  val JobKind = com.histmersion.api.automation.AgentJobKinds.Agent.kind.value

  enum PauseReason(val wire: String):
    case Budget extends PauseReason("budget")
    case Interrupted extends PauseReason("interrupted")
    case Requested extends PauseReason("requested")
    case Provider extends PauseReason("provider")

    case Allowance extends PauseReason("allowance")

    case PlanQuota extends PauseReason("plan-quota")

    case Stalled extends PauseReason("stalled")

  enum StepKind(val wire: String):
    case Code extends StepKind("code")
    case Llm extends StepKind("llm")

  object StepKind:
    def parse(s: String): Option[StepKind] = values.find(_.wire == s)

  enum Origin(val wire: String):
    case Person extends Origin("person")
    case Scheduler extends Origin("scheduler")
    case Run extends Origin("run")

    /** A run an ingest started, by a name no other type takes. */
    case FromIngest extends Origin("ingest")

    case Manager extends Origin("manager")

    def unattended: Boolean = this != Person

  object Origin:
    def parse(s: String): Option[Origin] = values.find(_.wire == s.trim.toLowerCase)

  final case class ProbeRequest(citekey: com.histmersion.api.library.Citekey, model: Option[ProbeRequest.Model] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ProbeRequest:
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ProbeAnswer(citekey: com.histmersion.api.library.Citekey, title: com.histmersion.api.library.Title, year: Option[com.histmersion.api.catalogue.Year])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    /** A strict-schema provider sends `""` and `null` where it has nothing; they must not pass. */
    def problems(recordYear: Option[Int]): List[String] = List(
      Option.when(Option(citekey).forall(_.trim.isEmpty))("the citekey is empty — give the source's citekey"),
      Option.when(Option(title).forall(_.trim.isEmpty))(
        "the title is empty — copy the source's exact title from get_source"
      ),
      Option.when(year.isEmpty && recordYear.isDefined)(
        "the year is missing — give the source's publication year from get_source"
      )
    ).flatten


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
      attempts: ProbeResult.Attempts = 1
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ProbeResult:
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RawAnswer = RawAnswer.T
    object RawAnswer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MatchesRecord = MatchesRecord.T
    object MatchesRecord extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolCalls = ToolCalls.T
    object ToolCalls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Iterations = Iterations.T
    object Iterations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Finish = Finish.T
    object Finish extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Millis = Millis.T
    object Millis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempts = Attempts.T
    object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RunRequest(
      pipeline: RunRequest.Pipeline,
      target: RunRequest.Target,
      model: Option[RunRequest.Model] = None,
      budgetUsd: Option[RunRequest.BudgetUsd] = None,
      budgetTokens: Option[RunRequest.BudgetTokens] = None,
      fix: Option[RunRequest.Fix] = None,
      rounds: Option[RunRequest.Rounds] = None,
      acquireItem: Option[com.histmersion.api.automation.Queue.QueueItem.Id] = None,
      title: Option[RunRequest.Title] = None,
      kind: Option[RunRequest.Kind] = None,
      query: Option[RunRequest.Query] = None,
      topics: List[RunRequest.Topics] = Nil,
      tags: List[RunRequest.Tags] = Nil,
      /** Every field is optional: a run stored before an option existed must still decode. */
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      dryRun: Option[RunRequest.DryRun] = None,
      modelFor: List[ModelOverride] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RunRequest:
    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fix = Fix.T
    object Fix extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rounds = Rounds.T
    object Rounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ModelOverride(role: ModelOverride.Role, model: ModelOverride.Model) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema:
    def text: String = s"$role=$model"
  object ModelOverride:


    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: String): Either[String, ModelOverride] =
      s.indexOf('=') match
        case -1 => Left(s"'${s.trim}' is not <role>=<provider:model>")
        case i  =>
          val (r, m) = (s.substring(0, i).trim.toLowerCase(java.util.Locale.ROOT), s.substring(i + 1).trim)
          if r.isEmpty then Left(s"'${s.trim}' names no role")
          else if m.isEmpty then Left(s"'${s.trim}' names no model")
          else Right(ModelOverride(r, m))

  final case class RunParams(
      fix: Option[com.histmersion.api.automation.Agents.RunRequest.Fix] = None,
      rounds: Option[com.histmersion.api.automation.Agents.RunRequest.Rounds] = None,
      title: Option[com.histmersion.api.automation.Agents.RunRequest.Title] = None,
      kind: Option[com.histmersion.api.automation.Agents.RunRequest.Kind] = None,
      query: Option[com.histmersion.api.automation.Agents.RunRequest.Query] = None,
      topics: Option[List[com.histmersion.api.automation.Agents.RunRequest.Topics]] = None,
      tags: Option[List[com.histmersion.api.automation.Agents.RunRequest.Tags]] = None,
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
        KindlingsSchema, com.histmersion.api.json.Schema:
    def fixes: Boolean = fix.contains(true)
    def noteList: List[String] = notes.getOrElse(Nil).map(_.trim).filter(_.nonEmpty)
    def overrides: List[ModelOverride] = modelFor.getOrElse(Nil)

  object RunParams:
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChainReview = ChainReview.T
    object ChainReview extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinSources = MinSources.T
    object MinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxArticles = MaxArticles.T
    object MaxArticles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AutoIngest = AutoIngest.T
    object AutoIngest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Hint = Hint.T
    object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SubGoals = SubGoals.T
    object SubGoals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OaBacklog = OaBacklog.T
    object OaBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RunInfo(
      id: RunInfo.Id,
      pipeline: RunInfo.Pipeline,
      target: RunInfo.Target,
      status: RunInfo.Status,
      progress: Option[RunInfo.Progress] = None,
      error: Option[RunInfo.Error] = None,
      model: Option[RunInfo.Model] = None,
      usage: Usage = Usage.Zero,
      costUsd: RunInfo.CostUsd = 0,
      budgetUsd: RunInfo.BudgetUsd = 0,
      budgetTokens: RunInfo.BudgetTokens = 0,
      warnings: List[RunInfo.Warnings] = Nil,
      createdAt: RunInfo.CreatedAt = "",
      updatedAt: RunInfo.UpdatedAt = "",
      targetKind: RunInfo.TargetKind = "",
      params: RunParams = RunParams(),
      acquireItem: Option[com.histmersion.api.automation.Queue.QueueItem.Id] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      parent: Option[com.histmersion.api.automation.Agents.RunInfo.Id] = None,
      origin: Option[RunInfo.Origin] = None,
      resumeAt: Option[RunInfo.ResumeAt] = None,
      dryRun: RunInfo.DryRun = false,
      managerId: Option[com.histmersion.api.automation.Managers.ManagerInfo.Id] = None,
      managerItem: Option[com.histmersion.api.automation.Managers.ManagerItem.Id] = None,
      heartbeatAt: Option[RunInfo.HeartbeatAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def isWaiting: Boolean = status == com.histmersion.api.automation.Run.State.WaitingApproval.wire

    def modelFor(role: Role): Option[String] =
      params.overrides.find(o => Role.parse(o.role).contains(role)).map(_.model).orElse(model)

  object RunInfo:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Progress = Progress.T
    object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetTokens = BudgetTokens.T
    object BudgetTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Warnings = Warnings.T
    object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetKind = TargetKind.T
    object TargetKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Origin = Origin.T
    object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResumeAt = ResumeAt.T
    object ResumeAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HeartbeatAt = HeartbeatAt.T
    object HeartbeatAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class WouldWrite(what: WouldWrite.What, payload: WouldWrite.Payload) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object WouldWrite:
    type What = What.T
    object What extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Payload = Payload.T
    object Payload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class StepInfo(
      seq: StepInfo.Seq,
      name: StepInfo.Name,
      kind: StepInfo.Kind,
      attempt: StepInfo.Attempt,
      status: StepInfo.Status,
      model: Option[StepInfo.Model] = None,
      promptHash: Option[StepInfo.PromptHash] = None,
      usage: Usage = Usage.Zero,
      costUsd: StepInfo.CostUsd = 0,
      error: Option[StepInfo.Error] = None,
      output: Option[StepInfo.Output] = None,
      startedAt: StepInfo.StartedAt = "",
      finishedAt: StepInfo.FinishedAt = "",
      actualModel: Option[StepInfo.ActualModel] = None,
      wouldWrite: List[WouldWrite] = Nil,
      firstCallTokens: Option[StepInfo.FirstCallTokens] = None,
      turns: Option[StepInfo.Turns] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def modelText: String = (model, actualModel) match
      case (Some(m), Some(a)) if m.drop(m.indexOf(':') + 1) != a => s"$m → $a"
      case (Some(m), _)                                          => m
      case (None, Some(a))                                       => a
      case (None, None)                                          => ""

  object StepInfo:
    type Seq = Seq.T
    object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempt = Attempt.T
    object Attempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PromptHash = PromptHash.T
    object PromptHash extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CostUsd = CostUsd.T
    object CostUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Output = Output.T
    object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StartedAt = StartedAt.T
    object StartedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FinishedAt = FinishedAt.T
    object FinishedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ActualModel = ActualModel.T
    object ActualModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstCallTokens = FirstCallTokens.T
    object FirstCallTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Turns = Turns.T
    object Turns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class MessageInfo(
      seq: MessageInfo.Seq,
      n: MessageInfo.N,
      role: MessageInfo.Role,
      content: Option[MessageInfo.Content] = None,
      toolName: Option[MessageInfo.ToolName] = None,
      toolInput: Option[MessageInfo.ToolInput] = None,
      toolResult: Option[MessageInfo.ToolResult] = None,
      latencyMs: Option[MessageInfo.LatencyMs] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object MessageInfo:
    type Seq = Seq.T
    object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type N = N.T
    object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Content = Content.T
    object Content extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolName = ToolName.T
    object ToolName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolInput = ToolInput.T
    object ToolInput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ToolResult = ToolResult.T
    object ToolResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatencyMs = LatencyMs.T
    object LatencyMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RunDetail(run: RunInfo, steps: List[StepInfo], messages: List[MessageInfo])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class RoleModel(role: RoleModel.Role, model: Option[RoleModel.Model], ready: RoleModel.Ready, problem: Option[RoleModel.Problem])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RoleModel:
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ready = Ready.T
    object Ready extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ModelsInfo(
      enabled: ModelsInfo.Enabled,
      roles: List[RoleModel],
      pipelines: List[ModelsInfo.Pipelines],
      maxConcurrentRuns: ModelsInfo.MaxConcurrentRuns,
      problems: List[ModelsInfo.Problems],
      chains: List[ModelChains.RoleChain] = Nil,
      allowances: List[ModelChains.AllowanceStatus] = Nil,
      now: ModelsInfo.Now = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ModelsInfo:
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pipelines = Pipelines.T
    object Pipelines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxConcurrentRuns = MaxConcurrentRuns.T
    object MaxConcurrentRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Now = Now.T
    object Now extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum CheckLevel(val wire: String):
    case Ok extends CheckLevel("ok")
    case Warn extends CheckLevel("warn")
    case Fail extends CheckLevel("fail")

  object CheckLevel:
    def parse(s: String): Option[CheckLevel] = values.find(_.wire == s)
    given WireEnumValues[CheckLevel]:
      def all: Array[CheckLevel] = CheckLevel.values
      def wire(e: CheckLevel): String = e.wire
      def fromWire(s: String): Option[CheckLevel] = parse(s)
    given io.circe.Encoder[CheckLevel] = WireEnum.encoder
    given io.circe.Decoder[CheckLevel] = WireEnum.decoder
    given Schema[CheckLevel] = WireEnum.schema

  final case class DoctorCheck(name: DoctorCheck.Name, level: CheckLevel, detail: DoctorCheck.Detail)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object DoctorCheck:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ProviderDoctor(
      provider: ProviderDoctor.Provider,
      used: ProviderDoctor.Used,
      models: List[ProviderDoctor.Models],
      checks: List[DoctorCheck],
      upstreamModels: List[ProviderDoctor.UpstreamModels] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def level: CheckLevel =
      if checks.exists(_.level == CheckLevel.Fail) then CheckLevel.Fail
      else if checks.exists(_.level == CheckLevel.Warn) then CheckLevel.Warn
      else CheckLevel.Ok

  object ProviderDoctor:
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Used = Used.T
    object Used extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Models = Models.T
    object Models extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpstreamModels = UpstreamModels.T
    object UpstreamModels extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class DoctorReport(
      enabled: DoctorReport.Enabled,
      online: DoctorReport.Online,
      providers: List[ProviderDoctor],
      checks: List[DoctorCheck]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object DoctorReport:
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Online = Online.T
    object Online extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class GenerateRequest(runtimes: List[GenerateRequest.Runtimes] = Nil, out: Option[GenerateRequest.Out] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object GenerateRequest:
    type Runtimes = Runtimes.T
    object Runtimes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Out = Out.T
    object Out extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class GenerateResult(
      dir: GenerateResult.Dir,
      runtimes: List[GenerateResult.Runtimes],
      files: GenerateResult.Files,
      written: GenerateResult.Written,
      removed: GenerateResult.Removed,
      definitions: GenerateResult.Definitions
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object GenerateResult:
    type Dir = Dir.T
    object Dir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Runtimes = Runtimes.T
    object Runtimes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Files = Files.T
    object Files extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Written = Written.T
    object Written extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Removed = Removed.T
    object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Definitions = Definitions.T
    object Definitions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum Decision(val wire: String):
    case Approve extends Decision("approve")
    case Reject extends Decision("reject")

  object Decision:
    def parse(s: String): Option[Decision] = values.find(_.wire == s.trim.toLowerCase)

  final case class ApprovalInfo(
      id: ApprovalInfo.Id,
      runId: Option[com.histmersion.api.automation.Agents.RunInfo.Id],
      kind: ApprovalInfo.Kind,
      citekey: com.histmersion.api.library.Citekey,
      status: Option[ApprovalInfo.Status] = None,
      current: Option[ApprovalInfo.Current] = None,
      edgeKind: Option[ApprovalInfo.EdgeKind] = None,
      target: Option[ApprovalInfo.Target] = None,
      evidence: ApprovalInfo.Evidence = "",
      quote: Option[ApprovalInfo.Quote] = None,
      state: ApprovalInfo.State = Approval.State.Open.wire,
      note: Option[ApprovalInfo.Note] = None,
      result: Option[ApprovalInfo.Result] = None,
      createdAt: ApprovalInfo.CreatedAt = "",
      decidedAt: Option[ApprovalInfo.DecidedAt] = None,
      subject: Option[ApprovalInfo.Subject] = None,
      plan: Option[Goal.Plans.Plan] = None,
      article: Option[Goal.Plans.ArticleDraft] = None,
      filter: Option[Article.Plans.FilterProposal] = None,
      articles: Option[Article.Plans.ArticlePlan] = None,
      hintEffect: Option[Managers.HintProposal] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def approvalKind: Option[Approval.Kind] = Approval.Kind.parse(kind)

    /** The approval with its payload read as the article proposal its kind carries (an article's coverage filter, a
      * plan of articles), its rationale as the evidence; None for a kind that carries none.
      */
    def withArticleProposal(payload: Option[io.circe.Json]): Option[ApprovalInfo] =
      if approvalKind.exists(_.proposesFilter) then
        val f = payload.flatMap(_.as[Article.Plans.FilterProposal].toOption)
        Some(this.modify(_.filter).setTo(f).modify(_.evidence).setTo(f.map(_.rationale).getOrElse("")))
      else if approvalKind.exists(_.proposesArticles) then
        val p = payload.flatMap(_.as[Article.Plans.ArticlePlan].toOption)
        Some(this.modify(_.articles).setTo(p).modify(_.evidence).setTo(p.map(_.rationale).getOrElse("")))
      else None

  object ApprovalInfo:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Current = Current.T
    object Current extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgeKind = EdgeKind.T
    object EdgeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Evidence = Evidence.T
    object Evidence extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quote = Quote.T
    object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Result = Result.T
    object Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecidedAt = DecidedAt.T
    object DecidedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class DecisionRequest(note: Option[DecisionRequest.Note] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object DecisionRequest:
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.research.ArticlePlans
import com.histmersion.api.given

object Agents:

  export com.histmersion.values.{ApprovalKind, ApprovalMode, ApprovalState, RunState, StepStatus}
  export com.histmersion.domain.automation.{AgentRole as Role, Pipeline, ProviderKind, TargetKind}

  /** The model id may itself contain colons (`local:qwen2.5-coder:32b`), so only the first colon separates the
    * provider.
    */
  final case class ModelRef(provider: ProviderKind, model: String):
    def wire: String = s"${provider.wire}:$model"

  object ModelRef:
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
  final case class Usage(input: Long = 0, output: Long = 0, cacheRead: Long = 0, cacheWrite: Long = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def +(o: Usage): Usage =
      Usage(input + o.input, output + o.output, cacheRead + o.cacheRead, cacheWrite + o.cacheWrite)
    def total: Long = input + output

    def budgeted(cacheReadWeight: Double): Long =
      math.max(0L, input - cacheRead) + output + math.round(cacheRead.toDouble * math.max(0.0, cacheReadWeight))

  object Usage:
    val Zero: Usage = Usage()
    given Schema[Usage] = summon[KindlingsSchema[Usage]].schema

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

  final case class ProbeRequest(citekey: String, model: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object ProbeRequest:
    given Schema[ProbeRequest] = summon[KindlingsSchema[ProbeRequest]].schema

  final case class ProbeAnswer(citekey: String, title: String, year: Option[Int])
      derives KindlingsCodecAsObject,
        KindlingsSchema:
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
  object ProbeAnswer:
    given Schema[ProbeAnswer] = summon[KindlingsSchema[ProbeAnswer]].schema

  final case class ProbeResult(
      model: String,
      answer: Option[ProbeAnswer],
      rawAnswer: String,
      matchesRecord: Boolean,
      toolCalls: List[String],
      iterations: Int,
      finish: String,
      usage: Usage,
      costUsd: Option[Double],
      millis: Long,
      problems: List[String] = Nil,
      attempts: Int = 1
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ProbeResult:
    given Schema[ProbeResult] = summon[KindlingsSchema[ProbeResult]].schema

  final case class RunRequest(
      pipeline: String,
      target: String,
      model: Option[String] = None,
      budgetUsd: Option[Double] = None,
      budgetTokens: Option[Long] = None,
      fix: Option[Boolean] = None,
      rounds: Option[Int] = None,
      acquireItem: Option[Int] = None,
      title: Option[String] = None,
      kind: Option[String] = None,
      query: Option[String] = None,
      topics: List[String] = Nil,
      tags: List[String] = Nil,
      /** Every field is optional: a run stored before an option existed must still decode. */
      goal: Option[String] = None,
      dryRun: Option[Boolean] = None,
      modelFor: List[ModelOverride] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RunRequest:
    given Schema[RunRequest] = summon[KindlingsSchema[RunRequest]].schema

  final case class ModelOverride(role: String, model: String) derives KindlingsCodecAsObject, KindlingsSchema:
    def text: String = s"$role=$model"
  object ModelOverride:
    given Schema[ModelOverride] = summon[KindlingsSchema[ModelOverride]].schema

    def parse(s: String): Either[String, ModelOverride] =
      s.indexOf('=') match
        case -1 => Left(s"'${s.trim}' is not <role>=<provider:model>")
        case i  =>
          val (r, m) = (s.substring(0, i).trim.toLowerCase(java.util.Locale.ROOT), s.substring(i + 1).trim)
          if r.isEmpty then Left(s"'${s.trim}' names no role")
          else if m.isEmpty then Left(s"'${s.trim}' names no model")
          else Right(ModelOverride(r, m))

  final case class RunParams(
      fix: Option[Boolean] = None,
      rounds: Option[Int] = None,
      title: Option[String] = None,
      kind: Option[String] = None,
      query: Option[String] = None,
      topics: Option[List[String]] = None,
      tags: Option[List[String]] = None,
      modelFor: Option[List[ModelOverride]] = None,
      notes: Option[List[String]] = None,
      chainReview: Option[Boolean] = None,
      minSources: Option[Int] = None,
      maxArticles: Option[Int] = None,
      autoIngest: Option[Int] = None,
      hint: Option[Long] = None,
      subGoals: Option[Int] = None,
      oaBacklog: Option[Boolean] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def fixes: Boolean = fix.contains(true)
    def noteList: List[String] = notes.getOrElse(Nil).map(_.trim).filter(_.nonEmpty)
    def overrides: List[ModelOverride] = modelFor.getOrElse(Nil)
  object RunParams:
    given Schema[RunParams] = summon[KindlingsSchema[RunParams]].schema

  final case class RunInfo(
      id: String,
      pipeline: String,
      target: String,
      status: String,
      progress: Option[String] = None,
      error: Option[String] = None,
      model: Option[String] = None,
      usage: Usage = Usage.Zero,
      costUsd: Double = 0,
      budgetUsd: Double = 0,
      budgetTokens: Long = 0,
      warnings: List[String] = Nil,
      createdAt: String = "",
      updatedAt: String = "",
      targetKind: String = "",
      params: RunParams = RunParams(),
      acquireItem: Option[Int] = None,
      goal: Option[String] = None,
      parent: Option[String] = None,
      origin: Option[String] = None,
      resumeAt: Option[String] = None,
      dryRun: Boolean = false,
      managerId: Option[String] = None,
      managerItem: Option[Long] = None,
      heartbeatAt: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isWaiting: Boolean = status == RunState.WaitingApproval.wire

    def modelFor(role: Role): Option[String] =
      params.overrides.find(o => Role.parse(o.role).contains(role)).map(_.model).orElse(model)
  object RunInfo:
    given Schema[RunInfo] = summon[KindlingsSchema[RunInfo]].schema

  final case class WouldWrite(what: String, payload: String) derives KindlingsCodecAsObject, KindlingsSchema
  object WouldWrite:
    given Schema[WouldWrite] = summon[KindlingsSchema[WouldWrite]].schema

  final case class StepInfo(
      seq: Int,
      name: String,
      kind: String,
      attempt: Int,
      status: String,
      model: Option[String] = None,
      promptHash: Option[String] = None,
      usage: Usage = Usage.Zero,
      costUsd: Double = 0,
      error: Option[String] = None,
      output: Option[String] = None,
      startedAt: String = "",
      finishedAt: String = "",
      actualModel: Option[String] = None,
      wouldWrite: List[WouldWrite] = Nil,
      firstCallTokens: Option[Long] = None,
      turns: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def modelText: String = (model, actualModel) match
      case (Some(m), Some(a)) if m.drop(m.indexOf(':') + 1) != a => s"$m → $a"
      case (Some(m), _)                                          => m
      case (None, Some(a))                                       => a
      case (None, None)                                          => ""
  object StepInfo:
    given Schema[StepInfo] = summon[KindlingsSchema[StepInfo]].schema

  final case class MessageInfo(
      seq: Int,
      n: Int,
      role: String,
      content: Option[String] = None,
      toolName: Option[String] = None,
      toolInput: Option[String] = None,
      toolResult: Option[String] = None,
      latencyMs: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object MessageInfo:
    given Schema[MessageInfo] = summon[KindlingsSchema[MessageInfo]].schema

  final case class RunDetail(run: RunInfo, steps: List[StepInfo], messages: List[MessageInfo])
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object RunDetail:
    given Schema[RunDetail] = summon[KindlingsSchema[RunDetail]].schema

  final case class RoleModel(role: String, model: Option[String], ready: Boolean, problem: Option[String])
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object RoleModel:
    given Schema[RoleModel] = summon[KindlingsSchema[RoleModel]].schema

  final case class ModelsInfo(
      enabled: Boolean,
      roles: List[RoleModel],
      pipelines: List[String],
      maxConcurrentRuns: Int,
      problems: List[String],
      chains: List[ModelChains.RoleChain] = Nil,
      allowances: List[ModelChains.AllowanceStatus] = Nil,
      now: String = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ModelsInfo:
    given Schema[ModelsInfo] = summon[KindlingsSchema[ModelsInfo]].schema

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

  final case class DoctorCheck(name: String, level: CheckLevel, detail: String)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object DoctorCheck:
    given Schema[DoctorCheck] = summon[KindlingsSchema[DoctorCheck]].schema

  final case class ProviderDoctor(
      provider: String,
      used: Boolean,
      models: List[String],
      checks: List[DoctorCheck],
      upstreamModels: List[String] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def level: CheckLevel =
      if checks.exists(_.level == CheckLevel.Fail) then CheckLevel.Fail
      else if checks.exists(_.level == CheckLevel.Warn) then CheckLevel.Warn
      else CheckLevel.Ok
  object ProviderDoctor:
    given Schema[ProviderDoctor] = summon[KindlingsSchema[ProviderDoctor]].schema

  final case class DoctorReport(
      enabled: Boolean,
      online: Boolean,
      providers: List[ProviderDoctor],
      checks: List[DoctorCheck]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object DoctorReport:
    given Schema[DoctorReport] = summon[KindlingsSchema[DoctorReport]].schema

  final case class GenerateRequest(runtimes: List[String] = Nil, out: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object GenerateRequest:
    given Schema[GenerateRequest] = summon[KindlingsSchema[GenerateRequest]].schema

  final case class GenerateResult(
      dir: String,
      runtimes: List[String],
      files: Int,
      written: Int,
      removed: Int,
      definitions: String
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object GenerateResult:
    given Schema[GenerateResult] = summon[KindlingsSchema[GenerateResult]].schema

  enum Decision(val wire: String):
    case Approve extends Decision("approve")
    case Reject extends Decision("reject")

  object Decision:
    def parse(s: String): Option[Decision] = values.find(_.wire == s.trim.toLowerCase)

  final case class ApprovalInfo(
      id: Long,
      runId: Option[String],
      kind: String,
      citekey: String,
      status: Option[String] = None,
      current: Option[String] = None,
      edgeKind: Option[String] = None,
      target: Option[String] = None,
      evidence: String = "",
      quote: Option[String] = None,
      state: String = ApprovalState.Open.wire,
      note: Option[String] = None,
      result: Option[String] = None,
      createdAt: String = "",
      decidedAt: Option[String] = None,
      subject: Option[String] = None,
      plan: Option[GoalPlans.Plan] = None,
      article: Option[GoalPlans.ArticleDraft] = None,
      filter: Option[ArticlePlans.FilterProposal] = None,
      articles: Option[ArticlePlans.ArticlePlan] = None,
      hintEffect: Option[Managers.HintProposal] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def approvalKind: Option[ApprovalKind] = ApprovalKind.parse(kind)

    /** The approval with its payload read as the article proposal its kind carries (an article's coverage filter, a
      * plan of articles), its rationale as the evidence; None for a kind that carries none.
      */
    def withArticleProposal(payload: Option[io.circe.Json]): Option[ApprovalInfo] =
      if approvalKind.exists(_.proposesFilter) then
        val f = payload.flatMap(_.as[ArticlePlans.FilterProposal].toOption)
        Some(copy(filter = f, evidence = f.map(_.rationale).getOrElse("")))
      else if approvalKind.exists(_.proposesArticles) then
        val p = payload.flatMap(_.as[ArticlePlans.ArticlePlan].toOption)
        Some(copy(articles = p, evidence = p.map(_.rationale).getOrElse("")))
      else None
  object ApprovalInfo:
    given Schema[ApprovalInfo] = summon[KindlingsSchema[ApprovalInfo]].schema

  final case class DecisionRequest(note: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
  object DecisionRequest:
    given Schema[DecisionRequest] = summon[KindlingsSchema[DecisionRequest]].schema

package com.histmersion.plugins.ai

import com.histmersion.plugin.*

/** The Anthropic Messages API (`anthropic:<model>`) over the host's [[AiHttp]]: one call per turn, keyed by
  * `x-api-key`. Its failures read as they always have (`RuntimeException: Claude API error: …`), whole: the provider
  * asks for verbatim failures and for the loop's iteration notes, which the API is sent as user messages.
  */
final class AnthropicProvider(settings: ProviderSettings) extends AiProvider:
  def title: com.histmersion.plugin.AiProvider.TitleResult = com.histmersion.plugin.AiProvider.TitleResult("Anthropic API")

  override def capabilities: AiCapabilities =
    AiCapabilities(tools = _root_.com.histmersion.plugin.AiCapabilities.Tools(true), structuredOutput = _root_.com.histmersion.plugin.AiCapabilities.StructuredOutput(true), iterationMarkers = _root_.com.histmersion.plugin.AiCapabilities.IterationMarkers(true), verbatimFailures = _root_.com.histmersion.plugin.AiCapabilities.VerbatimFailures(true))

  private def base: Either[com.histmersion.plugins.ai.BaseUrl.Problem, BaseUrl] =
    BaseUrl.parse(settings.baseUrl, _root_.com.histmersion.plugins.ai.BaseUrl.Setting("agents.providers.anthropic.base-url"))

  override def check(model: com.histmersion.plugin.AiProvider.CheckModel): Either[com.histmersion.plugin.AiProvider.CheckResultPart1, Unit] =
    base.left
      .map(p => com.histmersion.plugin.AiProvider.CheckResultPart1(p.value))
      .flatMap(_ =>
        Either.cond(
          settings.apiKey.value.nonEmpty,
          (),
          com.histmersion.plugin.AiProvider.CheckResultPart1("no Anthropic API key — set ANTHROPIC_API_KEY in the server's environment")
        )
      )

  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val schema = request.answerSchemaRendered.orElse(request.answerSchema)
    val sent: Either[String, AiTurn] = for
      url <- base.left.map(_.value).map(_.endpoint("v1", "messages"))
      body <- AnthropicRequest.of(request).left.map(_.value)
      _ <- Either.cond(
        schema.isEmpty || AnthropicProvider.structuredOutput(request.model),
        (),
        AnthropicProvider.noStructuredOutput(request.model).value
      )
      answer <- http
        .post(
          AiHttp.PostUrl(url.value),
          AnthropicProvider.headers(settings.apiKey),
          body.body,
          AiHttp.PostTimeoutSeconds(math.max(1, settings.timeoutSeconds.value))
        )
        .left
        .map(e => SendFailure(SendFailure.Reported(e.value)).describe(SendFailure.Method("POST"), AiHttp.GetUrl(url.value)).value)
      turn <- AnthropicAnswer(AnthropicAnswer.Status(answer.status.value), AnthropicAnswer.Body(answer.body.value)).turn.left.map(why =>
        s"RuntimeException: Claude API error: ${why.value}"
      )
    yield turn
    sent.left.map(m => AiFailure.Error(AiFailure.Error.Message(m)))

object AnthropicProvider:
  type WithoutStructuredOutput = WithoutStructuredOutputValue
  type WithoutStructuredOutputValue = WithoutStructuredOutputValue.T
  object WithoutStructuredOutputValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Version = "2023-06-01"

  def headers(key: com.histmersion.plugins.ai.ProviderSettings.ApiKey): Map[com.histmersion.plugin.AiHttp.PostHeadersPart1, com.histmersion.plugin.AiHttp.PostHeadersPart2] =
    Map(
      AiHttp.PostHeadersPart1("x-api-key") -> AiHttp.PostHeadersPart2(key.value),
      AiHttp.PostHeadersPart1("anthropic-version") -> AiHttp.PostHeadersPart2(Version),
      AiHttp.PostHeadersPart1("content-type") -> AiHttp.PostHeadersPart2("application/json")
    )

  /** The models known NOT to give structured output (older than Claude 4.1); any other model is taken to. */
  val WithoutStructuredOutput: Set[com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutput] = Set(
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-5-sonnet-20241022"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-5-sonnet-latest"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-5-haiku-20241022"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-5-haiku-latest"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-opus-20240229"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-sonnet-20240229"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-3-haiku-20240307"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-sonnet-4-20250514"),
    _root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue("claude-opus-4-20250514")
  )

  def structuredOutput(model: com.histmersion.plugin.AiTurn.Request.Model): Boolean = (!WithoutStructuredOutput(_root_.com.histmersion.plugins.ai.AnthropicProvider.WithoutStructuredOutputValue((model).value)))

  def noStructuredOutput(model: com.histmersion.plugin.AiTurn.Request.Model): com.histmersion.plugin.AiFailure.Error.Message =
    com.histmersion.plugin.AiFailure.Error.Message(
      s"UnsupportedModelForStructuredOutputException: Model '${model.value}' does not support structured output. " +
        "Structured output is only supported by Claude 4.1+ models (e.g., claude-sonnet-4-5, claude-opus-4-1-20250805)."
    )

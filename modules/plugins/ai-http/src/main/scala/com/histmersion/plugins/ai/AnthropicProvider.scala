package com.histmersion
package plugins.ai
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

/** The Anthropic Messages API (`anthropic:<model>`) over the host's [[AiHttp]]: one call per turn, keyed by
  * `x-api-key`. Its failures read as they always have (`RuntimeException: Claude API error: …`), whole: the provider
  * asks for verbatim failures and for the loop's iteration notes, which the API is sent as user messages.
  */
final class AnthropicProvider(settings: ProviderSettings) extends AiProvider:
  def title: AiProvider.Title = AiProvider.Title("Anthropic API")

  override def capabilities: AiCapabilities =
    AiCapabilities(
      tools = AiCapabilities.Tools(true),
      structuredOutput = AiCapabilities.StructuredOutput(true),
      iterationMarkers = AiCapabilities.IterationMarkers(true),
      verbatimFailures = AiCapabilities.VerbatimFailures(true)
    )

  private def base: Either[Problem, BaseUrl] =
    BaseUrl.parse(settings.baseUrl, BaseUrl.Setting("agents.providers.anthropic.base-url"))

  override def check(model: AiProvider.CheckModel): Either[Problem, Unit] =
    base
      .flatMap(_ =>
        Either.cond(
          settings.apiKey.value.nonEmpty,
          (),
          Problem.Unclassified(Legacy.Text("no Anthropic API key — set ANTHROPIC_API_KEY in the server's environment"))
        )
      )

  def turn(request: AiTurn.Request, http: AiHttp): Either[Problem, AiTurn] =
    val schema = request.answerSchemaRendered.orElse(request.answerSchema)
    for
      url <- base.map(_.endpoint("v1", "messages"))
      body <- AnthropicRequest.of(request)
      _ <- Either.cond(
        schema.isEmpty || AnthropicProvider.structuredOutput(request.model),
        (),
        AnthropicProvider.noStructuredOutput(request.model)
      )
      answer <- http.post(
        Plugin.Http.Url(url.value),
        AnthropicProvider.headers(settings.apiKey),
        body.body,
        AiHttp.TimeoutSeconds(math.max(1, settings.timeoutSeconds.value))
      )
      turn <- AnthropicAnswer(
        AnthropicAnswer.Status(answer.status.value),
        AnthropicAnswer.Body(answer.body.value)
      ).turn
    yield turn

object AnthropicProvider:
  type WithoutStructuredOutput = WithoutStructuredOutputValue
  type WithoutStructuredOutputValue = WithoutStructuredOutputValue.T
  object WithoutStructuredOutputValue extends Newtype[String, Pure]

  val Version = "2023-06-01"

  def headers(key: ProviderSettings.ApiKey): Map[Header.Name, Header.Value] =
    Map(
      Header.Name("x-api-key") -> Header.Value(key.value),
      Header.Name("anthropic-version") -> Header.Value(Version),
      Header.Name("content-type") -> Header.Value("application/json")
    )

  /** The models known NOT to give structured output (older than Claude 4.1); any other model is taken to. */
  val WithoutStructuredOutput: Set[WithoutStructuredOutput] = Set(
    WithoutStructuredOutputValue("claude-3-5-sonnet-20241022"),
    WithoutStructuredOutputValue("claude-3-5-sonnet-latest"),
    WithoutStructuredOutputValue("claude-3-5-haiku-20241022"),
    WithoutStructuredOutputValue("claude-3-5-haiku-latest"),
    WithoutStructuredOutputValue("claude-3-opus-20240229"),
    WithoutStructuredOutputValue("claude-3-sonnet-20240229"),
    WithoutStructuredOutputValue("claude-3-haiku-20240307"),
    WithoutStructuredOutputValue("claude-sonnet-4-20250514"),
    WithoutStructuredOutputValue("claude-opus-4-20250514")
  )

  def structuredOutput(model: AiTurn.Request.Model): Boolean =
    (!WithoutStructuredOutput(WithoutStructuredOutputValue((model).value)))

  def noStructuredOutput(model: AiTurn.Request.Model): Problem =
    Problem.Unclassified(
      Legacy.Text(
        s"UnsupportedModelForStructuredOutputException: Model '${model.value}' does not support structured output. " +
          "Structured output is only supported by Claude 4.1+ models (e.g., claude-sonnet-4-5, claude-opus-4-1-20250805)."
      )
    )

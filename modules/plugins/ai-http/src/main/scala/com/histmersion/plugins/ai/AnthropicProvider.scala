package com.histmersion.plugins.ai

import com.histmersion.plugin.*

/** The Anthropic Messages API (`anthropic:<model>`) over the host's [[AiHttp]]: one call per turn, keyed by
  * `x-api-key`. Its failures read as they always have (`RuntimeException: Claude API error: …`), whole: the provider
  * asks for verbatim failures and for the loop's iteration notes, which the API is sent as user messages.
  */
final class AnthropicProvider(settings: ProviderSettings) extends AiProvider:
  def title: String = "Anthropic API"

  override def capabilities: AiCapabilities =
    AiCapabilities(tools = true, structuredOutput = true, iterationMarkers = true, verbatimFailures = true)

  private def base: Either[String, BaseUrl] =
    BaseUrl.parse(settings.baseUrl, "agents.providers.anthropic.base-url")

  override def check(model: String): Either[String, Unit] =
    base.flatMap(_ =>
      Either.cond(
        settings.apiKey.nonEmpty,
        (),
        "no Anthropic API key — set ANTHROPIC_API_KEY in the server's environment"
      )
    )

  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val schema = request.answerSchemaRendered.orElse(request.answerSchema)
    val sent = for
      url <- base.map(_.endpoint("v1", "messages"))
      body <- AnthropicRequest.of(request)
      _ <- Either.cond(
        schema.isEmpty || AnthropicProvider.structuredOutput(request.model),
        (),
        AnthropicProvider.noStructuredOutput(request.model)
      )
      answer <- http
        .post(url, AnthropicProvider.headers(settings.apiKey), body.body, math.max(1, settings.timeoutSeconds))
        .left
        .map(e => SendFailure(e).describe("POST", url))
      turn <- AnthropicAnswer(answer.status, answer.body).turn.left.map(why =>
        s"RuntimeException: Claude API error: $why"
      )
    yield turn
    sent.left.map(AiFailure.Error(_))

object AnthropicProvider:
  /** The API version every request names. */
  val Version = "2023-06-01"

  def headers(key: String): Map[String, String] =
    Map("x-api-key" -> key, "anthropic-version" -> Version, "content-type" -> "application/json")

  /** The models known NOT to give structured output (older than Claude 4.1); any other model is taken to. */
  val WithoutStructuredOutput: Set[String] = Set(
    "claude-3-5-sonnet-20241022",
    "claude-3-5-sonnet-latest",
    "claude-3-5-haiku-20241022",
    "claude-3-5-haiku-latest",
    "claude-3-opus-20240229",
    "claude-3-sonnet-20240229",
    "claude-3-haiku-20240307",
    "claude-sonnet-4-20250514",
    "claude-opus-4-20250514"
  )

  def structuredOutput(model: String): Boolean = !WithoutStructuredOutput(model)

  def noStructuredOutput(model: String): String =
    s"UnsupportedModelForStructuredOutputException: Model '$model' does not support structured output. " +
      "Structured output is only supported by Claude 4.1+ models (e.g., claude-sonnet-4-5, claude-opus-4-1-20250805)."

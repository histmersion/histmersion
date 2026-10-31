package com.histmersion.plugins.ai

import com.histmersion.plugin.*

/** An OpenAI-compatible chat-completions API (`openai:<model>`, `local:<model>`) over the host's [[AiHttp]]: one call
  * per turn, keyed by `Authorization: Bearer`. Its failures read as they always have (`RuntimeException: OpenAI API
  * error: …`), whole: the provider asks for verbatim failures and for the loop's iteration notes.
  *
  * @param name
  *   the provider's name, as `agents.providers.<name>` and `<name>:<model>` say it
  * @param keyed
  *   the variable its key comes from when it needs one (`OPENAI_API_KEY`); a local server needs none and is sent the
  *   placeholder `none`
  */
final class ChatCompletionsProvider(
    name: String,
    val title: String,
    settings: ProviderSettings,
    keyed: Option[(String, String)]
) extends AiProvider:

  override def capabilities: AiCapabilities =
    AiCapabilities(tools = true, structuredOutput = true, iterationMarkers = true, verbatimFailures = true)

  private def base: Either[String, BaseUrl] = BaseUrl.parse(settings.baseUrl, s"agents.providers.$name.base-url")

  override def check(model: String): Either[String, Unit] =
    base.flatMap(_ =>
      keyed match
        case Some((label, variable)) if settings.apiKey.isEmpty =>
          Left(s"no $label API key — set $variable in the server's environment")
        case _ => Right(())
    )

  private def key: String = if settings.apiKey.isEmpty then "none" else settings.apiKey

  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn] =
    val sent = for
      url <- base.map(_.endpoint("chat", "completions"))
      body = ChatRequest.of(request, settings.strictTools).body
      answer <- http
        .post(url, ChatCompletionsProvider.headers(key), body, math.max(1, settings.timeoutSeconds))
        .left
        .map(e => SendFailure(e).describe("POST", url))
      turn <- ChatAnswer(answer.status, answer.body).turn.left.map(why => s"RuntimeException: OpenAI API error: $why")
    yield turn
    sent.left.map(AiFailure.Error(_))

object ChatCompletionsProvider:

  def headers(key: String): Map[String, String] =
    Map("Authorization" -> s"Bearer $key", "Content-Type" -> "application/json")

  /** The OpenAI API, keyed by `OPENAI_API_KEY`. */
  def openAi(settings: ProviderSettings): ChatCompletionsProvider =
    ChatCompletionsProvider("openai", "OpenAI API", settings, Some(("OpenAI", "OPENAI_API_KEY")))

  /** Any OpenAI-compatible local server, with or without a key. */
  def local(settings: ProviderSettings): ChatCompletionsProvider =
    ChatCompletionsProvider("local", "OpenAI-compatible local server", settings, None)

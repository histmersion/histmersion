package com.histmersion
package plugins.ai

import com.histmersion.plugin.Problem
import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

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
    name: ChatCompletionsProvider.Name,
    val title: AiProvider.Title,
    settings: ProviderSettings,
    keyed: Option[(ChatCompletionsProvider.KeyLabel, ChatCompletionsProvider.KeyVariable)]
) extends AiProvider:

  override def capabilities: AiCapabilities =
    AiCapabilities(
      tools = AiCapabilities.Tools(true),
      structuredOutput = AiCapabilities.StructuredOutput(true),
      iterationMarkers = AiCapabilities.IterationMarkers(true),
      verbatimFailures = AiCapabilities.VerbatimFailures(true)
    )

  private def base: Either[Problem, BaseUrl] =
    BaseUrl.parse(settings.baseUrl, BaseUrl.Setting(s"agents.providers.${name.value}.base-url"))

  override def check(model: AiProvider.CheckModel): Either[Problem, Unit] =
    base.left
      .map(p => Problem(p.value))
      .flatMap(_ =>
        keyed match
          case Some((label, variable)) if settings.apiKey.value.isEmpty =>
            Left(Problem(s"no ${label.value} API key — set ${variable.value} in the server's environment"))
          case _ => Right(())
      )

  private def key: ProviderSettings.ApiKey =
    if settings.apiKey.value.isEmpty then ProviderSettings.ApiKey("none") else settings.apiKey

  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val sent: Either[String, AiTurn] = for
      url <- base.left.map(_.value).map(_.endpoint("chat", "completions"))
      body = ChatRequest.of(request, settings.strictTools).body
      answer <- http
        .post(
          Plugin.Http.Url(url.value),
          ChatCompletionsProvider.headers(key),
          body,
          AiHttp.TimeoutSeconds(math.max(1, settings.timeoutSeconds.value))
        )
        .left
        .map(e =>
          SendFailure(e)
            .describe(SendFailure.Method("POST"), Plugin.Http.Url(url.value))
            .value
        )
      turn <- ChatAnswer(ChatAnswer.Status(answer.status.value), ChatAnswer.Body(answer.body.value)).turn.left
        .map(why => s"RuntimeException: OpenAI API error: ${why.value}")
    yield turn
    sent.left.map(m => AiFailure.Error(Problem(m)))

object ChatCompletionsProvider:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type KeyLabel = KeyLabel.T
  object KeyLabel extends Newtype[String, Pure]
  type KeyVariable = KeyVariable.T
  object KeyVariable extends Newtype[String, Pure]

  def headers(key: ProviderSettings.ApiKey): Map[Header.Name, Header.Value] =
    Map(
      Header.Name("Authorization") -> Header.Value(s"Bearer ${key.value}"),
      Header.Name("Content-Type") -> Header.Value("application/json")
    )

  /** The OpenAI API, keyed by `OPENAI_API_KEY`. */
  def openAi(settings: ProviderSettings): ChatCompletionsProvider =
    ChatCompletionsProvider(
      Name("openai"),
      AiProvider.Title("OpenAI API"),
      settings,
      Some((KeyLabel("OpenAI"), KeyVariable("OPENAI_API_KEY")))
    )

  /** Any OpenAI-compatible local server, with or without a key. */
  def local(settings: ProviderSettings): ChatCompletionsProvider =
    ChatCompletionsProvider(Name("local"), AiProvider.Title("OpenAI-compatible local server"), settings, None)

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
    name: ChatCompletionsProvider.Name,
    val title: ChatCompletionsProvider.Title,
    settings: ProviderSettings,
    keyed: Option[(ChatCompletionsProvider.KeyedPart1, ChatCompletionsProvider.KeyedPart2)]
) extends AiProvider:

  override def capabilities: AiCapabilities =
    AiCapabilities(tools = _root_.com.histmersion.plugin.AiCapabilities.Tools(true), structuredOutput = _root_.com.histmersion.plugin.AiCapabilities.StructuredOutput(true), iterationMarkers = _root_.com.histmersion.plugin.AiCapabilities.IterationMarkers(true), verbatimFailures = _root_.com.histmersion.plugin.AiCapabilities.VerbatimFailures(true))

  private def base: Either[com.histmersion.plugins.ai.ChatCompletionsProvider.BaseResultPart1, BaseUrl] = BaseUrl.parse(settings.baseUrl, _root_.com.histmersion.plugins.ai.BaseUrl.ParseSetting(s"agents.providers.$name.base-url"))

  override def check(model: String): Either[String, Unit] =
    base.flatMap(_ =>
      keyed match
        case Some((label, variable)) if settings.apiKey.isEmpty =>
          Left(s"no $label API key — set $variable in the server's environment")
        case _ => Right(())
    )

  private def key: com.histmersion.plugins.ai.ChatCompletionsProvider.KeyResult = if settings.apiKey.isEmpty then _root_.com.histmersion.plugins.ai.ChatCompletionsProvider.KeyResult("none") else settings.apiKey

  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
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

  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyedPart1 = KeyedPart1.T
  object KeyedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyedPart2 = KeyedPart2.T
  object KeyedPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type BaseResultPart1 = BaseResultPart1.T
  object BaseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersResultPart1 = HeadersResultPart1.T
  object HeadersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersResultPart2 = HeadersResultPart2.T
  object HeadersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadersKey = HeadersKey.T
  object HeadersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def headers(key: com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersKey): Map[com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersResultPart1, com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersResultPart2] =
    Map({ val refactorTuple = "Authorization" -> s"Bearer $key"; (_root_.com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersResultPart1(refactorTuple._1), _root_.com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersResultPart2(refactorTuple._2)) }, { val refactorTuple2 = "Content-Type" -> "application/json"; (_root_.com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersResultPart1(refactorTuple2._1), _root_.com.histmersion.plugins.ai.ChatCompletionsProvider.HeadersResultPart2(refactorTuple2._2)) })

  /** The OpenAI API, keyed by `OPENAI_API_KEY`. */
  def openAi(settings: ProviderSettings): ChatCompletionsProvider =
    ChatCompletionsProvider(_root_.com.histmersion.plugins.ai.ChatCompletionsProvider.Name("openai"), _root_.com.histmersion.plugins.ai.ChatCompletionsProvider.Title("OpenAI API"), settings, Some((_root_.com.histmersion.plugins.ai.ChatCompletionsProvider.KeyedPart1("OpenAI"), _root_.com.histmersion.plugins.ai.ChatCompletionsProvider.KeyedPart2("OPENAI_API_KEY"))))

  /** Any OpenAI-compatible local server, with or without a key. */
  def local(settings: ProviderSettings): ChatCompletionsProvider =
    ChatCompletionsProvider(_root_.com.histmersion.plugins.ai.ChatCompletionsProvider.Name("local"), _root_.com.histmersion.plugins.ai.ChatCompletionsProvider.Title("OpenAI-compatible local server"), settings, None)

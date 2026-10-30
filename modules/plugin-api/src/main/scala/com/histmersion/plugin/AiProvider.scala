package com.histmersion.plugin

/** AI providers, the fifth part of the app that plugins extend: who serves a model the embedded agents call. A plugin
  * adds an [[AiProvider]] to [[PluginContext.aiProviders]] under the PROVIDER NAME — the part before the colon of a
  * `provider:model` reference (`agents.roles.extractor = "gemini:gemini-3-pro"` asks the provider registered as
  * `gemini`).
  *
  * The provider answers ONE model call at a time ([[AiProvider.turn]]). The host owns the rest: the prompts, the tool
  * loop and the tools themselves (run in process, under each step's allowlist), the typed answer's decoding and
  * validation and its retries, budgets, allowances, approvals, the trace, and which model of a role's chain is picked
  * before every call. A provider's models are METERED: they need a price in `agents.prices` (`0/0` for a free one), and
  * allowances on `<provider>:*` cap them.
  */

/** One provider. Called from many runs at once (each call is independent: keep no per-call state). Failures are values;
  * an exception from [[turn]] is the provider's error.
  */
trait AiProvider:
  /** What to call it: "Google Gemini API". */
  def title: String

  def description: String = ""

  def capabilities: AiCapabilities = AiCapabilities()

  /** The provider spends the PERSON'S own subscription (a login, not an API key): refused while the server listens
    * beyond loopback, where another client could spend it.
    */
  def spendsPersonalPlan: Boolean = false

  /** Whether a client for `model` can be used now (a key configured, a URL given) — asked before a model is picked, so
    * a provider that cannot run is passed over with its reason. Spends nothing.
    */
  def check(model: String): Either[String, Unit] = Right(())

  /** The models the provider lists, from a FREE call (never a generation), for `agent doctor`; `Right(Nil)` = it has no
    * listing.
    */
  def models(http: AiHttp): Either[String, List[String]] = Right(Nil)

  /** Its own checks for `agent doctor` (`online = false`: send nothing). Spends nothing. */
  def doctor(online: Boolean, http: AiHttp): List[AiCheck] = Nil

  /** ONE model call. */
  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn]

package com.histmersion.plugin

/** AI providers, the fifth part of the app that plugins extend: who serves a model the embedded agents call. A plugin
  * adds an [[AiProvider]] to [[Plugin.Context.aiProviders]] under the PROVIDER NAME — the part before the colon of a
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
  def title: com.histmersion.plugin.AiProvider.TitleResult

  def description: com.histmersion.plugin.AiProvider.DescriptionResult = ""

  def capabilities: AiCapabilities = AiCapabilities()

  /** The provider spends the PERSON'S own subscription (a login, not an API key): refused while the server listens
    * beyond loopback, where another client could spend it.
    */
  def spendsPersonalPlan: com.histmersion.plugin.AiProvider.SpendsPersonalPlanResult = false

  /** Whether a client for `model` can be used now (a key configured, a URL given) — asked before a model is picked, so
    * a provider that cannot run is passed over with its reason. Spends nothing.
    */
  def check(model: com.histmersion.plugin.AiProvider.CheckModel): Either[com.histmersion.plugin.AiProvider.CheckResultPart1, Unit] = Right(())

  /** The models the provider lists, from a FREE call (never a generation), for `agent doctor`; `Right(Nil)` = it has no
    * listing.
    */
  def models(http: AiHttp): Either[com.histmersion.plugin.AiProvider.ModelsResultPart1, List[com.histmersion.plugin.AiProvider.ModelsResultPart2]] = Right(Nil)

  /** Its own checks for `agent doctor` (`online = false`: send nothing). Spends nothing. */
  def doctor(online: com.histmersion.plugin.AiProvider.DoctorOnline, http: AiHttp): List[AiCheck] = Nil

  /** ONE model call. */
  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn]

object AiProvider:
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescriptionResult = DescriptionResult.T
  object DescriptionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpendsPersonalPlanResult = SpendsPersonalPlanResult.T
  object SpendsPersonalPlanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckModel = CheckModel.T
  object CheckModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModelsResultPart1 = ModelsResultPart1.T
  object ModelsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModelsResultPart2 = ModelsResultPart2.T
  object ModelsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoctorOnline = DoctorOnline.T
  object DoctorOnline extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

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
  def title: AiProvider.Title

  def description: AiProvider.Description = AiProvider.Description("")

  def capabilities: AiCapabilities = AiCapabilities()

  /** The provider spends the PERSON'S own subscription (a login, not an API key): refused while the server listens
    * beyond loopback, where another client could spend it.
    */
  def spendsPersonalPlan: AiProvider.SpendsPersonalPlan = AiProvider.SpendsPersonalPlan(false)

  /** Whether a client for `model` can be used now (a key configured, a URL given) — asked before a model is picked, so
    * a provider that cannot run is passed over with its reason. Spends nothing.
    */
  def check(model: AiProvider.CheckModel): Either[Problem, Unit] = Right(())

  /** The models the provider lists, from a FREE call (never a generation), for `agent doctor`; `Right(Nil)` = it has no
    * listing.
    */
  def models(http: AiHttp): Either[Problem, List[AiProvider.ModelName]] = Right(Nil)

  /** Its own checks for `agent doctor` (`online = false`: send nothing). Spends nothing. */
  def doctor(online: AiProvider.DoctorOnline, http: AiHttp): List[AiCheck] = Nil

  /** ONE model call: its turn, or why there is none — the provider's plan reporting its own usage limit as
    * [[Problem.Upstream.Limited]] (the host asks the chain's next model meanwhile), anything else pausing the run.
    */
  def turn(request: AiTurn.Request, http: AiHttp): Either[Problem, AiTurn]

object AiProvider:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type SpendsPersonalPlan = SpendsPersonalPlan.T
  object SpendsPersonalPlan extends Newtype[Boolean, Pure]
  type CheckModel = CheckModel.T
  object CheckModel extends Newtype[String, Pure]
  type ModelName = ModelName.T
  object ModelName extends Newtype[String, Pure]
  type DoctorOnline = DoctorOnline.T
  object DoctorOnline extends Newtype[Boolean, Pure]

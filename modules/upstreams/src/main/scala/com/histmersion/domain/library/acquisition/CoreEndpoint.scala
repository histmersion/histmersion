package com.histmersion.domain.library.acquisition
import hearth.kindlings.sconfigderivation.ConfigReader

/** CORE's endpoint and its plain key (`histmersion.acquire.core.url`, `.api-key`); the budget's limits are the host's.
  */
final case class CoreEndpoint(url: CoreEndpoint.Url, apiKey: CoreEndpoint.ApiKey) derives ConfigReader:
  /** The key, when one is configured. */
  def key: Option[com.histmersion.domain.library.acquisition.CoreEndpoint.KeyResult] = (Option(apiKey).map((_).value.trim).filter(_.nonEmpty)).map(refactorValue => _root_.com.histmersion.domain.library.acquisition.CoreEndpoint.KeyResult(refactorValue))

object CoreEndpoint:
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiKey = ApiKey.T
  object ApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

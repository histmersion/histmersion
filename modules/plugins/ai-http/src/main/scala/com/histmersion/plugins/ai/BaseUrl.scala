package com.histmersion.plugins.ai

import sttp.model.Uri

/** A provider's base URL (`agents.providers.<name>.base-url`), parsed as the provider APIs have always been addressed:
  * an endpoint's path segments are appended to it, any query it has kept after them.
  */
final case class BaseUrl(uri: Uri):
  /** The endpoint under this URL: `v1/messages`, `chat/completions`. */
  def endpoint(segments: String*): com.histmersion.plugins.ai.BaseUrl.Endpoint = _root_.com.histmersion.plugins.ai.BaseUrl.Endpoint(uri.addPath(segments.toList).toString)

object BaseUrl:
  type Endpoint = Endpoint.T
  object Endpoint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Setting = Setting.T
  object Setting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** `text` as a base URL, or why not, naming the `setting` it came from. */

  def parse(text: com.histmersion.plugins.ai.ProviderSettings.BaseUrl, setting: com.histmersion.plugins.ai.BaseUrl.Setting): Either[com.histmersion.plugins.ai.BaseUrl.Problem, BaseUrl] =
    (Uri.parse((text).value).left.map(e => s"$setting: $e").map(BaseUrl(_))).left.map(refactorError => _root_.com.histmersion.plugins.ai.BaseUrl.Problem(refactorError))

package com.histmersion
package plugins.ai

import sttp.model.Uri
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A provider's base URL (`agents.providers.<name>.base-url`), parsed as the provider APIs have always been addressed:
  * an endpoint's path segments are appended to it, any query it has kept after them.
  */
final case class BaseUrl(uri: Uri):
  /** The endpoint under this URL: `v1/messages`, `chat/completions`. */
  def endpoint(segments: String*): BaseUrl.Endpoint = BaseUrl.Endpoint(uri.addPath(segments.toList).toString)

object BaseUrl:
  type Endpoint = Endpoint.T
  object Endpoint extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type Setting = Setting.T
  object Setting extends RefinedType[String, Pure]
  /** `text` as a base URL, or why not, naming the `setting` it came from. */

  def parse(text: ProviderSettings.BaseUrl, setting: Setting): Either[Problem, BaseUrl] =
    (Uri.parse((text).value).left.map(e => s"$setting: $e").map(BaseUrl(_))).left.map(refactorError => Problem(refactorError))

package com.histmersion
package plugins.ai
import com.histmersion.util.problem.{External, Problem}

import sttp.model.Uri
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A provider's base URL (`agents.providers.<name>.base-url`), parsed as the provider APIs have always been addressed:
  * an endpoint's path segments are appended to it, any query it has kept after them.
  */
final case class BaseUrl(uri: Uri):
  /** The endpoint under this URL: `v1/messages`, `chat/completions`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def endpoint(segments: String*): BaseUrl.Endpoint = BaseUrl.Endpoint(uri.addPath(segments.toList).toString)

object BaseUrl:
  type Endpoint = Endpoint.T
  object Endpoint extends Newtype[String, Pure]
  type Setting = Setting.T
  object Setting extends Newtype[String, Pure]

  /** `text` as a base URL, or why not, naming the `setting` it came from. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(text: ProviderSettings.BaseUrl, setting: Setting): Either[Problem, BaseUrl] =
    Uri
      .parse((text).value)
      .map(BaseUrl(_))
      .left
      .map(e =>
        Problem.Config.Setting(
          Problem.SettingKey(s"histmersion.$setting"),
          Problem.Config.Fault.Unreadable(External.Reported.of(s"$e"))
        )
      )

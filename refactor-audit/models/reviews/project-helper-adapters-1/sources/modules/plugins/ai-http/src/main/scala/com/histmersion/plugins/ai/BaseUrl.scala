package com.histmersion.plugins.ai

import sttp.model.Uri

/** A provider's base URL (`agents.providers.<name>.base-url`), parsed as the provider APIs have always been addressed:
  * an endpoint's path segments are appended to it, any query it has kept after them.
  */
final case class BaseUrl(uri: Uri):
  /** The endpoint under this URL: `v1/messages`, `chat/completions`. */
  def endpoint(segments: String*): com.histmersion.plugins.ai.BaseUrl.EndpointResult = _root_.com.histmersion.plugins.ai.BaseUrl.EndpointResult(uri.addPath(segments.toList).toString)

object BaseUrl:
  /** `text` as a base URL, or why not, naming the `setting` it came from. */
  type EndpointResult = EndpointResult.T
  object EndpointResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseText = ParseText.T
  object ParseText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSetting = ParseSetting.T
  object ParseSetting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(text: com.histmersion.plugins.ai.BaseUrl.ParseText, setting: com.histmersion.plugins.ai.BaseUrl.ParseSetting): Either[com.histmersion.plugins.ai.BaseUrl.ParseResultPart1, BaseUrl] =
    (Uri.parse((text).value).left.map(e => s"$setting: $e").map(BaseUrl(_))).left.map(refactorError => _root_.com.histmersion.plugins.ai.BaseUrl.ParseResultPart1(refactorError))

package com.histmersion.plugins.ai

import sttp.model.Uri

/** A provider's base URL (`agents.providers.<name>.base-url`), parsed as the provider APIs have always been addressed:
  * an endpoint's path segments are appended to it, any query it has kept after them.
  */
final case class BaseUrl(uri: Uri):
  /** The endpoint under this URL: `v1/messages`, `chat/completions`. */
  def endpoint(segments: String*): String = uri.addPath(segments.toList).toString

object BaseUrl:
  /** `text` as a base URL, or why not, naming the `setting` it came from. */
  def parse(text: String, setting: String): Either[String, BaseUrl] =
    Uri.parse(text).left.map(e => s"$setting: $e").map(BaseUrl(_))

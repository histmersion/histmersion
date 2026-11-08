package com.histmersion
package plugin.testkit

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

/** One line the server would have logged.
  *
  * @param source
  *   the plugin's id for what the plugin logged through its `Plugin.Log`; `acquire: <fetcher>` lines are the
  *   acquisition chain's, `media`/`plugins` the host's own
  * @param warn
  *   logged as a warning
  */
final case class LogLine(source: LogLine.Source, warn: Boolean, message: LogLine.Message):
  override def toString: String = s"${if warn then "WARN" else "INFO"} [${source.value}] ${message.value}"

object LogLine:
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Message = Message.T
  object Message extends Newtype[String, Pure]

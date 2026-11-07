package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

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
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Message = Message.T
  object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

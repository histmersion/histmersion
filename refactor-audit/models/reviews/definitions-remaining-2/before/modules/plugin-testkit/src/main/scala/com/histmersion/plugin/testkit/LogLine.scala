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
final case class LogLine(source: String, warn: Boolean, message: String):
  override def toString: String = s"${if warn then "WARN" else "INFO"} [$source] $message"

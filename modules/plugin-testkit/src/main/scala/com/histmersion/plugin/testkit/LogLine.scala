package com.histmersion
package plugin.testkit

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One line the server would have logged.
  *
  * @param source
  *   the plugin's id for what the plugin logged through its `Plugin.Log`; `acquire: <fetcher>` lines are the
  *   acquisition chain's, `media`/`plugins` the host's own
  * @param warn
  *   logged as a warning
  */
@tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
final case class LogLine(source: LogLine.Source, warn: Boolean, message: LogLine.Text):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def toString: String = s"${if warn then "WARN" else "INFO"} [${source.value}] ${message.value}"

object LogLine:
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

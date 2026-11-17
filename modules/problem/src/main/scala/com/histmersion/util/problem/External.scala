package com.histmersion
package util.problem

import io.github.iltotore.iron.constraint.any.Pure

/** Text from outside Histmersion that a problem carries: an exception's message, a process's standard error, the start
  * of an upstream's reply, a plugin's or a model's words. Each is captured once, where the exception, the process or
  * the reply was met; it is shown at an edge and never branched on. What an exception or another layer reported is kept
  * whole (every line, uncut): it is the detail a failure is debugged from. A process's standard error, an upstream's
  * reply, a plugin's and a model's words are bounded excerpts of a larger text.
  */
object External:

  /** An exception's class and message, `<class>: <message>`, whole: every line, uncut. */
  type Reported = Reported.T
  object Reported extends Newtype[String, Pure]:

    def of(thrown: Throwable): Reported =
      val message = Option(thrown.getMessage).map(m => s": $m").getOrElse("")
      Reported(s"${thrown.getClass.getName}$message")

    /** What another layer reported as text, whole. */
    def of(text: String): Reported = Reported(s"$text")

  /** The end of what a process wrote to its standard error, at most [[Stderr.Limit]] characters. */
  type Stderr = Stderr.T
  object Stderr extends Newtype[String, Pure]:
    val Limit = 500

    def tail(text: String): Stderr = Stderr(s"${text.trim.takeRight(Limit)}")

  /** The start of an upstream's reply body, at most [[ReplyExcerpt.Limit]] characters. */
  type ReplyExcerpt = ReplyExcerpt.T
  object ReplyExcerpt extends Newtype[String, Pure]:
    val Limit = 200

    def head(body: String): ReplyExcerpt = ReplyExcerpt(s"${body.take(Limit)}")

  /** Words a plugin supplied (a reason, a title). It never holds a secret: the plugin SPI forbids that. */
  type PluginSaid = PluginSaid.T
  object PluginSaid extends Newtype[String, Pure]:
    val Limit = 500

    def of(text: String): PluginSaid = PluginSaid(s"${text.take(Limit)}")

  /** Words from a model's answer that failed validation, at most [[ModelSaid.Limit]] characters. */
  type ModelSaid = ModelSaid.T
  object ModelSaid extends Newtype[String, Pure]:
    val Limit = 200

    def of(text: String): ModelSaid = ModelSaid(s"${text.take(Limit)}")

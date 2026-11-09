package com.histmersion
package util.problem

import io.github.iltotore.iron.constraint.any.Pure

/** Text from outside Histmersion that a problem carries: an exception's message, a process's standard error, the start
  * of an upstream's reply, a plugin's or a model's words. Each is captured once, where the exception, the process or the
  * reply was met, cut to a bounded length; it is shown at an edge and never branched on.
  */
object External:

  /** An exception's class and message, `<class>: <message>`, its first line, at most [[Reported.Limit]] characters. */
  type Reported = Reported.T
  object Reported extends Newtype[String, Pure]:
    val Limit = 300

    def of(thrown: Throwable): Reported =
      val message = Option(thrown.getMessage).map(m => s": $m").getOrElse("")
      Reported(s"${thrown.getClass.getName}$message".linesIterator.nextOption().getOrElse("").take(Limit))

    /** What another layer reported as text, cut the same way. */
    def of(text: String): Reported = Reported(s"${text.linesIterator.nextOption().getOrElse("").take(Limit)}")

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

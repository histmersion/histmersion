package com.histmersion
package domain.automation.agents

import com.histmersion.domain.Problem
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure

/** One LLM step's prompt. `system` is the step's prompt FILE(S) (bundled as resources, so one copy per prompt and its
  * hash recorded on every attempt); `user` carries the data.
  */
final case class Prompt(system: Prompt.System, user: Prompt.User):
  /** sha256 of the system prompt — which prompt version produced a checkpoint. */
  def hash: Sha256 = Prompt.sha256(system.value)

object Prompt:
  type UserText = UserText.T
  object UserText extends Newtype[String, Pure]

  /** Untrusted text (an OCR'd body, an article under review) as DATA: fenced in tags the system prompt names, with any
    * copy of the closing tag inside it defused so the text cannot end its own fence.
    */

  type System = System.T
  object System extends Newtype[String, Pure]
  type User = User.T
  object User extends Newtype[String, Pure]

  def data(tag: String, text: String): String =
    val safe = text.replace(s"</$tag>", s"</ $tag>")
    s"<$tag>\n$safe\n</$tag>"

  /** The person's notes for a run (manager hints, docs/research-process-managers.md §3.6), as the block appended to a
    * step's USER message: fenced like data, and subordinate to the record. Empty for none.
    */
  def notes(ns: List[String]): String =
    val kept = ns.map(_.trim).filter(_.nonEmpty)
    if kept.isEmpty then ""
    else
      "\n\nNotes from the person who runs this library (follow them unless they contradict the record):\n" +
        data("operator-notes", kept.map(n => s"- $n").mkString("\n"))

  /** The most of a rejected answer a retry carries back. */
  val MaxPreviousAnswer = 60000

  /** Appended to a retried attempt: what code rejected last time. With the rejected answer itself (`previous`, when it
    * was well-formed and only failed a check), the model amends it instead of redoing every lookup — a fixer that
    * started over ran out of turns on its third attempt.
    */
  def retry(problems: List[Problem], previous: Option[String] = None): String =
    val points = problems.take(20).map(p => s"- $p").mkString("\n")
    previous.filter(_.length <= MaxPreviousAnswer) match
      case None =>
        "\n\nYour previous answer was rejected by validation:\n" + points + "\nAnswer again, fixing every point."
      case Some(prev) =>
        "\n\nYour previous answer, below, was rejected by validation for these points only:\n" + points +
          "\nEverything else in it stands. Do not repeat your lookups: return the same answer with exactly " +
          "these points fixed.\n\n" + data("previous-answer", prev)

  def sha256(s: String): Sha256 =
    Sha256.assume(
      java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(s.getBytes(UTF_8))
        .map(b => f"$b%02x")
        .mkString
    )

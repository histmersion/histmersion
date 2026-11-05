package com.histmersion.domain.automation.agents

import java.nio.charset.StandardCharsets.UTF_8

/** One LLM step's prompt. `system` is the step's prompt FILE(S) (bundled as resources, so one copy per prompt and its
  * hash recorded on every attempt); `user` carries the data.
  */
final case class Prompt(system: Prompt.System, user: Prompt.User):
  /** sha256 of the system prompt — which prompt version produced a checkpoint. */
  def hash: com.histmersion.domain.automation.agents.Prompt.HashResult = Prompt.sha256(system)

object Prompt:
  /** Untrusted text (an OCR'd body, an article under review) as DATA: fenced in tags the system prompt names, with any
    * copy of the closing tag inside it defused so the text cannot end its own fence.
    */
  type HashResult = HashResult.T
  object HashResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DataResult = DataResult.T
  object DataResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DataTag = DataTag.T
  object DataTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DataText = DataText.T
  object DataText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesResult = NotesResult.T
  object NotesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesNs = NotesNs.T
  object NotesNs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryResult = RetryResult.T
  object RetryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryProblems = RetryProblems.T
  object RetryProblems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryPrevious = RetryPrevious.T
  object RetryPrevious extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sha256S = Sha256S.T
  object Sha256S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type System = System.T
  object System extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type User = User.T
  object User extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def data(tag: com.histmersion.domain.automation.agents.Prompt.DataTag, text: com.histmersion.domain.automation.agents.Prompt.DataText): com.histmersion.domain.automation.agents.Prompt.DataResult =
    val safe = text.replace(s"</$tag>", s"</ $tag>")
    s"<$tag>\n$safe\n</$tag>"

  /** The person's notes for a run (manager hints, docs/research-process-managers.md §3.6), as the block appended to a
    * step's USER message: fenced like data, and subordinate to the record. Empty for none.
    */
  def notes(ns: List[com.histmersion.domain.automation.agents.Prompt.NotesNs]): com.histmersion.domain.automation.agents.Prompt.NotesResult =
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
  def retry(problems: List[com.histmersion.domain.automation.agents.Prompt.RetryProblems], previous: Option[com.histmersion.domain.automation.agents.Prompt.RetryPrevious] = None): com.histmersion.domain.automation.agents.Prompt.RetryResult =
    val points = problems.take(20).map(p => s"- $p").mkString("\n")
    previous.filter(_.length <= MaxPreviousAnswer) match
      case None =>
        "\n\nYour previous answer was rejected by validation:\n" + points + "\nAnswer again, fixing every point."
      case Some(prev) =>
        "\n\nYour previous answer, below, was rejected by validation for these points only:\n" + points +
          "\nEverything else in it stands. Do not repeat your lookups: return the same answer with exactly " +
          "these points fixed.\n\n" + data("previous-answer", prev)

  def sha256(s: com.histmersion.domain.automation.agents.Prompt.Sha256S): com.histmersion.Sha256 =
    java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(s.getBytes(UTF_8))
      .map(b => f"$b%02x")
      .mkString

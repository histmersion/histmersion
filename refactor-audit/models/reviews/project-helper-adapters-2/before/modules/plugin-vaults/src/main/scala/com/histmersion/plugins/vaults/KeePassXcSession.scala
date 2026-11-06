package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

import scala.concurrent.duration.*

/** One `keepassxc-cli open` session answering several references (KeePassXC's interactive mode, in keepassxc-cli since
  * 2.6): the database is unlocked once — one password, one YubiKey touch — and each reference is one
  * `show -q -a <attribute> <entry>` line on its stdin. What the session prints is read here; all pure.
  */
object KeePassXcSession:

  /** Pure: the prompt the session prints once the database is open and after each answer (`<name>> `, the database's
    * name or file name) — the text after the last line break, once stdout ends in `> `.
    */
  type PromptResult = PromptResult.T
  object PromptResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PromptOut = PromptOut.T
  object PromptOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendableResult = SendableResult.T
  object SendableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendableS = SendableS.T
  object SendableS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeResult = EscapeResult.T
  object EscapeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeS = EscapeS.T
  object EscapeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommandResult = CommandResult.T
  object CommandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommandAttribute = CommandAttribute.T
  object CommandAttribute extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommandEntry = CommandEntry.T
  object CommandEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentResult = SegmentResult.T
  object SegmentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentOut = SegmentOut.T
  object SegmentOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentFrom = SegmentFrom.T
  object SegmentFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentPrompt = SegmentPrompt.T
  object SegmentPrompt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerResult = AnswerResult.T
  object AnswerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerSegment = AnswerSegment.T
  object AnswerSegment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswerCommand = AnswerCommand.T
  object AnswerCommand extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def prompt(out: com.histmersion.plugins.vaults.KeePassXcSession.PromptOut): Option[com.histmersion.plugins.vaults.KeePassXcSession.PromptResult] =
    Option.when((out).value.endsWith("> "))(_root_.com.histmersion.plugins.vaults.KeePassXcSession.PromptResult((out).value.substring((out).value.lastIndexOf('\n') + 1)))

  /** Pure: can `s` be sent as one argument of an interactive line? A line break would end the line. */
  def sendable(s: com.histmersion.plugins.vaults.KeePassXcSession.SendableS): com.histmersion.plugins.vaults.KeePassXcSession.SendableResult = _root_.com.histmersion.plugins.vaults.KeePassXcSession.SendableResult(!(s).value.exists(_.isControl))

  /** Pure: an argument for the interactive mode's own splitting, which reads a backslash as "the next character as it
    * is": every space, tab, quote and backslash is escaped.
    */
  def escape(s: com.histmersion.plugins.vaults.KeePassXcSession.EscapeS): com.histmersion.plugins.vaults.KeePassXcSession.EscapeResult =
    _root_.com.histmersion.plugins.vaults.KeePassXcSession.EscapeResult((s).value.flatMap(c => if c == ' ' || c == '\t' || c == '"' || c == '\\' then s"\\$c" else c.toString))

  /** Pure: the line that asks for one attribute of one entry. */
  def command(attribute: com.histmersion.plugins.vaults.KeePassXcSession.CommandAttribute, entry: com.histmersion.plugins.vaults.KeePassXcSession.CommandEntry): com.histmersion.plugins.vaults.KeePassXcSession.CommandResult = s"show -q -a ${escape(_root_.com.histmersion.plugins.vaults.KeePassXcSession.EscapeS((attribute).value))} ${escape(_root_.com.histmersion.plugins.vaults.KeePassXcSession.EscapeS((entry).value))}"

  /** Pure: what the session printed for the request sent when its output was `from` characters long, once it has
    * printed its prompt again: the text in between (`None` while it has not).
    */
  def segment(out: com.histmersion.plugins.vaults.KeePassXcSession.SegmentOut, from: com.histmersion.plugins.vaults.KeePassXcSession.SegmentFrom, prompt: com.histmersion.plugins.vaults.KeePassXcSession.SegmentPrompt): Option[com.histmersion.plugins.vaults.KeePassXcSession.SegmentResult] =
    Option
      .when((out).value.length >= (from).value + (prompt).value.length && (out).value.endsWith((prompt).value))(_root_.com.histmersion.plugins.vaults.KeePassXcSession.SegmentResult((out).value.substring((from).value, (out).value.length - (prompt).value.length)))

  /** Pure: the value in a segment — without the echo of the request line (a session reading through readline prints it
    * back) and without the line break the value ends with.
    */
  def answer(segment: com.histmersion.plugins.vaults.KeePassXcSession.AnswerSegment, command: com.histmersion.plugins.vaults.KeePassXcSession.AnswerCommand): com.histmersion.plugins.vaults.KeePassXcSession.AnswerResult =
    val body =
      List((command).value + "\r\n", (command).value + "\n").find((segment).value.startsWith).fold(segment)(e => (segment).value.drop(e.length))
    Subprocess.value(body)

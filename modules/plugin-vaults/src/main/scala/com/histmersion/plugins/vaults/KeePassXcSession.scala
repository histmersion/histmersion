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
  type Command = Command.T
  object Command extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Entry = Entry.T
  object Entry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prompt = Prompt.T
  object Prompt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Segment = Segment.T
  object Segment extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegmentFrom = SegmentFrom.T
  object SegmentFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: the prompt the session prints once the database is open and after each answer (`<name>> `, the database's
    * name or file name) — the text after the last line break, once stdout ends in `> `.
    */
  def prompt(out: com.histmersion.plugin.Processes.Session.OutResult): Option[com.histmersion.plugins.vaults.KeePassXcSession.Prompt] =
    Option.when(out.value.endsWith("> "))(Prompt(out.value.substring(out.value.lastIndexOf('\n') + 1)))

  /** Pure: can the entry and the attribute be sent as arguments of an interactive line? A line break would end the
    * line.
    */
  def sendable(entry: com.histmersion.plugins.vaults.KeePassXcSession.Entry, attribute: com.histmersion.plugins.vaults.Vaults.Field): Boolean =
    !entry.value.exists(_.isControl) && !attribute.value.exists(_.isControl)

  /** Pure: an argument for the interactive mode's own splitting, which reads a backslash as "the next character as it
    * is": every space, tab, quote and backslash is escaped.
    */
  def escape(s: String): String =
    s.flatMap(c => if c == ' ' || c == '\t' || c == '"' || c == '\\' then s"\\$c" else c.toString)

  /** Pure: the line that asks for one attribute of one entry. */
  def command(attribute: com.histmersion.plugins.vaults.Vaults.Field, entry: com.histmersion.plugins.vaults.KeePassXcSession.Entry): com.histmersion.plugins.vaults.KeePassXcSession.Command =
    Command(s"show -q -a ${escape(attribute.value)} ${escape(entry.value)}")

  /** Pure: what the session printed for the request sent when its output was `from` characters long, once it has
    * printed its prompt again: the text in between (`None` while it has not).
    */
  def segment(out: com.histmersion.plugin.Processes.Session.OutResult, from: com.histmersion.plugins.vaults.KeePassXcSession.SegmentFrom, prompt: com.histmersion.plugins.vaults.KeePassXcSession.Prompt): Option[com.histmersion.plugins.vaults.KeePassXcSession.Segment] =
    Option
      .when(out.value.length >= from.value + prompt.value.length && out.value.endsWith(prompt.value))(Segment(out.value.substring(from.value, out.value.length - prompt.value.length)))

  /** Pure: the value in a segment — without the echo of the request line (a session reading through readline prints it
    * back) and without the line break the value ends with.
    */
  def answer(segment: com.histmersion.plugins.vaults.KeePassXcSession.Segment, command: com.histmersion.plugins.vaults.KeePassXcSession.Command): com.histmersion.plugin.Secret.Vault.ResolveResultPart2 =
    val body =
      List(command.value + "\r\n", command.value + "\n").find(segment.value.startsWith).fold(segment.value)(e => segment.value.drop(e.length))
    com.histmersion.plugin.Secret.Vault.ResolveResultPart2(Subprocess.value(Subprocess.ValueOut(body)).value)

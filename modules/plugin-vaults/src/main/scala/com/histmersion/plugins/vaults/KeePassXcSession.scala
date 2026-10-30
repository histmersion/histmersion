package com.histmersion.plugins.vaults

import scala.annotation.tailrec
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

import scala.concurrent.duration.*

/** One `keepassxc-cli open` session answering several references (KeePassXC's interactive mode, in keepassxc-cli since
  * 2.6): the database is unlocked once — one password, one YubiKey touch — and each reference is one
  * `show -q -a <attribute> <entry>` line on its stdin. What the session prints is read here; all pure.
  */
object KeePassXcSession:

  /** Pure: the prompt the session prints once the database is open and after each answer (`<name>> `, the database's
    * name or file name) — the text after the last line break, once stdout ends in `> `.
    */
  def prompt(out: String): Option[String] =
    Option.when(out.endsWith("> "))(out.substring(out.lastIndexOf('\n') + 1))

  /** Pure: can `s` be sent as one argument of an interactive line? A line break would end the line. */
  def sendable(s: String): Boolean = !s.exists(_.isControl)

  /** Pure: an argument for the interactive mode's own splitting, which reads a backslash as "the next character as it
    * is": every space, tab, quote and backslash is escaped.
    */
  def escape(s: String): String =
    s.flatMap(c => if c == ' ' || c == '\t' || c == '"' || c == '\\' then s"\\$c" else c.toString)

  /** Pure: the line that asks for one attribute of one entry. */
  def command(attribute: String, entry: String): String = s"show -q -a ${escape(attribute)} ${escape(entry)}"

  /** Pure: what the session printed for the request sent when its output was `from` characters long, once it has
    * printed its prompt again: the text in between (`None` while it has not).
    */
  def segment(out: String, from: Int, prompt: String): Option[String] =
    Option
      .when(out.length >= from + prompt.length && out.endsWith(prompt))(out.substring(from, out.length - prompt.length))

  /** Pure: the value in a segment — without the echo of the request line (a session reading through readline prints it
    * back) and without the line break the value ends with.
    */
  def answer(segment: String, command: String): String =
    val body =
      List(command + "\r\n", command + "\n").find(segment.startsWith).fold(segment)(e => segment.drop(e.length))
    Subprocess.value(body)

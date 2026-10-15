package com.histmersion.server.agents

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

/** One LLM step's prompt. `system` is the step's prompt FILE(S) (bundled as resources, so one copy per
  * prompt and its hash recorded on every attempt); `user` carries the data. */
final case class Prompt(system: String, user: String):
  /** sha256 of the system prompt — which prompt version produced a checkpoint. */
  def hash: String = Prompt.sha256(system)

object Prompt:
  /** Untrusted text (an OCR'd body, an article under review) as DATA: fenced in tags the system prompt
    * names, with any copy of the closing tag inside it defused so the text cannot end its own fence. */
  def data(tag: String, text: String): String =
    val safe = text.replace(s"</$tag>", s"</ $tag>")
    s"<$tag>\n$safe\n</$tag>"

  /** The person's notes for a run (manager hints, docs/research-process-managers.md §3.6), as the block
    * appended to a step's USER message: fenced like data, and subordinate to the record. Empty for none. */
  def notes(ns: List[String]): String =
    val kept = ns.map(_.trim).filter(_.nonEmpty)
    if kept.isEmpty then ""
    else "\n\nNotes from the person who runs this library (follow them unless they contradict the record):\n" +
      data("operator-notes", kept.map(n => s"- $n").mkString("\n"))

  /** The most of a rejected answer a retry carries back. */
  val MaxPreviousAnswer = 60000

  /** Appended to a retried attempt: what code rejected last time. With the rejected answer itself
    * (`previous`, when it was well-formed and only failed a check), the model amends it instead of
    * redoing every lookup — a fixer that started over ran out of turns on its third attempt. */
  def retry(problems: List[String], previous: Option[String] = None): String =
    val points = problems.take(20).map(p => s"- $p").mkString("\n")
    previous.filter(_.length <= MaxPreviousAnswer) match
      case None =>
        "\n\nYour previous answer was rejected by validation:\n" + points + "\nAnswer again, fixing every point."
      case Some(prev) =>
        "\n\nYour previous answer, below, was rejected by validation for these points only:\n" + points +
          "\nEverything else in it stands. Do not repeat your lookups: return the same answer with exactly " +
          "these points fixed.\n\n" + data("previous-answer", prev)

  def sha256(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(UTF_8)).map(b => f"$b%02x").mkString

/** The prompt files, read from `agents/` on the classpath (bundled into the native image), each one
  * overridable by a file at the same relative path under `agents.prompts-dir` — so a prompt can be
  * iterated on without a rebuild. The override is read on every use; the hash recorded with each
  * attempt says which text produced it.
  *
  * The tree (docs/research-agent-runtimes.md §2.1) is the repo's, copied in by the build:
  *   - `<name>.md` — the canonical agent definitions (`agents/` at the repo root);
  *   - `_shared/<fragment>.md` — what definitions `include:` (the review agents' "running inside the
  *     server" notes; `writing-guide.md` is `docs/writing-guide.md`);
  *   - `skills/<name>/SKILL.md` — the canonical skills. */
final class Prompts(overrideDir: String):

  private val dir: Option[Path] =
    Option(overrideDir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  /** `rel` (relative to `agents/`), from the override directory if it has it, else the classpath. A
    * path that would leave the override directory is not looked up there. */
  def load(rel: String): Either[String, String] =
    val local = dir.map(d => (d, d.resolve(rel).normalize())).collect { case (d, p) if p.startsWith(d) => p }
    local.filter(Files.isRegularFile(_)) match
      case Some(p) =>
        try Right(Files.readString(p, UTF_8))
        catch case e: java.io.IOException => Left(s"prompt override $p cannot be read: ${e.getMessage}")
      case None =>
        val path = s"agents/$rel"
        Option(getClass.getClassLoader.getResourceAsStream(path))
          .toRight(s"missing prompt resource $path")
          .map(in => try String(in.readAllBytes(), UTF_8) finally in.close())

object Prompts:
  /** Every file under `agents/` the server reads — the definitions, the shared fragments and the
    * skills, as the build's `agents/index.txt` lists them — which the native image must bundle (a
    * test holds the metadata to it). */
  def bundled: List[String] = AgentDefinitions(Prompts("")).index

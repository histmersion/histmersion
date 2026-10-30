package com.histmersion.server.agents

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

/** The prompt files, read from `agents/` on the classpath (bundled into the native image), each one overridable by a
  * file at the same relative path under `agents.prompts-dir` — so a prompt can be iterated on without a rebuild. The
  * override is read on every use; the hash recorded with each attempt says which text produced it.
  *
  * The tree (docs/research-agent-runtimes.md §2.1) is the repo's, copied in by the build:
  *   - `<name>.md` — the canonical agent definitions (`agents/` at the repo root);
  *   - `_shared/<fragment>.md` — what definitions `include:` (the review agents' "running inside the server" notes;
  *     `writing-guide.md` is `docs/writing-guide.md`);
  *   - `skills/<name>/SKILL.md` — the canonical skills.
  */
final class Prompts(overrideDir: String):

  private val dir: Option[Path] =
    Option(overrideDir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).toAbsolutePath.normalize())

  /** `rel` (relative to `agents/`), from the override directory if it has it, else the classpath. A path that would
    * leave the override directory is not looked up there.
    */
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
          .map(in =>
            try String(in.readAllBytes(), UTF_8)
            finally in.close()
          )

object Prompts:
  /** Every file under `agents/` the server reads — the definitions, the shared fragments and the skills, as the build's
    * `agents/index.txt` lists them — which the native image must bundle (a test holds the metadata to it).
    */
  def bundled: List[String] = AgentDefinitions(Prompts("")).index

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

  /** Appended to a retried attempt: what code rejected last time. */
  def retry(problems: List[String]): String =
    "\n\nYour previous answer was rejected by validation:\n" + problems.take(20).map(p => s"- $p").mkString("\n") +
      "\nAnswer again, fixing every point."

  def sha256(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(UTF_8)).map(b => f"$b%02x").mkString

  /** A Claude Code agent definition without its YAML frontmatter (`---` … `---`): the body is the
    * prompt; the frontmatter (name, model, tools) is the interactive session's business. */
  def stripFrontmatter(md: String): String =
    val t = md.replace("\r\n", "\n")
    if !t.startsWith("---\n") then t.trim
    else
      t.indexOf("\n---\n", 3) match
        case -1 => t.trim
        case i  => t.substring(i + 5).trim

/** The prompt files, read from `agents/` on the classpath (bundled into the native image), each one
  * overridable by a file at the same relative path under `agents.prompts-dir` — so a prompt can be
  * iterated on without a rebuild. The override is read on every use; the hash recorded with each
  * attempt says which text produced it.
  *
  * The tree (docs/research-embedded-agents.md §3.1, §13):
  *   - `article-*.md` — the four review agents, ONE copy shared with the Claude Code plugin
  *     (`agents/` at the repo root; the build copies them in);
  *   - `writing-guide.md` — `docs/writing-guide.md`, copied in the same way;
  *   - `prompts/<pipeline>/<step>.md` — the server's own step prompts and the review agents'
  *     "running inside the server" notes. */
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

  /** A step prompt of a pipeline. */
  def step(pipeline: String, step: String): Either[String, String] = load(s"prompts/$pipeline/$step.md")

  /** A review agent's prompt: its shared definition, frontmatter removed. */
  def agent(name: String): Either[String, String] = load(s"$name.md").map(Prompt.stripFrontmatter)

object Prompts:
  /** Every prompt resource the server reads — what the native image must bundle (a test holds the
    * metadata to it). */
  val bundled: List[String] =
    List("article-rules-reviewer.md", "article-cold-reader.md", "article-skeptic.md", "article-fixer.md",
      "writing-guide.md") ++
      List("classify", "findings", "notes").map(s => s"prompts/enrich-source/$s.md") ++
      List("common", "rules", "cold-reader", "skeptic", "fixer").map(s => s"prompts/review-article/$s.md")

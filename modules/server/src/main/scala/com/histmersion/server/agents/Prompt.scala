package com.histmersion.server.agents

/** One LLM step's prompt. `system` is the step's prompt FILE (bundled as a resource, so one copy per
  * prompt and its hash recorded on every attempt); `user` carries the data. */
final case class Prompt(system: String, user: String):
  /** sha256 of the system prompt — which prompt version produced a checkpoint. */
  def hash: String = Prompt.sha256(system)

object Prompt:
  /** `agents/prompts/<pipeline>/<step>.md` from the classpath (bundled into the native image). */
  def resource(pipeline: String, step: String): Either[String, String] =
    val path = s"agents/prompts/$pipeline/$step.md"
    Option(getClass.getClassLoader.getResourceAsStream(path))
      .toRight(s"missing prompt resource $path")
      .map(in => try String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) finally in.close())

  /** Untrusted text (an OCR'd body) as DATA: fenced in tags the system prompt names, with any copy
    * of the closing tag inside it defused so the text cannot end its own fence. */
  def data(tag: String, text: String): String =
    val safe = text.replace(s"</$tag>", s"</ $tag>")
    s"<$tag>\n$safe\n</$tag>"

  /** Appended to a retried attempt: what code rejected last time. */
  def retry(problems: List[String]): String =
    "\n\nYour previous answer was rejected by validation:\n" + problems.take(20).map(p => s"- $p").mkString("\n") +
      "\nAnswer again, fixing every point."

  def sha256(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
      .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"$b%02x").mkString

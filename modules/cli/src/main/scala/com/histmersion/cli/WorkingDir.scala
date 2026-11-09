package com.histmersion
package cli

/** The CLI started in a directory it may not read (#901). On macOS, privacy protection (TCC) denies a native binary its
  * working directory under `~/Documents`, `~/Desktop` or `~/Downloads` until the terminal is granted Files and Folders
  * access; a deleted working directory fails the same way. The native image then throws
  * `Properties init: Could not determine current working directory` on the first read of `user.dir` — a stack trace
  * that says nothing about the fix. This turns it into one plain message. The property is read lazily, by the commands
  * that resolve a local path, so `Cli` catches the failure rather than probing up front (which would fail every command
  * there). Pure.
  */
object WorkingDir:
  /** What GraalVM's `SystemPropertiesSupport` says when `getcwd` fails. */

  private val Marker = "Could not determine current working directory"

  val message: String =
    "error: histmersion cannot read the directory it was started in.\n" +
      "  On macOS this is privacy protection: the folder (e.g. under ~/Documents) is off limits to this terminal.\n" +
      "  Either grant the terminal access in System Settings → Privacy & Security → Files and Folders\n" +
      "  (Documents Folder, or the folder in question), or run histmersion from another directory, e.g. `cd ~`."

  /** Pure: is `t`, or anything in its cause chain, the working-directory failure? */
  def denied(t: Throwable): Boolean =
    Iterator
      .iterate(t)(_.getCause)
      .takeWhile(_ != null)
      .take(16)
      .exists(e => Option(e.getMessage).exists(_.contains(Marker)))

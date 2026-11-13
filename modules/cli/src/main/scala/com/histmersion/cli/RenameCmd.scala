package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.library.SourcesMarkdown

import com.histmersion.api.library.Citekey

import com.histmersion.config.*

import java.nio.file.{Files, Path}
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Source
import com.histmersion.util.console.Console

/** `histmersion rename <old> <new>` — give a source a new citekey (`POST /api/sources/{old}/rename`). The server judges
  * the new key and answers why it refuses one.
  */
object RenameCmd:
  final case class Args(oldCitekey: Citekey, newCitekey: Citekey, json: Cli.JsonOutput) extends Cli.Command:
    def run(c: Client)(using Console): Either[Cli.Error, Unit] =
      c.renameSource(oldCitekey, newCitekey).map(r => Output.emit(json, r)(SourcesMarkdown.sourceRenamed))

  val Help: String =
    s"Give a source a new citekey — findings, links in articles/notes and every table follow. ${Aliases.rule(AliasKinds.SourceRecords)}"

  private def plainName(name: String): Boolean =
    name.nonEmpty && !name.exists(c => c == '/' || c == '\\') && !name.startsWith(".")

  /** Remove `<sources>/<alias>.md` for every old citekey — the mirror file a renamed source leaves behind.
    * `Right("old → new")` per file removed, `Left(reason)` per file that could not be; an alias with no file is
    * neither. Only ever touches `<alias>.md` directly inside `sources`.
    */
  def pruneRenamed(sources: Path, aliases: List[Source.Alias]): List[Either[String, String]] =
    aliases.filter(a => plainName(a.alias.value)).flatMap { a =>
      val f = sources.resolve(s"${a.alias}.md")
      scala.util
        .Try(Files.deleteIfExists(f))
        .fold(e => Some(Left(s"$f: ${e.getMessage}")), gone => Option.when(gone)(Right(s"${a.alias} → ${a.citekey}")))
    }

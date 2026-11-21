package com.histmersion
package server
package comments

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try

final case class CommentScope(
    excludedRoots: List[String],
    excludedDirectories: List[String],
    exemptRoots: List[(String, String)],
    exemptDirectories: List[(String, String)],
    exemptFixtures: String
):
  import CommentScope.*

  private def under(path: String, directory: String): Boolean =
    path.startsWith(s"$directory/") || path.contains(s"/$directory/")

  def includes(path: String): Boolean =
    !excludedRoots.exists(root => path.startsWith(s"$root/")) && !excludedDirectories.exists(under(path, _))

  def candidate(path: String): Boolean =
    val known = SourceLanguage.of(path, "").isDefined
    val extensionless = !path.substring(path.lastIndexOf('/') + 1).contains('.')
    includes(path) && (known || extensionless)

  def exemption(path: String, language: SourceLanguage): Option[String] =
    exemptRoots
      .collectFirst { case (root, why) if path.startsWith(s"$root/") => why }
      .orElse(exemptDirectories.collectFirst { case (directory, why) if under(path, directory) => why })
      .orElse(Option.when(language == SourceLanguage.Html && path.contains("/src/test/resources/"))(exemptFixtures))

  def file(root: Path, path: String): Option[CommentedFile] =
    val absolute = root.resolve(path)
    if !includes(path) || !Files.isRegularFile(absolute) || Files.isSymbolicLink(absolute) then None
    else Try(new String(Files.readAllBytes(absolute), UTF_8)).toOption.flatMap(parsed(path, _))

  def parsed(path: String, text: String): Option[CommentedFile] =
    Option
      .when(includes(path))(path)
      .flatMap(p => SourceLanguage.of(p, text.linesIterator.nextOption().getOrElse("")))
      .map(language => CommentedFile(path, language, text, exemption(path, language)))

  def files(root: Path): Vector[CommentedFile] =
    listed(root).flatMap(file(root, _))

object CommentScope:
  val default: CommentScope = CommentScope(
    excludedRoots = List("refactor-audit", "plugin", ".claude-plugin", "docs/user-guide", ".opencode"),
    excludedDirectories = List("target", "node_modules"),
    exemptRoots = List("modules/db/src/main/resources/db/migration" -> "applied Flyway migration (migrations.tsv)"),
    exemptDirectories = List("vendor" -> "vendored third-party code"),
    exemptFixtures = "test fixture page (data, not code)"
  )

  def listed(root: Path): Vector[String] =
    val process = new ProcessBuilder(
      List("git", "-C", root.toString, "ls-files", "--cached", "--others", "--exclude-standard").asJava
    ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val out = new String(process.getInputStream.readAllBytes(), UTF_8)
    if process.waitFor() == 0 then out.linesIterator.filter(_.nonEmpty).toVector.distinct.sorted else Vector.empty

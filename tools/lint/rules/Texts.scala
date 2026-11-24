package com.histmersion.linting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

final case class Violation(rule: String, file: String, detail: String, count: Int, at: String, offsets: List[Int]):
  def row: String = s"$rule\t$file\t$detail\t$count"

final case class SourceFile(key: String, pkg: String, code: String, raw: String):
  def lineOf(index: Int): Int = code.substring(0, index).count(_ == '\n') + 1

  def platformless: String =
    key.split(':').toList match
      case module :: _ :: path :: Nil => s"$module:$path"
      case _                          => key

object SourceFile:
  val PlatformRoots: Set[String] = Set("scalajvm", "scalajs")

  def key(module: String, root: String, path: String): String =
    if PlatformRoots(root) then s"$module:$root:$path" else s"$module:$path"

  def of(key: String, text: String): SourceFile =
    val pkg = "(?m)^package ([\\w.]+)".r.findAllMatchIn(text).map(_.group(1)).mkString(".")
    SourceFile(key, pkg, Blank(text), text)

  def all(module: String, scope: String = "main", modules: Path = Path.of("modules")): List[SourceFile] =
    val main = modules.resolve(module).resolve("src").resolve(scope)
    val prefix = if scope == "main" then "" else s"$scope/"
    if !Files.isDirectory(main) then Nil
    else
      Files
        .list(main)
        .iterator
        .asScala
        .toList
        .filter(_.getFileName.toString.startsWith("scala"))
        .flatMap { root =>
          val base = root.resolve("com/histmersion")
          if !Files.isDirectory(base) then Nil
          else
            Files.walk(base).iterator.asScala.filter(_.toString.endsWith(".scala")).toList.map { p =>
              of(
                key(module, root.getFileName.toString, s"$prefix${base.relativize(p).toString.replace('\\', '/')}"),
                Files.readString(p, UTF_8)
              )
            }
        }
        .sortBy(_.key)

object Blank:
  def apply(text: String): String =
    val out = new StringBuilder(text.length)
    val n = text.length
    def keep(c: Char): Char = if c == '\n' then '\n' else ' '
    def blankTo(stop: Int, from: Int): Int =
      var i = from
      while i < stop do { out += keep(text(i)); i += 1 }
      i
    def code(from: Int, splice: Boolean): Int =
      var i = from
      var depth = 0
      var done = false
      while i < n && !done do
        val c = text(i)
        if text.startsWith("//", i) then while i < n && text(i) != '\n' do { out += ' '; i += 1 }
        else if text.startsWith("/*", i) then
          var nested = 0
          while i < n && (nested > 0 || text.startsWith("/*", i)) do
            if text.startsWith("/*", i) then { nested += 1; out ++= "  "; i += 2 }
            else if text.startsWith("*/", i) then { nested -= 1; out ++= "  "; i += 2 }
            else { out += keep(text(i)); i += 1 }
        else if c == '"' then i = string(i)
        else if c == '\'' && i + 2 < n && (text(i + 2) == '\'' || text(i + 1) == '\\') then
          val end = text.indexOf('\'', i + 2)
          i = blankTo(if end < 0 || end - i > 8 then i + 1 else end + 1, i)
        else if splice && c == '}' && depth == 0 then done = true
        else
          if splice && c == '{' then depth += 1
          else if splice && c == '}' then depth -= 1
          out += c
          i += 1
      i
    def string(from: Int): Int =
      val interpolated = from > 0 && (text(from - 1).isLetterOrDigit || text(from - 1) == '_')
      val triple = text.startsWith("\"\"\"", from)
      var i = blankTo(from + (if triple then 3 else 1), from)
      var done = false
      while i < n && !done do
        val c = text(i)
        if triple && text.startsWith("\"\"\"", i) then
          i = blankTo(i + 3, i)
          while i < n && text(i) == '"' do { out += ' '; i += 1 }
          done = true
        else if !triple && (c == '"' || c == '\n') then
          if c == '"' then { out += ' '; i += 1 }
          done = true
        else if interpolated && text.startsWith("$$", i) then i = blankTo(i + 2, i)
        else if interpolated && text.startsWith("${", i) then
          i = code(blankTo(i + 2, i), splice = true)
          if i < n then i = blankTo(i + 1, i)
        else if !triple && c == '\\' && i + 1 < n && text(i + 1) != '\n' then i = blankTo(i + 2, i)
        else { out += keep(c); i += 1 }
      i
    val _ = code(0, splice = false)
    out.toString

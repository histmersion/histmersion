package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

final case class SourceFile(key: String, code: String)

object SourceFile:
  val PlatformRoots: Set[String] = Set("scalajvm", "scalajs")

  def key(module: String, root: String, path: String): String =
    if PlatformRoots(root) then s"$module:$root:$path" else s"$module:$path"

  def all(module: String, modules: Path = Path.of("modules")): List[SourceFile] =
    val main = modules.resolve(module).resolve("src").resolve("main")
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
              val text = Files.readString(p, UTF_8)
              SourceFile(
                key(module, root.getFileName.toString, base.relativize(p).toString.replace('\\', '/')),
                blank(text)
              )
            }
        }
        .sortBy(_.key)

  def blank(text: String): String =
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

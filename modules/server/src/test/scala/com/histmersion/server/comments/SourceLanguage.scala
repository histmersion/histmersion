package com.histmersion
package server
package comments

enum SourceLanguage derives CanEqual:
  case Scala, Java, JavaScript, Css, Html, Python, Shell, Sql

  def label: String = toString.toLowerCase

  def comments(text: String): Vector[SourceComment] = CommentLexer(text, this).comments

  def lineComment(comment: String): Boolean =
    this match
      case Scala | Java | JavaScript => comment.startsWith("//")
      case Python | Shell            => comment.startsWith("#")
      case Sql                       => comment.startsWith("--")
      case Css | Html                => false

  def directive(comment: String): Boolean =
    val body = comment.stripLeading
    body.startsWith("#!") || (this match
      case Scala | Java =>
        SourceLanguage.cFamilyDirectives.exists(d => body.startsWith(s"//$d") || body.startsWith(s"/*$d")) ||
        SourceLanguage.cFamilyDirectives.exists(d => body.startsWith(s"// $d") || body.startsWith(s"/* $d"))
      case JavaScript =>
        body.startsWith("/*!") ||
        SourceLanguage.scriptDirectives.exists(d => body.startsWith(s"// $d") || body.startsWith(s"/* $d"))
      case Css    => body.startsWith("/*!") || body.startsWith("/* stylelint-")
      case Html   => body.startsWith("<!-- prettier-ignore") || body.startsWith("<!--[if")
      case Python => SourceLanguage.pythonDirectives.exists(d => body.startsWith(s"# $d") || body.startsWith(s"#$d"))
      case Shell  => body.startsWith("# shellcheck ")
      case Sql    => false)

object SourceLanguage:
  val cFamilyDirectives: List[String] =
    List("> using", "scalafmt:", "format: off", "format: on", "scalafix:", "nowarn", "noinspection", "$COVERAGE-")

  val scriptDirectives: List[String] =
    List("eslint", "@ts-", "global ", "prettier-ignore", "@vite-ignore", "webpackChunkName", "jshint")

  val pythonDirectives: List[String] =
    List("type:", "noqa", "pragma", "-*-", "fmt:", "pylint:", "/// script", "///")

  def of(path: String, firstLine: String): Option[SourceLanguage] =
    val name = path.substring(path.lastIndexOf('/') + 1)
    val dot = name.lastIndexOf('.')
    val extension = if dot < 0 then "" else name.substring(dot + 1)
    extension match
      case "scala" | "sbt"                                                  => Some(Scala)
      case "java"                                                           => Some(Java)
      case "js" | "mjs" | "cjs"                                             => Some(JavaScript)
      case "css"                                                            => Some(Css)
      case "html"                                                           => Some(Html)
      case "py"                                                             => Some(Python)
      case "sh" | "bash"                                                    => Some(Shell)
      case "sql"                                                            => Some(Sql)
      case "" if firstLine.startsWith("#!") && firstLine.contains("python") => Some(Python)
      case "" if firstLine.startsWith("#!") && firstLine.contains("sh")     => Some(Shell)
      case _                                                                => None

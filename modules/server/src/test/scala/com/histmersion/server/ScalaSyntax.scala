package com.histmersion
package server

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers.Parser
import dotty.tools.dotc.reporting.StoreReporter
import dotty.tools.dotc.util.SourceFile as Source

final class ScalaSyntax:
  private val root: Context =
    val initial = (new ContextBase).initialCtx.fresh
    initial.setSetting(initial.settings.source, "future")

  def parse(path: String, code: String): Either[String, untpd.Tree] = synchronized {
    val source = Source.virtual(path, code)
    val reporter = new StoreReporter()
    given Context = root.fresh.setReporter(reporter).setSource(source)
    val tree = new Parser(source).parse()
    if reporter.hasErrors then
      Left(reporter.removeBufferedMessages.map(d => s"$path:${d.pos.line + 1}: ${d.message}").mkString("\n"))
    else Right(tree)
  }

  def unwraps(path: String, code: String): Either[String, List[Int]] =
    parse(path, code).map { tree =>
      val nodes = ScalaSyntax.nodes(tree)
      val bodies = nodes.collect {
        case m: untpd.ModuleDef if ScalaSyntax.parentNames(m.impl).exists(ScalaSyntax.NewtypeParents) =>
          m.span.start -> m.span.end
      }
      nodes
        .collect { case s @ untpd.Select(_, name) if name.toString == "value" && s.span.exists => s.span.point }
        .filterNot(at => bodies.exists((from, to) => at >= from && at < to))
        .sorted
    }

object ScalaSyntax:
  val NewtypeParents: Set[String] = Set("Newtype", "RefinedType", "RefinedSubtype")

  def nodes(tree: untpd.Tree): List[untpd.Tree] =
    def walk(node: Matchable): List[untpd.Tree] = node match
      case t: untpd.Tree => t :: elements(t).flatMap(walk)
      case xs: List[?]   => xs.map(_.asInstanceOf[Matchable]).flatMap(walk)
      case _             => Nil
    walk(tree)

  def parentNames(template: untpd.Template): List[String] =
    elements(template)
      .collectFirst { case parents: List[?] => parents }
      .toList
      .flatten
      .map(_.asInstanceOf[Matchable])
      .collect { case parent: untpd.Tree => nameOf(parent) }

  private def elements(node: Product): List[Matchable] = node.productIterator.map(_.asInstanceOf[Matchable]).toList

  def nameOf(parent: untpd.Tree): String = parent match
    case untpd.AppliedTypeTree(tpt, _)   => nameOf(tpt)
    case untpd.Apply(fun, _)             => nameOf(fun)
    case untpd.Select(untpd.New(tpt), _) => nameOf(tpt)
    case untpd.New(tpt)                  => nameOf(tpt)
    case untpd.Select(_, name)           => name.toString
    case untpd.Ident(name)               => name.toString
    case _                               => ""

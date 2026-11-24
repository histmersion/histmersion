package com.histmersion.linting

import scala.meta.*
import scalafix.lint.LintSeverity
import scalafix.v1.*

final case class Finding(rule: String, pos: Position, message: String)

final case class Mark(rule: String, owner: Tree, annotation: Mod.Annot, topLevel: Boolean)

object Tolerance:

  val FileScoped: Set[String] =
    Set("Placement", "Direction", "Layer", "FileSystem", "Process", "Repository", "Bucket", "G47Package")

  def modsOf(t: Tree): List[Mod] = t match
    case d: Defn.Def        => d.mods
    case d: Defn.Val        => d.mods
    case d: Defn.Var        => d.mods
    case d: Defn.Class      => d.mods
    case d: Defn.Trait      => d.mods
    case d: Defn.Object     => d.mods
    case d: Defn.Enum       => d.mods
    case d: Defn.EnumCase   => d.mods
    case d: Defn.Type       => d.mods
    case d: Defn.Given      => d.mods
    case d: Defn.GivenAlias => d.mods
    case d: Decl.Def        => d.mods
    case d: Decl.Val        => d.mods
    case d: Decl.Var        => d.mods
    case d: Decl.Type       => d.mods
    case d: Pkg.Object      => d.mods
    case _                  => Nil

  def isDefinition(t: Tree): Boolean = t match
    case _: Defn.Def | _: Defn.Val | _: Defn.Var | _: Defn.Class | _: Defn.Trait | _: Defn.Object | _: Defn.Enum |
        _: Defn.EnumCase | _: Defn.Type | _: Defn.Given | _: Defn.GivenAlias | _: Decl.Def | _: Decl.Val |
        _: Decl.Var | _: Decl.Type | _: Pkg.Object =>
      true
    case _ => false

  def isTopLevel(t: Tree): Boolean = t.parent.exists {
    case p @ (_: Pkg.Body | _: Pkg | _: Source) => true
    case e: Defn.ExtensionGroup                 => isTopLevel(e)
    case b: Term.Block                          => b.parent.exists(e => e.is[Defn.ExtensionGroup] && isTopLevel(e))
    case _                                      => false
  }

  def nonLocal(t: Tree): Boolean = t.parent.exists {
    case _: Pkg.Body | _: Pkg | _: Source | _: Template.Body | _: Template => true
    case b: Term.Block                                                      => b.parent.exists(_.is[Defn.ExtensionGroup])
    case _: Defn.ExtensionGroup                                             => true
    case _                                                                  => false
  }

  def annotationName(a: Mod.Annot): String = a.init.tpe match
    case Type.Select(_, n) => n.value
    case n: Type.Name      => n.value
    case other             => other.syntax

  def arguments(a: Mod.Annot): List[Term] = a.init.argClauses.toList.flatMap(_.values)

  def ruleOf(a: Mod.Annot): Option[String] =
    if annotationName(a) != "tolerated" then None
    else
      arguments(a).headOption.collect { case Term.Select(Term.Name("LintRule"), Term.Name(rule)) => rule }

  def marks(tree: Tree): List[Mark] =
    tree.collect {
      case d if isDefinition(d) =>
        modsOf(d).collect {
          case a: Mod.Annot if ruleOf(a).nonEmpty => Mark(ruleOf(a).get, d, a, isTopLevel(d))
          case a: Mod.Annot if annotationName(a) == "movesTo" => Mark("Placement", d, a, isTopLevel(d))
        }
    }.flatten

  def topLevelDefinitions(tree: Tree): List[Tree] = tree.collect { case d if isDefinition(d) && isTopLevel(d) => d }

  def encloses(outer: Tree, pos: Position): Boolean = outer.pos.start <= pos.start && pos.end <= outer.pos.end

  def definitions(tree: Tree): List[Tree] = tree.collect { case d if isDefinition(d) => d }

  def outside(f: Finding, defs: List[Tree]): Boolean = !defs.exists(d => encloses(d, f.pos))

  def covered(f: Finding, marks: List[Mark], defs: List[Tree]): Boolean =
    if FileScoped(f.rule) || outside(f, defs) then marks.exists(m => m.rule == f.rule && m.topLevel)
    else marks.exists(m => m.rule == f.rule && encloses(m.owner, f.pos))

  def open(findings: List[Finding], tree: Tree): List[Finding] =
    val marks = Tolerance.marks(tree)
    val defs = definitions(tree)
    findings.filterNot(covered(_, marks, defs))

  def unused(findings: List[Finding], marks: List[Mark], owned: Set[String], defs: List[Tree]): List[Finding] =
    marks.filter(m => owned(m.rule)).flatMap { m =>
      val of = findings.filter(_.rule == m.rule)
      if FileScoped(m.rule) then
        if !m.topLevel then
          List(Finding(m.rule, m.annotation.pos, s"${m.rule} is a file's rule: it is tolerated on a top-level definition"))
        else if of.isEmpty then List(Finding(m.rule, m.annotation.pos, s"unused: no ${m.rule} finding in this file"))
        else
          val first = marks.filter(o => o.rule == m.rule && o.topLevel).minBy(_.annotation.pos.start)
          if first eq m then Nil
          else List(Finding(m.rule, m.annotation.pos, s"${m.rule} is tolerated once per file"))
      else if of.exists(f => encloses(m.owner, f.pos) || (m.topLevel && outside(f, defs))) then Nil
      else List(Finding(m.rule, m.annotation.pos, s"unused: no ${m.rule} finding in this definition"))
    }

  def diagnostics(findings: List[Finding], tree: Tree, owned: Set[String]): List[Finding] =
    val marks = Tolerance.marks(tree)
    val defs = definitions(tree)
    findings
      .filterNot(covered(_, marks, defs))
      .map(f => f.copy(message = s"${f.message} (fix it, or @tolerated(LintRule.${f.rule}, \"<why>\") on its definition)")) ++
      unused(findings, marks, owned, defs)

  def patch(findings: List[Finding]): Patch =
    findings
      .map(f => Patch.lint(Diagnostic(f.rule, f.message, f.pos, severity = LintSeverity.Error)))
      .asPatch

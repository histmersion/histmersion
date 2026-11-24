package com.histmersion.linting

import scala.meta.*

object Shape:

  val Annotations: Set[String] = Set("tolerated", "atBoundary", "movesTo")

  private def literalReason(t: Term): Boolean = t match
    case Lit.String(s) => s.trim.nonEmpty
    case _             => false

  private def enumArgument(t: Term, enumName: String): Boolean = t match
    case Term.Select(Term.Name(`enumName`), _: Term.Name) => true
    case _                                               => false

  def findings(tree: Tree, tokens: Tokens): List[Finding] =
    val onDefinitions = tree.collect {
      case d if Tolerance.isDefinition(d) =>
        Tolerance.modsOf(d).collect {
          case a: Mod.Annot if Annotations(Tolerance.annotationName(a)) => a -> d
        }
    }.flatten
    val onDefinition = onDefinitions.map(_._1).toSet
    val misplaced = tree.collect {
      case a: Mod.Annot if Annotations(Tolerance.annotationName(a)) && !onDefinition(a) =>
        Finding("Tolerated", a.pos, s"@${Tolerance.annotationName(a)} annotates a definition, never an expression or a type")
    }
    val shapes = onDefinitions.flatMap { (a, d) =>
      val args = Tolerance.arguments(a)
      Tolerance.annotationName(a) match
        case "tolerated" =>
          args match
            case List(rule, reason) if enumArgument(rule, "LintRule") && literalReason(reason) => Nil
            case _ =>
              List(Finding("Tolerated", a.pos, "@tolerated(LintRule.<Rule>, \"<why>\"): a rule and a non-blank literal reason"))
        case "atBoundary" =>
          args match
            case List(kind, reason) if enumArgument(kind, "BoundaryKind") && literalReason(reason) && Tolerance.isTopLevel(d) =>
              Nil
            case _ =>
              List(
                Finding(
                  "Tolerated",
                  a.pos,
                  "@atBoundary(BoundaryKind.<Kind>, \"<why>\") on a top-level definition, with a non-blank literal reason"
                )
              )
        case _ =>
          args match
            case List(place) if enumArgument(place, "FeaturePlace") && Tolerance.isTopLevel(d) => Nil
            case _ =>
              List(Finding("Tolerated", a.pos, "@movesTo(FeaturePlace.<Place>) on a top-level definition"))
    }
    val repeated = List("atBoundary", "movesTo").flatMap { name =>
      onDefinitions.filter((a, _) => Tolerance.annotationName(a) == name).drop(1).map { (a, _) =>
        Finding("Tolerated", a.pos, s"@$name is written once per file")
      }
    }
    val renamed = tree.collect {
      case i: Importer if i.ref.syntax.endsWith("histmersion.lint") =>
        i.importees.collect {
          case r: Importee.Rename => Finding("Tolerated", r.pos, "the lint annotations are imported by their own names")
        }
    }.flatten
    val suppressions = tree.collect {
      case a: Mod.Annot if Tolerance.annotationName(a) == "SuppressWarnings" && a.syntax.contains("scalafix") =>
        Finding("Tolerated", a.pos, "a lint finding is tolerated with @tolerated(LintRule.<Rule>, \"<why>\")")
    }
    val switches = tokens.collect {
      case c: Token.Comment if c.value.contains("scalafix:") =>
        Finding("Tolerated", c.pos, "a lint finding is tolerated with @tolerated(LintRule.<Rule>, \"<why>\")")
    }
    misplaced ++ shapes ++ repeated ++ renamed ++ suppressions ++ switches

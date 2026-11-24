package com.histmersion.linting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*
import scalafix.v1.*

object Baseline:

  val Reasons: Map[String, String] = Map(
    "G44" -> "pending G44 (raw value only in the newtype's companion or at a lifted boundary)",
    "G44Conversion" -> "pending G44 (a controller hands the wire value to the domain's parse, not .value)",
    "G44PlainDef" -> "pending G1 (the parameter takes the newtype, not its raw value)",
    "G45Boundary" -> "pending G45 (boundaries lift type classes instead of hand .value)",
    "G3Misplaced" -> "pending G3 (a function about a type lives on the type)",
    "G9Prefix" -> "pending G9 (namespaces, not prefixes)",
    "G9PrefixRole" -> "pending G9 (namespaces, not prefixes)",
    "G46" -> "pending G46 (re-export, don't alias)",
    "G47Root" -> "pending G47 (no FQN noise)",
    "G47Fqn" -> "pending G47 (no FQN noise)",
    "G47Self" -> "pending G47 (no FQN noise)",
    "G47Package" -> "pending G47 (chained package clauses)",
    "G48" -> "pending G48 (schemas from Kindlings annotations)",
    "G5Problem" -> "pending G60 (one typed Problem ADT)",
    "G5ProblemText" -> "pending G60 (a caller branches on the case, never the sentence)",
    "G5TextBranch" -> "pending G60 (a caller branches on a closed enum, never on text)",
    "G55Positional" -> "pending G55 (real concepts, no positional names)",
    "G58Raw" -> "pending G58 (SQL text only through the codecs)",
    "G60FromString" -> "pending G60 (problems built from cases, not text)",
    "G60External" -> "pending G60 (outside text shown only at an edge)",
    "G60Unrecognised" -> "pending G60 (only the stored column's reader builds Unrecognised)",
    "G89Unguarded" -> "pending G89 (every transaction takes a write permit)",
    "BooleanParam" -> "pending ADR 0027 (a state is an enum, not a Boolean)",
    "UnnamedBoolean" -> "pending ADR 0027 (a Boolean argument is passed by name)",
    "SilentCompare" -> "pending G53 (no comparison of unrelated types)",
    "SilentInterp" -> "pending G35 (a vocabulary renders through its wire string)",
    "SilentPure" -> "pending G44 (a Pure newtype's option/either never fails)",
    "Primitive" -> "pending G1 (no primitives in the domain)",
    "Layer" -> "pending G7 (separate types per layer)",
    "ClientRefined" -> "pending G8 (no domain validation in clients)",
    "Concept" -> "pending G5 (one concept, one type within a layer)",
    "Derives" -> "pending G18 (derives instead of hand-written givens)",
    "Copy" -> "pending G19 (lenses instead of .copy)",
    "OneType" -> "pending G21 (one top-level type per file)",
    "Transaction" -> "pending G13 (repositories never own transactions)",
    "Global" -> "pending G15 (no globals; everything from config)",
    "Service" -> "pending G16 (a service is a trait with Implementation and InMemory)",
    "Helper" -> "pending G63 (no free-standing helper objects)",
    "Prefix" -> "pending G9 (namespaces, not prefixes)",
    "Bucket" -> "pending G10 (no layer buckets)",
    "Circe" -> "pending G62 (jsoniter-scala, not circe)",
    "Scallop" -> "pending G62b (scallop only in the command-line core)",
    "NonEmptyPattern" -> "pending G60 (Problems are folded, never matched on)",
    "ProblemField" -> "pending G60 (a problem's fields are newtypes or enums)",
    "Unrecognised" -> "pending G60 (only the stored column's reader builds Unrecognised)",
    "Relayed" -> "pending G60 (only the browser's decoder builds Relayed)",
    "FirstProblem" -> "pending G60 (every problem is kept)",
    "HtmlSink" -> "pending G96 (markup reaches the DOM only through HtmlSink)",
    "HtmlNewtype" -> "pending G96 (one markup type, api.Html)",
    "FailureRequest" -> "pending G105 (a failure never decides another request)",
    "RawRequest" -> "pending G107 (requests through the one Request type)",
    "SchemaConfig" -> "pending ADR 0026 (one JsoniterConfig for schemas and codecs)",
    "Unclassified" -> "pending G60 step 7 (the api's renderer names every case until Unclassified goes)",
    "CaseDefault" -> "pending G64 (a caller states every field)",
    "CanEqualHome" -> "pending G67 (a foreign type's CanEqual in one file; its users share no module yet)",
    "Direction" -> "pending G12 (a domain names only lower places)",
    "FileSystem" -> "pending G23 (files only through util.storage)",
    "Process" -> "pending G39 (processes only through util.process)",
    "Repository" -> "pending G11 (a repository stays inside its feature)",
    "TotalOrder" -> "pending G58 (ORDER BY ends with a unique key)",
    "Placement" -> "pending G38 (package by feature)"
  )

  private def rows(path: String): List[Array[String]] =
    val p = Path.of(path)
    if !Files.isRegularFile(p) then Nil
    else
      Files
        .readAllLines(p, UTF_8)
        .asScala
        .toList
        .filterNot(l => l.isBlank || l.startsWith("#") || l.startsWith("path\t"))
        .map(_.split("\t", -1))

  lazy val textBranchReasons: Map[String, String] =
    rows("architecture/text-branch-exceptions.tsv").collect { case Array(path, why) => path -> why }.toMap

  lazy val totalOrderReasons: Map[(String, String), String] =
    rows("modules/server/src/test/resources/architecture/total-order.tsv").collect {
      case Array(file, clause, why) => (file, clause) -> why
    }.toMap

  lazy val boundaries: Map[String, (String, String)] =
    rows("architecture/boundaries.tsv").collect { case Array(path, kind, why) => path -> (kind, why) }.toMap

  lazy val places: Map[String, String] =
    rows("modules/server/src/test/resources/architecture/places.tsv").collect { case Array(key, place) => key -> place }.toMap

  def kindCase(kind: String): String = kind match
    case "cli-args" => "CliArgs"
    case other      => other.capitalize

  def quoted(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def reasonOf(f: Finding, rel: String, key: Option[String]): String = f.rule match
    case "G5TextBranch" if textBranchReasons.contains(rel) => textBranchReasons(rel)
    case "TotalOrder" =>
      val clause = f.message.split(" — ", 2).lift(1).getOrElse("")
      key.flatMap(k => totalOrderReasons.get(k -> clause)).getOrElse(Reasons("TotalOrder"))
    case other => Reasons.getOrElse(other, s"pending the $other rule")

  def target(f: Finding, tree: Tree): Option[Tree] =
    val top = Tolerance.topLevelDefinitions(tree).headOption
    if Tolerance.FileScoped(f.rule) then top
    else
      tree
        .collect { case d if Tolerance.isDefinition(d) && Tolerance.nonLocal(d) && Tolerance.encloses(d, f.pos) => d }
        .sortBy(d => -d.pos.start)
        .headOption
        .orElse(top)

  def indentOf(d: Tree): String =
    val text = d.pos.input.text
    val lineStart = text.lastIndexOf('\n', d.pos.start - 1) + 1
    val lead = text.substring(lineStart, d.pos.start)
    if lead.forall(_.isWhitespace) then lead else " " * d.pos.startColumn

  def annotate(tree: Tree, annotations: List[(Tree, String)], names: Set[String]): Patch =
    if annotations.isEmpty then Patch.empty
    else
      val inserted = annotations
        .groupBy(_._1)
        .toList
        .map { (d, as) =>
          val indent = indentOf(d)
          Patch.addLeft(d.tokens.head, as.map(_._2).distinct.sorted.map(a => a + "\n" + indent).mkString)
        }
        .asPatch
      val present = tree.collect {
        case i: Importer if i.ref.syntax == "com.histmersion.lint" =>
          i.importees.collect { case Importee.Name(n) => n.value }
      }.flatten.toSet
      val missing = names -- present
      val imported =
        if missing.isEmpty then Patch.empty
        else
          val text = s"import com.histmersion.lint.{${missing.toList.sorted.mkString(", ")}}"
          text.parse[Stat].get match
            case i: Import => Patch.addGlobalImport(i.importers.head)
            case _         => Patch.empty
      inserted + imported

  def tolerations(findings: List[Finding], tree: Tree, rel: String): Patch =
    val key = Project.ref(rel).map(_.key)
    val open = Tolerance.open(findings, tree)
    val annotations = open.flatMap { f =>
      target(f, tree).map(d => d -> s"@tolerated(LintRule.${f.rule}, ${quoted(reasonOf(f, rel, key))})")
    }
    annotate(tree, annotations, Set("LintRule", "tolerated"))

  def files(tree: Tree, rel: String): Patch =
    val top = Tolerance.topLevelDefinitions(tree).headOption
    val key = Project.ref(rel).map(_.key)
    val present = Tolerance.topLevelDefinitions(tree).flatMap(Tolerance.modsOf).collect {
      case a: Mod.Annot => Tolerance.annotationName(a)
    }.toSet
    val boundary = boundaries.get(rel).filterNot(_ => present("atBoundary")).flatMap { (kind, why) =>
      top.map(d => d -> s"@atBoundary(BoundaryKind.${kindCase(kind)}, ${quoted(why)})")
    }
    val placeCase = Project.places.map(_.swap)
    val moves = key.flatMap(places.get).flatMap(placeCase.get).filterNot(_ => present("movesTo")).flatMap { c =>
      top.map(d => d -> s"@movesTo(FeaturePlace.$c)")
    }
    annotate(tree, boundary.toList, Set("BoundaryKind", "atBoundary")) +
      annotate(tree, moves.toList, Set("FeaturePlace", "movesTo"))

final class FileBaseline extends SyntacticRule("FileBaseline"):
  override def fix(using doc: SyntacticDocument): Patch =
    Baseline.files(doc.tree, Project.relativeOf(doc.input))

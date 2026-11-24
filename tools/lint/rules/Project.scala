package com.histmersion.linting

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.meta.*
import scala.meta.internal.semanticdb as s
import scalafix.v1.*

final case class SourceRef(rel: String, module: String, scope: String, root: String, path: String):
  def key: String =
    val prefix = if scope == "main" then "" else s"$scope/"
    if Project.PlatformRoots(root) then s"$module:$root:$prefix$path" else s"$module:$prefix$path"

object Project:

  val PlatformRoots: Set[String] = Set("scalajvm", "scalajs")

  private val Layout = "^modules/(.+?)/src/(main|test|test-fixture)/(scala[^/]*)/com/histmersion/(.+\\.scala)$".r

  def ref(rel: String): Option[SourceRef] = rel match
    case Layout(module, scope, root, path) => Some(SourceRef(rel, module, scope, root, path))
    case _                                 => None

  def relative(p: String): String =
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if abs.startsWith(here) then here.relativize(abs).toString.replace('\\', '/') else p

  def relative(doc: SemanticDocument): String = relativeOf(doc.input)

  def relativeOf(input: Input): String = relative(input match
    case Input.File(p, _)        => p.toString
    case Input.VirtualFile(p, _) => p
    case other                   => other.toString
  )

  final case class Text(ref: SourceRef, raw: String)

  lazy val texts: Vector[Text] =
    val root = Paths.get("modules")
    if !Files.isDirectory(root) then Vector.empty
    else
      val stream = Files.walk(root)
      try
        stream.iterator.asScala
          .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
          .flatMap(p => ref(relative(p.toString)).map(r => Text(r, Files.readString(p, UTF_8))))
          .toVector
          .sortBy(_.ref.rel)
      finally stream.close()

  lazy val byRel: Map[String, Text] = texts.map(t => t.ref.rel -> t).toMap

  private val BoundaryAnnotation = "@atBoundary\\(\\s*BoundaryKind\\.(\\w+)".r
  private val MovesToAnnotation = "@movesTo\\(\\s*FeaturePlace\\.(\\w+)\\s*\\)".r

  def boundaryWord(kind: String): String = kind match
    case "CliArgs" => "cli-args"
    case other     => other.toLowerCase

  def boundaryOf(raw: String): Option[String] =
    BoundaryAnnotation.findFirstMatchIn(raw).map(m => boundaryWord(m.group(1)))

  lazy val places: Map[String, String] =
    val file = Paths.get("modules/lint/src/main/scala/com/histmersion/lint/FeaturePlace.scala")
    if !Files.isRegularFile(file) then Map.empty
    else
      "case (\\w+) extends FeaturePlace\\(\"([\\w.]+)\"\\)".r
        .findAllMatchIn(Files.readString(file, UTF_8))
        .map(m => m.group(1) -> m.group(2))
        .toMap

  def movesToOf(raw: String): Option[String] =
    MovesToAnnotation.findFirstMatchIn(raw).flatMap(m => places.get(m.group(1)))

  final case class Semantic(
      symbols: ConcurrentHashMap[String, s.SymbolInformation],
      definedIn: ConcurrentHashMap[String, String]
  )

  private val semanticCache = new ConcurrentHashMap[String, Semantic]()

  def semantic(scalaVersion: String): Semantic =
    semanticCache.computeIfAbsent(scalaVersion, v => buildSemantic(semanticdbRoots(v)))

  def semanticdbRoots(scalaVersion: String): List[String] =
    sys.env
      .get("LINT_SEMANTICDB_ROOTS")
      .filter(_.nonEmpty)
      .map(_.split(java.io.File.pathSeparatorChar).toList)
      .getOrElse(List(s"target/out/jvm/scala-$scalaVersion", s"target/out/sjs1/scala-$scalaVersion"))

  private def buildSemantic(roots: List[String]): Semantic =
    val index = new ConcurrentHashMap[String, s.SymbolInformation]()
    val defined = new ConcurrentHashMap[String, String]()
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try
        stream.iterator.asScala
          .filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p) && !p.toString.contains("/classes/"))
          .toList
      finally stream.close()
    }
    val md5Cache = new ConcurrentHashMap[String, String]()
    def md5(uri: String): String = md5Cache.computeIfAbsent(
      uri,
      u =>
        val p = Paths.get(u)
        if !Files.isRegularFile(p) then ""
        else
          java.security.MessageDigest
            .getInstance("MD5")
            .digest(Files.readAllBytes(p))
            .map(b => f"${b & 255}%02X")
            .mkString
    )
    files.asJava.parallelStream().forEach { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.foreach { d =>
        if md5(d.uri) == d.md5 then
          d.symbols.foreach { i =>
            index.putIfAbsent(if i.symbol.startsWith("local") then d.uri + "::" + i.symbol else i.symbol, i)
          }
          d.occurrences.foreach { o =>
            if o.role == s.SymbolOccurrence.Role.DEFINITION && !o.symbol.startsWith("local") then
              defined.putIfAbsent(o.symbol, d.uri)
          }
      }
    }
    Semantic(index, defined)

  def staleSemanticdb(doc: SemanticDocument): Boolean = scala.util
    .Try {
      val internal = doc.getClass.getMethod("internal").invoke(doc)
      val textDocument = internal.getClass.getMethod("textDocument").invoke(internal).asInstanceOf[s.TextDocument]
      val md5 = java.security.MessageDigest
        .getInstance("MD5")
        .digest(doc.input.text.getBytes(UTF_8))
        .map(b => f"${b & 255}%02X")
        .mkString
      textDocument.md5.nonEmpty && textDocument.md5 != md5
    }
    .getOrElse(false)

  final case class TypeDef(module: String, scope: String, rel: String, owner: String, name: String)

  lazy val typeDefs: Vector[TypeDef] =
    texts.par(text =>
      dialects.Scala3(Input.VirtualFile(text.ref.rel, text.raw)).parse[Source].toOption.toList.flatMap { tree =>
        typeDefsOf(tree, text.ref)
      }
    )

  extension (v: Vector[Text])
    def par[A](f: Text => List[A]): Vector[A] =
      v.asJava.parallelStream().map(t => f(t)).toList.asScala.toVector.flatten

  def ownerKey(t: Tree, rel: String): String =
    val chain = enclosingTemplates(t).map(_._2)
    if chain.isEmpty then "" else rel + "#" + chain.reverse.mkString(".")

  def enclosingTemplates(t: Tree): List[(Tree, String)] =
    Iterator
      .iterate(t.parent)(_.flatMap(_.parent))
      .takeWhile(_.nonEmpty)
      .flatten
      .collect {
        case o: Defn.Object => o -> o.name.value
        case c: Defn.Class  => c -> c.name.value
        case c: Defn.Trait  => c -> c.name.value
        case e: Defn.Enum   => e -> e.name.value
      }
      .toList

  def local(t: Tree): Boolean =
    Iterator
      .iterate(t.parent)(_.flatMap(_.parent))
      .takeWhile(_.nonEmpty)
      .flatten
      .exists {
        case _: Defn.Def | _: Defn.Val | _: Defn.Var | _: Defn.Given | _: Defn.GivenAlias => true
        case _: Term.Block                                                                => true
        case _                                                                            => false
      }

  def typeDefsOf(tree: Tree, r: SourceRef): List[TypeDef] =
    tree.collect {
      case d: Defn.Class if !local(d)  => TypeDef(r.module, r.scope, r.rel, ownerKey(d, r.rel), d.name.value)
      case d: Defn.Trait if !local(d)  => TypeDef(r.module, r.scope, r.rel, ownerKey(d, r.rel), d.name.value)
      case d: Defn.Enum if !local(d)   => TypeDef(r.module, r.scope, r.rel, ownerKey(d, r.rel), d.name.value)
      case d: Defn.Object if !local(d) => TypeDef(r.module, r.scope, r.rel, ownerKey(d, r.rel), d.name.value)
    }

  lazy val typeDefsByModule: Map[String, Vector[TypeDef]] = typeDefs.groupBy(_.module)

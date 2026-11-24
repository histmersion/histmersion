import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.meta.internal.{semanticdb => s}

object AttributeEndpoints {
  case class Config(
      semanticdbRoots: List[String] = Nil,
      files: String = ".*(Routes|Tools|HttpApi)\\.scala",
      methods: List[String] = List("serverLogic", "serverLogicSuccess", "handle"),
      creatorType: String = "com/histmersion/domain/Creator.New#",
      wrappers: Map[String, String] = Map.empty,
      toolFiles: String = ".*Tools\\.scala",
      toolWrappers: Map[String, String] = Map.empty,
      refuseIn: List[String] = List("serverLogicSuccess"),
      importLine: String = "com.histmersion.domain.CreatorControllers",
      report: String = "refactor-audit/provenance/attribute-endpoints.tsv",
      apply: Boolean = false
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val rows: mutable.LinkedHashSet[String] = mutable.LinkedHashSet.empty

  private val indexes = mutable.Map.empty[List[String], Map[String, s.SymbolInformation]]

  def index(roots: List[String]): Map[String, s.SymbolInformation] = synchronized {
    indexes.getOrElseUpdate(roots, roots.flatMap { r =>
      val root = Paths.get(r)
      if (!Files.isDirectory(root)) Nil
      else {
        val stream = Files.walk(root)
        try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".semanticdb")).toList
        finally stream.close()
      }
    }.flatMap(f => s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents)
      .flatMap(_.symbols.filterNot(_.symbol.startsWith("local")).map(i => i.symbol -> i)).toMap)
  }

  def flush(path: String): Unit = synchronized {
    val out = Paths.get(path)
    Option(out.getParent).foreach(Files.createDirectories(_))
    val counts = rows.toList.groupBy(_.takeWhile(_ != '\t')).map { case (k, v) => s"# $k\t${v.size}" }.toList.sorted
    Files.write(out, (counts ++ List("kind\tpath\tline\tmethod\tparameter\ttype") ++ rows).mkString("", "\n", "\n")
      .getBytes(UTF_8))
  }
}

class AttributeEndpoints(config: AttributeEndpoints.Config) extends SemanticRule("AttributeEndpoints") {
  import AttributeEndpoints._

  def this() = this(AttributeEndpoints.Config())

  private lazy val index: Map[String, s.SymbolInformation] = AttributeEndpoints.index(config.semanticdbRoots)

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("AttributeEndpoints")(this.config).map(new AttributeEndpoints(_))

  private def typeSymbol(t: SemanticType): Option[String] = t match {
    case TypeRef(_, sym, _) => Some(sym.value)
    case _                  => None
  }

  private def rawTypeSymbol(t: s.Type): Option[String] = t match {
    case r: s.TypeRef => Some(r.symbol)
    case _            => None
  }

  private def creatorParameter(i: s.SymbolInformation): Boolean = i.signature match {
    case v: s.ValueSignature => rawTypeSymbol(v.tpe).contains(config.creatorType)
    case _                   => false
  }

  private def takesCreator(sym: Symbol)(implicit doc: SemanticDocument): Boolean =
    index.get(sym.value).exists(_.signature match {
      case m: s.MethodSignature =>
        m.parameterLists.exists(scope =>
          scope.hardlinks.exists(creatorParameter) || scope.symlinks.exists(p => index.get(p).exists(creatorParameter)))
      case _ => false
    }) || localInfo(sym)

  private def localInfo(sym: Symbol)(implicit doc: SemanticDocument): Boolean =
    sym.info.exists(_.signature match {
          case MethodSignature(_, lists, _) =>
            lists.flatten.exists(p => p.signature match {
              case ValueSignature(t) => typeSymbol(t).contains(config.creatorType)
              case _                 => false
            })
          case _ => false
        })

  private def parents(sym: String)(implicit doc: SemanticDocument): List[String] =
    index.get(sym) match {
      case Some(i) => i.signature match {
          case c: s.ClassSignature => c.parents.toList.flatMap(rawTypeSymbol)
          case t: s.TypeSignature  => rawTypeSymbol(t.upperBound).toList
          case _                   => Nil
        }
      case None => Symbol(sym).info.toList.flatMap(_.signature match {
          case ClassSignature(_, ps, _, _) => ps.flatMap(typeSymbol)
          case TypeSignature(_, _, upper)  => typeSymbol(upper).toList
          case _                           => Nil
        })
    }

  private def reaches(sym: String, wrappers: Map[String, String], depth: Int)(implicit
      doc: SemanticDocument): Option[String] =
    if (depth > 6) None
    else wrappers.get(sym).orElse(
      parents(sym).iterator.map(reaches(_, wrappers, depth + 1)).collectFirst { case Some(w) => w })

  private def wrapperIn(path: String)(implicit doc: SemanticDocument): String => Option[String] =
    if (path.matches(config.toolFiles)) reaches(_, config.toolWrappers, 0) else reaches(_, config.wrappers, 0)

  private def paramType(p: Term.Param)(implicit doc: SemanticDocument): Option[String] =
    p.name.symbol.info.flatMap(_.signature match {
      case ValueSignature(t) => typeSymbol(t)
      case _                 => None
    })

  private def lambda(arg: Term): Option[Term.Function] = arg match {
    case f: Term.Function                                  => Some(f)
    case Term.Block(List(f: Term.Function))                => Some(f)
    case _                                                 => None
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match {
      case Input.VirtualFile(p, _) => p
      case Input.File(p, _)        => p.toString
      case other                   => other.syntax
    }
    if (!path.matches(config.files)) Patch.empty
    else {
      val patches = doc.tree.collect {
        case Term.Apply.After_4_6_0(fun, Term.ArgClause(List(arg), _)) if (fun match {
              case Term.Select(_, n)                      => config.methods.contains(n.value)
              case Term.ApplyType.After_4_6_0(Term.Select(_, n), _) => config.methods.contains(n.value)
              case _                                      => false
            }) =>
          val method = fun match {
            case Term.Select(_, n)                                => n.value
            case Term.ApplyType.After_4_6_0(Term.Select(_, n), _) => n.value
            case _                                                => ""
          }
          val line = (arg.pos.startLine + 1).toString
          val needs = arg.collect { case n: Term.Name if takesCreator(n.symbol) => n }.nonEmpty
          if (needs && lambda(arg).isEmpty) {
            rows += List("stop", path, line, method, "", "not a function literal").mkString("\t")
            Patch.empty
          }
          else if (!needs) Patch.empty
          else {
            val f = lambda(arg).get
            val params = f.paramClause.values
            val typed = params.map(p => (p, paramType(p)))
            val wrapperOf = wrapperIn(path)
            typed.collectFirst { case (p, Some(t)) if wrapperOf(t).isDefined => (p, t, wrapperOf(t).get) } match {
              case Some((p, t, wrapper)) if config.refuseIn.contains(method) =>
                rows += List("stop", path, line, method, p.name.value, s"$t: returns no Either").mkString("\t")
                Patch.empty
              case Some((p, t, wrapper)) =>
                rows += List("wrap", path, line, method, p.name.value, t).mkString("\t")
                if (!config.apply) Patch.empty
                else
                  Patch.addLeft(f.body, s"$wrapper(${p.name.value}) { ") + Patch.addRight(f.body, " }") +
                    Patch.addGlobalImport(importer(config.importLine))
              case None =>
                rows += List("fallback", path, line, method, params.map(_.name.value).mkString(","),
                  typed.map(_._2.getOrElse("?")).mkString(",")).mkString("\t")
                Patch.empty
            }
          }
      }
      flush(config.report)
      patches.asPatch
    }
  }

  private def importer(path: String): Importer = {
    implicit val dialect: Dialect = dialects.Scala3
    s"import $path".parse[Stat].get.asInstanceOf[Import].importers.head
  }
}

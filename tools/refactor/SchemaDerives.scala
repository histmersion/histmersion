import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object SchemaDerives {
  case class Policy(mode: String = "inspect", roots: List[String] = List("modules"), report: String = "refactor-audit/schema-derives",
      adapter: String = "com.histmersion.api.json.Schema", adapterPath: String = "modules/api/src/main/scala/com/histmersion/api/json/Schema.scala")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  def paths(roots: List[String]): Set[String] = roots.flatMap { r =>
    val walk = Files.walk(Paths.get(r).toAbsolutePath.normalize)
    try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList finally walk.close()
  }.toSet
}

class SchemaDerives(val policy: SchemaDerives.Policy) extends SemanticRule("SchemaDerives") {
  def this() = this(SchemaDerives.Policy())
  import SchemaDerives._
  private implicit val dialect: Dialect = dialects.Scala3
  private val schema = "sttp/tapir/Schema#"
  private val kindlings = "hearth/kindlings/tapirschemaderivation/KindlingsSchema#"
  private val files = new ConcurrentLinkedQueue[(String,String)]()
  private val rows = new ConcurrentLinkedQueue[List[String]]()
  private val refs = new ConcurrentLinkedQueue[List[String]]()
  private val applied = new ConcurrentLinkedQueue[String]()
  private val plan = if (policy.mode == "apply") Files.readAllLines(Paths.get(policy.report, "plan.tsv"), UTF_8).asScala.drop(1).map(_.split("\t", -1).toList).filter(_.head == "rewrite").toList else Nil
  private val symbols = plan.map(_(4)).toSet
  private val adapterSource = s"""package ${policy.adapter.split('.').dropRight(1).mkString(".")}

import scala.language.adhocExtensions

final class ${policy.adapter.split('.').last}[A] private (schema: sttp.tapir.Schema[A]) extends sttp.tapir.Schema[A](
  schema.schemaType, schema.name, schema.isOptional, schema.description, schema.default,
  schema.format, schema.encodedExample, schema.deprecated, schema.hidden, schema.validator, schema.attributes
)
object ${policy.adapter.split('.').last}:
  def derived[A](using instance: hearth.kindlings.tapirschemaderivation.KindlingsSchema[A]): ${policy.adapter.split('.').last}[A] =
    new ${policy.adapter.split('.').last}(instance.schema)
"""
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("SchemaDerives")(Policy()).andThen { p =>
    if (!Set("inspect", "apply", "check", "measure")(p.mode) || p.roots.isEmpty || p.adapter.isEmpty || p.report.isEmpty) Configured.error("Require mode, roots, adapter and report")
    else {
      if (p.mode == "apply") {
        val configured = new SchemaDerives(p)
        if (p.adapterPath.nonEmpty && Files.exists(Paths.get(p.adapterPath)))
          require(Files.readString(Paths.get(p.adapterPath)).parse[Source].get.structure == configured.adapterSource.parse[Source].get.structure, "Adapter destination differs")
        val manifest = Files.readAllLines(Paths.get(p.report,"sources.tsv"), UTF_8).asScala.drop(1).map(_.split("\t").toList)
        require(paths(p.roots) == manifest.map(_.head).toSet, "Source set changed; inspect again")
        manifest.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1), s"Stale source: ${r.head}"))
        require(Files.readString(Paths.get(p.report,"adapter.txt")) == p.adapter, "Adapter changed; inspect again")
      }
      Configured.ok(new SchemaDerives(p))
    }
  }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def symbol(t: Tree)(implicit doc: SemanticDocument): String = t match {
    case a: Type.Apply => symbol(a.tpe)
    case s: Type.Select => s.name.symbol.value
    case n: Name => n.symbol.value
    case _ => ""
  }
  private def bridge(g: Defn.GivenAlias)(implicit doc: SemanticDocument): Option[Type] = g.decltpe match {
    case a: Type.Apply if symbol(a.tpe) == schema && a.argClause.values.size == 1 => g.body match {
      case s: Term.Select if s.name.symbol.value == kindlings + "schema()." => s.qual match {
        case call: Term.ApplyType if call.fun.symbol.value == "scala/Predef.summon()." && call.targClause.values.size == 1 =>
          call.targClause.values.head match {
            case k: Type.Apply if symbol(k.tpe) == kindlings && k.argClause.values.size == 1 && k.argClause.values.head.structure == a.argClause.values.head.structure => Some(a.argClause.values.head)
            case _ => None
          }
        case _ => None
      }
      case _ => None
    }
    case _ => None
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p }
    files.add(path -> hash(doc.input.text))
    val classes = doc.tree.collect { case c: Defn.Class => c }
    val removals = scala.collection.mutable.ListBuffer.empty[Defn.GivenAlias]
    val additions = scala.collection.mutable.Set.empty[Defn.Class]
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    doc.tree.collect { case g: Defn.GivenAlias if bridge(g).nonEmpty =>
      val target = bridge(g).get
      val cls = classes.find(_.name.symbol.value == symbol(target))
      val obj = parents(g).collectFirst { case o: Defn.Object => o }
      val derivedKindlings = obj.toList.flatMap(_.name.symbol.info.toList).exists(_.signature match {
        case ClassSignature(_,_,_,decls) => decls.exists(d => d.displayName.startsWith("derived$") && (d.signature match {
          case ValueSignature(TypeRef(_,s,List(TypeRef(_,t,Nil)))) => s.value == kindlings && t.value == symbol(target)
          case _ => false
        }))
        case _ => false
      })
      val members = obj.toList.flatMap(_.name.symbol.info.toList.flatMap(_.signature match {
        case ClassSignature(_,_,_,decls) => decls.filter(_.signature match {
          case ValueSignature(TypeRef(_,s,List(TypeRef(_,t,Nil)))) => s.value == schema && t.value == symbol(target)
          case MethodSignature(Nil,Nil,TypeRef(_,s,List(TypeRef(_,t,Nil)))) => s.value == schema && t.value == symbol(target)
          case _ => false
        })
        case _ => Nil
      }))
      val resolved = if (!g.name.symbol.isNone) g.name.symbol.value else if (members.size == 1) members.head.symbol.value else ""
      val reason = if (cls.isEmpty) "type-not-local-case-class" else if (!cls.get.mods.exists(_.is[Mod.Case])) "non-case-class"
        else if (cls.get.tparams.nonEmpty) "generic-type"
        else if (obj.isEmpty || obj.get.name.symbol.value.dropRight(1) != cls.get.name.symbol.value.dropRight(1)) "not-companion"
        else if (g.mods.nonEmpty) "modified-given"
        else if (cls.get.templ.derives.isEmpty || !derivedKindlings) "no-existing-kindlings-derivation"
        else if (resolved.isEmpty) "unresolved-given"
        else if (cls.get.templ.derives.exists(_.syntax == policy.adapter)) "adapter-already-present" else "eligible"
      val action = if (reason == "eligible") "rewrite" else "review"
      rows.add(List(action, path, (g.pos.startLine+1).toString, symbol(target), resolved, reason, g.pos.text, g.structure))
      if (policy.mode == "apply" && action == "rewrite") {
        require(symbols(resolved), s"Unplanned schema bridge: $resolved")
        removals += g; additions += cls.get; applied.add(resolved)
      }
      if (policy.mode == "check") patches += Patch.lint(Diagnostic("remaining", reason, g.pos))
    }
    val emptied = removals.toList.flatMap(g => parents(g).collectFirst { case o: Defn.Object => o }).distinct.filter(o => o.mods.isEmpty && o.templ.inits.isEmpty && o.templ.stats.nonEmpty && o.templ.stats.forall(s => removals.exists(_ eq s)))
    if (policy.mode == "apply") {
      additions.foreach(c => patches += Patch.addRight(c.templ.derives.last, ", " + policy.adapter))
      emptied.foreach(o => patches += Patch.removeTokens(o.tokens))
      removals.filterNot(g => parents(g).exists(emptied.contains)).foreach(g => patches += Patch.removeTokens(g.tokens))
    }
    doc.tree.collect { case n: Name if !n.symbol.isNone && !parents(n).exists(t => removals.exists(_ eq t) || emptied.exists(_ eq t)) =>
      refs.add(List(n.symbol.value, path, (n.pos.startLine+1).toString, n.pos.text, n.parent.getOrElse(n).structure))
      if (policy.mode == "apply" && symbols(n.symbol.value) && n.symbol.info.exists(_.displayName == n.value))
        patches += Patch.replaceTree(n, "derived$" + policy.adapter.split('.').last)
    }
    if (policy.mode == "apply") patches.toList.asPatch.atomic else patches.toList.asPatch
  }
  override def afterComplete(): Unit = {
    require(files.asScala.map(_._1).toSet == paths(policy.roots), "Incomplete schema derivation coverage")
    val out = Paths.get(policy.report); Files.createDirectories(out)
    def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    def write(name: String, header: String, rs: List[List[String]]) = Files.write(out.resolve(name), (header :: rs.map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    val all = rows.asScala.toList.sortBy(r => (r(1),r(2).toInt))
    val targetSymbols = all.filter(_.head == "rewrite").map(_(4)).toSet
    val references = refs.asScala.toList.filter(r => targetSymbols(r.head)).distinct.sortBy(r => (r(1),r(2).toInt))
    if (Set("inspect", "measure")(policy.mode)) {
      write("plan.tsv", "action\tpath\tline\ttype\tgiven\treason\tsource\tast", all)
      write("sources.tsv", "path\tsha256", files.asScala.toList.sortBy(_._1).map { case(p,h) => List(p,h) })
      write("references.tsv", "symbol\tpath\tline\tsource\tast", references)
      Files.writeString(out.resolve("adapter.txt"), policy.adapter)
    }
    if (policy.mode == "apply") {
      require(applied.asScala.toSet == symbols && applied.size == symbols.size, "Not every planned bridge was patched exactly once")
      if (policy.adapterPath.nonEmpty) {
        adapterSource.parse[Source].get
        val file = Paths.get(policy.adapterPath)
        require(!Files.exists(file) || Files.readString(file).parse[Source].get.structure == adapterSource.parse[Source].get.structure, "Adapter destination differs")
        Files.createDirectories(file.getParent)
        if (!Files.exists(file)) Files.writeString(file, adapterSource)
      }
    }
    Files.write(out.resolve(policy.mode + "-summary.txt"), (List(s"files=${files.size}", s"bridges=${all.size}", s"eligible=${all.count(_.head == "rewrite")}", s"review=${all.count(_.head == "review")}", s"explicit-references=${references.size}") ++ all.groupBy(_(5)).toList.sortBy(_._1).map { case(k,rs) => k + "=" + rs.size }).asJava, UTF_8)
    if (policy.mode == "check") require(all.isEmpty, s"${all.size} schema bridges remain")
  }
}

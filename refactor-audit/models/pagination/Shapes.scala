import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

class Shapes extends SemanticRule("Shapes") {
  private val root = Paths.get("refactor-audit/models")
  private def rows(p: java.nio.file.Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t",-1).toList).toList
  private val positions = rows(root.resolve("positions.tsv")).map(r => r.head -> r).toMap
  private val decisions = rows(root.resolve("pagination/review-1.tsv"))
  private val owners = decisions.map(r => positions(r(1))(6)).toSet
  private val expectedSources = rows(root.resolve("sources.tsv")).map(r => r.head -> r(1)).toMap
  private val seen = new ConcurrentLinkedQueue[String]()
  private val evidence = new ConcurrentLinkedQueue[List[String]]()
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  private def clean(s: String) = s.replace("\t","\\t").replace("\r","\\r").replace("\n","\\n")
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = Paths.get(doc.input.syntax).toAbsolutePath.normalize.toString
    require(expectedSources.get(path).contains(hash(doc.input.text)),"Source/inventory mismatch: " + path)
    seen.add(path)
    doc.tree.collect { case n: Name if !n.symbol.isNone && owners.exists(o => n.symbol.value.startsWith(o) || n.symbol.value.startsWith(o.stripSuffix("#") + ".")) =>
      val parents = ancestors(n)
      val operation = parents.collectFirst {
        case x: Term.Apply => x
        case x: Term.ApplyInfix => x
        case x: Term.ApplyType => x
        case x: Term.Assign => x
        case x: Init => x
      }.getOrElse(n.parent.getOrElse(n))
      val owner = parents.collectFirst {
        case x: Defn.Def => x.name.symbol.value
        case x: Defn.Class => x.name.symbol.value
        case x: Defn.Object => x.name.symbol.value
      }.getOrElse("")
      evidence.add(List(path,(n.pos.startLine+1).toString,n.pos.start.toString,n.symbol.value,owner,
        operation.productPrefix,operation.syntax,operation.structure,n.symbol.info.map(_.signature.toString).getOrElse("unresolved")))
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    require(seen.asScala.toSet == expectedSources.keySet,"Incomplete semantic source coverage")
    expectedSources.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h,"Source changed during scan: " + p) }
    val out = root.resolve("pagination/shapes-1")
    Files.createDirectories(out)
    def write(name: String, header: String, data: List[List[String]]): Unit = {
      val content = (header :: data.map(_.map(clean).mkString("\t")).sorted).mkString("\n") + "\n"
      val path = out.resolve(name)
      if(Files.exists(path)) require(Files.readString(path) == content,"Frozen shape observation changed")
      else Files.writeString(path,content)
    }
    val es = evidence.asScala.toList
    write("evidence.tsv","path\tline\tstart\ttarget\towner\tshape\tsyntax\tast\tsignature",es)
    write("counts.tsv","scope\tshape\toccurrences",es.groupBy(r => List(if(r.head.contains("/src/test/")) "test" else "production",r(5))).toList.map { case(k,v) => k :+ v.size.toString })
    write("coverage.tsv","path\tsha256",expectedSources.toList.map { case(p,h) => List(p,h) })
    Files.writeString(out.resolve("status.txt"),"PASS: semantic AST scan of " + seen.asScala.toSet.size + " source/test files; " + es.size + " matching names.\nIncludes declarations, imports and references; rows are evidence, not violations. Enclosing operations may include unrelated arguments; eligibility still requires exact symbol and argument mapping.\n")
    println(Files.readString(out.resolve("status.txt")))
  }
}

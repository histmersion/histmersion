import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

class CodecUsageInventory extends SemanticRule("CodecUsageInventory") {
  private val prefix = "com/histmersion/util/database/Codecs$package."
  private val root = Paths.get("").toAbsolutePath.normalize
  private val sources = new ConcurrentLinkedQueue[List[String]]()
  private val uses = new ConcurrentLinkedQueue[List[String]]()
  private val declarations = new ConcurrentLinkedQueue[List[String]]()
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private def symbols(value: Any): List[String] = value match {
    case id: IdTree => List(id.symbol.value).filter(_.startsWith(prefix))
    case tree: SemanticTree => tree.productIterator.toList.flatMap(symbols)
    case values: Iterable[_] => values.toList.flatMap(symbols)
    case _ => Nil
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val input = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    val path = root.relativize(input.toAbsolutePath.normalize).toString
    sources.add(List(path,hash(doc.input.text)))
    doc.tree.collect { case n: Name if n.symbol.value.startsWith(prefix) => n }.foreach { n =>
      uses.add(List(path,n.symbol.value,"source-name",(n.pos.startLine+1).toString,n.parent.getOrElse(n).structure))
    }
    doc.synthetics.foreach { tree => symbols(tree).distinct.foreach(s => uses.add(List(path,s,"compiler-synthetic","no-source-position",tree.toString))) }
    if(path == "modules/db/src/main/scala/com/histmersion/util/database/Codecs.scala") {
      doc.tree.collect {
        case g: Defn.GivenAlias => (g.symbol.value,g.structure)
        case g: Defn.Given => (g.symbol.value,g.structure)
      }.foreach { case(s,t) => declarations.add(List(s,t)) }
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val stream = Files.walk(Paths.get("modules"))
    val expected = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toSet finally stream.close()
    require(sources.asScala.map(_.head).toSet == expected,"Incomplete codec consumer scan")
    val out = Paths.get("refactor-audit/codec-ownership/inventory-1"); require(!Files.exists(out)); Files.createDirectories(out)
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String, header: String, rows: List[List[String]]) = Files.write(out.resolve(name),(header :: rows.map(_.map(escape).mkString("\t"))).asJava)
    val all = uses.asScala.toList.distinct.sortBy(r => (r.head,r(1),r(2),r(3)))
    write("uses.tsv","path\tsymbol\tevidence\tline\tast-or-synthetic",all)
    write("declarations.tsv","symbol\tast",declarations.asScala.toList)
    write("sources.tsv","path\tsha256",sources.asScala.toList.sortBy(_.head))
    val summary = all.groupBy(r => (if(r.head.contains("/src/test/")) "test" else "production",r(1),r(2))).toList.sortBy(_._1).map { case((scope,symbol,kind),rs) => List(scope,symbol,kind,rs.size.toString,rs.map(_.head).distinct.size.toString) }
    write("summary.tsv","scope\tsymbol\tevidence\trows\tfiles",summary)
    Files.writeString(out.resolve("status.txt"),"BOUNDED_CONSUMER_EVIDENCE\nfiles=" + expected.size + "\nMacro-generated consumers may be absent from SemanticDB; zero synthetic matches is not proof of no consumers.\n")
    println(summary.map(_.mkString("\t")).mkString("\n"))
  }
}

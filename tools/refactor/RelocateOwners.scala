import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object RelocateOwners {
  case class Move(from: String = "", to: String = "", oldPackage: String = "", newPackage: String = "", names: List[String] = Nil, symbols: Map[String,String] = Map.empty)
  object Move {
    implicit val surface: generic.Surface[Move] = generic.deriveSurface[Move]
    implicit val decoder: ConfDecoder[Move] = generic.deriveDecoder(Move()).noTypos
  }
  case class Policy(mode: String = "preview", report: String = "", roots: List[String] = Nil, moves: List[Move] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  def sources(roots: List[String]): Set[String] = roots.flatMap { root =>
    val stream = Files.walk(Paths.get(root))
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList finally stream.close()
  }.toSet
}

class RelocateOwners(val policy: RelocateOwners.Policy) extends SemanticRule("RelocateOwners") {
  def this() = this(RelocateOwners.Policy())
  import RelocateOwners._
  private val root = Paths.get("").toAbsolutePath.normalize
  private val seen = new ConcurrentLinkedQueue[(String,String)]()
  private val changed = new ConcurrentLinkedQueue[(String,String,String)]()
  private val evidence = new ConcurrentLinkedQueue[List[String]]()
  private def rows(path: String) = Files.readAllLines(Paths.get(path)).asScala.drop(1).map(_.split("\t",-1).toList).toList
  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  private def definition(n: Name): Boolean = n.parent.exists {
    case d: Defn.Type => d.name eq n
    case d: Defn.Object => d.name eq n
    case d: Defn.Class => d.name eq n
    case d: Defn.Trait => d.name eq n
    case _ => false
  }
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("RelocateOwners")(Policy()).map { p =>
    require(Set("preview","apply","check")(p.mode) && p.report.nonEmpty && p.roots.nonEmpty && p.moves.nonEmpty)
    val applied = p.mode == "apply" && Files.exists(Paths.get(p.report,"applied.txt"))
    require(p.moves.map(_.from).distinct.size == p.moves.size && p.moves.map(_.to).distinct.size == p.moves.size)
    require(p.moves.flatMap(_.symbols.keys).distinct.size == p.moves.map(_.symbols.size).sum)
    p.moves.foreach { m =>
      require(m.oldPackage != m.newPackage && m.from != m.to && m.names.nonEmpty && m.symbols.nonEmpty)
      require(m.symbols.values.forall(n => m.names.contains(n)))
      List(m.from,m.to).foreach(s => require(!Paths.get(s).isAbsolute && Paths.get(s).normalize.toString == s))
      if(p.mode != "check" && !applied) {
        require(Files.isRegularFile(Paths.get(m.from)), "Missing definition: " + m.from)
        require(!Files.exists(Paths.get(m.to)), "Destination collision: " + m.to)
      }
    }
    if(p.mode == "preview") require(!Files.exists(Paths.get(p.report)), "Preserve prior preview")
    if(p.mode == "apply") {
      require(Files.readString(Paths.get(p.report,"policy.txt")) == p.moves.toString)
      val frozen = rows(p.report + "/sources.tsv")
      val changed = rows(p.report + "/plan.tsv").map(r => r.head -> r(2)).toMap
      val moves = p.moves.map(m => m.from -> m.to).toMap
      val expected = if(applied) frozen.map(r => List(moves.getOrElse(r.head,r.head),changed.getOrElse(r.head,r(1)))) else frozen
      require(expected.map(_.head).toSet == sources(p.roots), "Changed source universe")
      expected.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1), "Stale source: " + r.head))
    }
    new RelocateOwners(if(applied) p.copy(mode="check") else p)
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val input = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    val path = root.relativize(input.toAbsolutePath.normalize).toString
    val source = doc.input.text
    seen.add(path -> hash(source))
    val mapping = policy.moves.flatMap(m => m.symbols.map { case(s,n) => s -> (m.newPackage + "." + n) }).toMap
    val edits = scala.collection.mutable.ListBuffer.empty[(Tree,String)]
    val moving = policy.moves.find(_.from == path)
    moving.foreach { m =>
      require(policy.mode != "check", "Old definition remains: " + path)
      val pkgs = doc.tree.collect { case p: Pkg => p }
      require(pkgs.size == 1 && pkgs.head.ref.syntax == m.oldPackage)
      val names = pkgs.head.stats.collect {
        case d: Defn.Type => d.name.value
        case d: Defn.Object => d.name.value
        case d: Defn.Class => d.name.value
        case d: Defn.Trait => d.name.value
      }.distinct.sorted
      require(names == m.names.sorted, "Unreviewed declarations: " + path)
      edits += pkgs.head.ref -> m.newPackage
    }
    val imports = doc.tree.collect { case i: Import => i }
    val handled = scala.collection.mutable.Set.empty[Tree]
    val bindings = scala.collection.mutable.ListBuffer.empty[(Tree,String,String)]
    def imported(ie: Importee): Option[(String,String)] = ie match {
      case n: Importee.Name => mapping.get(n.name.symbol.value).map(_ -> n.name.value)
      case n: Importee.Rename => mapping.get(n.name.symbol.value).map(_ -> n.rename.value)
      case n: Importee.Unimport => mapping.get(n.name.symbol.value).map(_ -> "")
      case _ => None
    }
    imports.foreach { i =>
      if(i.importers.exists(_.importees.exists(ie => imported(ie).nonEmpty))) {
        handled += i
        val rendered = i.importers.flatMap { imp =>
          val groups = scala.collection.mutable.LinkedHashMap.empty[String,List[String]]
          imp.importees.foreach { ie =>
            val target = imported(ie)
            target.filter(_._2.nonEmpty).foreach { case(t,n) => bindings += ((i.parent.get,t,n)) }
            val owner = target.map(_._1.split('.').dropRight(1).mkString(".")).getOrElse(imp.ref.syntax)
            groups.update(owner, groups.getOrElse(owner,Nil) :+ ie.pos.text)
          }
          groups.toList.map { case(owner,entries) =>
            if(entries.size == 1 && !entries.head.contains(" ")) "import " + owner + "." + entries.head
            else "import " + owner + ".{" + entries.mkString(", ") + "}"
          }
        }
        edits += i -> rendered.mkString("\n")
      }
    }
    doc.tree.collect { case n: Name if mapping.contains(n.symbol.value) => n }.foreach { n =>
      val target = mapping(n.symbol.value)
      evidence.add(List(path,(n.pos.startLine+1).toString,n.symbol.value,n.pos.text,n.parent.getOrElse(n).structure,
        if(definition(n)) "declaration" else "reference"))
      require(policy.mode != "check", "Old owner reference: " + path + ":" + (n.pos.startLine+1))
      if(!definition(n) && !ancestors(n).exists(handled) && moving.isEmpty) {
        n.parent match {
          case Some(s: Term.Select) if s.name eq n => edits += s -> ("_root_." + target)
          case Some(s: Type.Select) if s.name eq n => edits += s -> ("_root_." + target)
          case _ =>
            val bound = bindings.exists { case(scope,t,local) => t == target && local == n.value && ancestors(n).exists(_ eq scope) }
            if(!bound) edits += n -> ("_root_." + target)
        }
      }
    }
    val ordered = edits.toList.sortBy(_._1.pos.start)
    require(ordered.sliding(2).forall { case List(a,b) => a._1.pos.end <= b._1.pos.start; case _ => true }, "Overlapping patches: " + path)
    if(ordered.nonEmpty) {
      val next = ordered.reverse.foldLeft(source) { case(s,(t,replacement)) => s.patch(t.pos.start,replacement,t.pos.end-t.pos.start) }
      implicit val dialect: Dialect = dialects.Scala3
      next.parse[Source].get
      changed.add((path,source,next))
      if(policy.mode == "apply") require(next == Files.readString(Paths.get(policy.report,"after",path)), "Preview changed: " + path)
    }
    if(policy.mode == "apply") ordered.map { case(t,s) => Patch.replaceTree(t,s) }.asPatch.atomic else Patch.empty
  }
  override def afterComplete(): Unit = {
    require(seen.asScala.map(_._1).toSet == sources(policy.roots), "Incomplete traversal")
    require(seen.asScala.map(_._1).toSet.size == seen.size, "Duplicate source traversal")
    val all = changed.asScala.toList.sortBy(_._1)
    if(policy.mode == "preview") {
      val out = Paths.get(policy.report); Files.createDirectories(out)
      def write(name: String, lines: List[String]): Unit = Files.write(out.resolve(name),lines.asJava)
      write("sources.tsv", "path\tsha256" :: seen.asScala.toList.sorted.map { case(p,h) => p + "\t" + h })
      write("plan.tsv", "path\tbefore-sha256\tafter-sha256" :: all.map { case(p,b,n) => List(p,hash(b),hash(n)).mkString("\t") })
      write("moves.tsv", "from\tto" :: policy.moves.map(m => m.from + "\t" + m.to))
      Files.writeString(out.resolve("policy.txt"),policy.moves.toString)
      def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
      write("shapes.tsv", "path\tline\tsymbol\tsource\tast\trole" :: evidence.asScala.toList.sortBy(r => (r.head,r(1).toInt,r(2))).map(_.map(escape).mkString("\t")))
      all.foreach { case(p,b,n) => List("before" -> b,"after" -> n).foreach { case(dir,s) =>
        val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s)
      }}
      write("status.txt",List("PREVIEW_ONLY","files=" + all.size,"references-and-declarations=" + evidence.size))
    } else if(policy.mode == "apply") {
      require(all.map(_._1).toSet == rows(policy.report + "/plan.tsv").map(_.head).toSet, "Changed patch set")
      all.foreach { case(p,_,n) => require(Files.readString(Paths.get(p)) == n,"Patch not applied: " + p) }
      policy.moves.foreach { m => Files.createDirectories(Paths.get(m.to).getParent); Files.move(Paths.get(m.from),Paths.get(m.to)) }
      Files.writeString(Paths.get(policy.report,"applied.txt"), "PASS: patches match preview; files relocated without overwrite\n")
    } else {
      implicit val dialect: Dialect = dialects.Scala3
      policy.moves.foreach { m =>
        require(!Files.exists(Paths.get(m.from)) && Files.isRegularFile(Paths.get(m.to)), "Relocation incomplete")
        val tree = Files.readString(Paths.get(m.to)).parse[Source].get
        require(tree.collect { case p: Pkg => p.ref.syntax } == List(m.newPackage), "Wrong destination package")
      }
    }
    println(policy.mode + ": " + all.size + " changed files, " + evidence.size + " matched names")
  }
}

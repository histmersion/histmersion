import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object FeatureOwners {
  case class Move(from: String = "", to: String = "", oldPackage: String = "", newPackage: String = "", names: List[String] = Nil)
  object Move {
    implicit val surface: generic.Surface[Move] = generic.deriveSurface[Move]
    implicit val decoder: ConfDecoder[Move] = generic.deriveDecoder(Move()).noTypos
  }
  case class Policy(mode: String = "inspect", report: String = "refactor-audit/feature-owners", roots: List[String] = List("modules"), moves: List[Move] = Nil, moveFiles: Boolean = true)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def path(s: String) = Paths.get(s).toAbsolutePath.normalize
  def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  def sources(roots: List[String]): Set[String] = roots.flatMap { r =>
    val walk = Files.walk(path(r))
    try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toList
    finally walk.close()
  }.toSet
}

class FeatureOwners(val policy: FeatureOwners.Policy) extends SemanticRule("FeatureOwners") {
  def this() = this(FeatureOwners.Policy())
  import FeatureOwners._
  private val files = new ConcurrentLinkedQueue[(String, String)]()
  private val declarations = new ConcurrentLinkedQueue[String]()
  private val references = new ConcurrentLinkedQueue[List[String]]()
  private val mappings = policy.moves.flatMap(m => m.names.map(n => (m.oldPackage + "." + n, m.newPackage + "." + n)))
  private val symbols = mappings.flatMap { case (a,b) => List(a.replace('.', '/') + "." -> b, a.replace('.', '/') + "#" -> b) }.toMap
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("FeatureOwners")(Policy()).andThen { p =>
    if (!Set("inspect", "apply", "check")(p.mode) || p.moves.isEmpty ||
      p.moves.map(_.from).distinct.size != p.moves.size || p.moves.map(_.to).distinct.size != p.moves.size ||
      p.moves.exists(m => m.from == m.to || m.oldPackage == m.newPackage || m.names.isEmpty || !m.from.contains("/src/test/") || !m.to.contains("/src/test/")))
      Configured.error("Require unique test-file moves, explicit packages, declarations and inspect/apply/check mode")
    else {
      if (p.mode != "check") p.moves.foreach { m =>
        require(Files.isRegularFile(path(m.from)), s"Missing source: ${m.from}")
        require(!Files.exists(path(m.to)), s"Destination collision: ${m.to}")
      }
      if (p.mode == "apply") {
        val manifest = Files.readAllLines(path(p.report).resolve("sources.tsv"), UTF_8).asScala.drop(1).map(_.split("\t").toList)
        require(sources(p.roots) == manifest.map(_.head).toSet, "Source coverage changed; inspect again")
        manifest.foreach(r => require(hash(Files.readString(path(r.head))) == r(1), s"Stale inventory: ${r.head}"))
        require(Files.readString(path(p.report).resolve("policy.txt")) == p.moves.toString, "Move policy changed; inspect again")
      }
      Configured.ok(new FeatureOwners(p))
    }
  }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def definition(n: Name): Boolean = n.parent.exists {
    case d: Defn.Class => d.name eq n
    case d: Defn.Object => d.name eq n
    case d: Defn.Trait => d.name eq n
    case d: Defn.Type => d.name eq n
    case _ => false
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val file = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p }
    files.add(file -> hash(doc.input.text))
    val move = policy.moves.find(m => path(m.from).toString == file)
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    move.foreach { m =>
      val pkgs = doc.tree.collect { case p: Pkg => p }
      require(pkgs.size == 1 && pkgs.head.ref.syntax == m.oldPackage, s"Unexpected package: $file")
      val names = pkgs.head.stats.collect {
        case d: Defn.Class => d.name.value
        case d: Defn.Object => d.name.value
        case d: Defn.Trait => d.name.value
        case d: Defn.Type => d.name.value
      }.distinct.sorted
      require(names == m.names.distinct.sorted, s"Unreviewed declarations: $file: $names")
      declarations.add(m.from)
      if (policy.mode == "apply") {
        patches += Patch.replaceTree(pkgs.head.ref, m.newPackage)
        patches += Patch.addRight(pkgs.head.ref, "\n\nimport " + m.oldPackage + ".*")
      }
    }
    val changedImports = scala.collection.mutable.Set.empty[Tree]
    val explicit = scala.collection.mutable.Set.empty[String]
    def movedImport(i: Importee): Option[String] = i match {
      case x: Importee.Name => symbols.get(x.name.symbol.value)
      case x: Importee.Rename => symbols.get(x.name.symbol.value)
      case x: Importee.Unimport => symbols.get(x.name.symbol.value)
      case _ => None
    }
    doc.tree.collect { case i: Import if i.importers.exists(_.importees.exists(x => movedImport(x).nonEmpty)) =>
      changedImports += i
      val rendered = i.importers.flatMap { imp =>
        val groups = scala.collection.mutable.LinkedHashMap.empty[String, List[String]]
        imp.importees.foreach { ie =>
          val target = movedImport(ie)
          ie match { case _: Importee.Name => target.foreach(explicit += _); case _ => () }
          val owner = target.map(_.split('.').dropRight(1).mkString(".")).getOrElse(imp.ref.syntax)
          groups.update(owner, groups.getOrElse(owner, Nil) :+ ie.pos.text)
        }
        groups.toList.map { case (owner, entries) => "import " + owner + ".{" + entries.mkString(", ") + "}" }
      }
      if (policy.mode == "apply") patches += Patch.replaceTree(i, rendered.mkString("\n"))
    }
    val needed = scala.collection.mutable.Set.empty[String]
    doc.tree.collect { case n: Name if symbols.contains(n.symbol.value) && !definition(n) =>
      references.add(List(file, (n.pos.startLine + 1).toString, n.symbol.value, n.parent.getOrElse(n).structure))
      if (policy.mode == "check") patches += Patch.lint(Diagnostic("old-owner", "Reference still uses the previous feature owner", n.pos))
      if (policy.mode == "apply" && !parents(n).exists(changedImports)) {
        val target = symbols(n.symbol.value)
        n.parent match {
          case Some(s: Term.Select) if s.name eq n => patches += Patch.replaceTree(s, target)
          case Some(s: Type.Select) if s.name eq n => patches += Patch.replaceTree(s, target)
          case Some(i: Importer) if i.ref eq n => patches += Patch.replaceTree(n, target)
          case _ if n.value == target.split('.').last => needed += target
          case _ => () // The renamed import carries its existing local spelling.
        }
      }
    }
    if (policy.mode == "apply") {
      val pkg = doc.tree.collect { case p: Pkg => p }.headOption
      val owner = move.map(_.newPackage).orElse(pkg.map(_.ref.syntax))
      val imports = needed.toList.sorted.filterNot(t => explicit(t) || owner.contains(t.split('.').dropRight(1).mkString(".")))
      if (imports.nonEmpty) {
        require(pkg.nonEmpty, s"A package is required for added imports: $file")
        patches += Patch.addRight(pkg.get.ref, "\n\n" + imports.map("import " + _).mkString("\n"))
      }
    }
    if (policy.mode == "apply") patches.toList.asPatch.atomic else patches.toList.asPatch
  }
  override def afterComplete(): Unit = {
    val expected = sources(policy.roots)
    require(files.asScala.map(_._1).toSet == expected, "Incomplete source coverage")
    if (policy.mode != "check") require(policy.moves.forall(m => declarations.asScala.count(_ == m.from) == 1), "Every move must be visited exactly once")
    val out = path(policy.report)
    Files.createDirectories(out)
    if (policy.mode == "inspect") {
      Files.write(out.resolve("sources.tsv"), ("path\tsha256" :: files.asScala.toList.sortBy(_._1).map { case (f,h) => f + "\t" + h }).asJava, UTF_8)
      Files.writeString(out.resolve("policy.txt"), policy.moves.toString)
      def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
      Files.write(out.resolve("references.tsv"), ("path\tline\tsymbol\tast" :: references.asScala.toList.sortBy(r => (r.head,r(1).toInt)).map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    }
    if (policy.mode == "apply" && policy.moveFiles) {
      // Preflight the entire patched batch before moving any file.
      policy.moves.foreach { m =>
        val source = dialects.Scala3(Files.readString(path(m.from))).parse[Source].get
        require(source.collect { case p: Pkg => p.ref.syntax } == List(m.newPackage), s"Patch was suppressed or failed: ${m.from}")
        require(!Files.exists(path(m.to)), s"Destination collision: ${m.to}")
      }
      policy.moves.foreach { m =>
        Files.createDirectories(path(m.to).getParent)
        Files.move(path(m.from), path(m.to))
      }
    }
    if (policy.mode == "check" && policy.moveFiles) policy.moves.foreach(m => require(!Files.exists(path(m.from)) && Files.isRegularFile(path(m.to)), s"Move incomplete: ${m.from}"))
    Files.write(out.resolve(policy.mode + "-summary.txt"), List(s"files=${files.size}", s"moves=${policy.moves.size}", s"references=${references.size}").asJava, UTF_8)
    if (policy.mode == "check") require(references.isEmpty, s"Old-owner references remain: ${references.size}")
  }
}

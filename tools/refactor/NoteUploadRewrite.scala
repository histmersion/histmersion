import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class NoteUploadRewrite extends SemanticRule("NoteUploadRewrite") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val output = Paths.get("target/note-upload-preview")
  private val rows = scala.collection.mutable.ListBuffer.empty[String]
  private val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
  private val storage = "com.histmersion.util.storage."
  private var uploadCalls = 0
  private var noteCalls = 0
  private var filesystemCalls = 0
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private def save(path: String, before: Option[String], after: String): Unit = {
    after.parse[Source].get
    val dest = output.resolve(path); Files.createDirectories(dest.getParent); Files.writeString(dest, after)
    rows += List(path, before.fold("new")(hash), hash(after)).mkString("\t")
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(doc.input.syntax).toAbsolutePath.normalize).toString
    val name = Paths.get(path).getFileName.toString
    val edits = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    def insert(at: Int, text: String): Unit = edits += ((at, at, text))
    def edit(tree: Tree, text: String): Unit = {
      val rendered = text.linesIterator.zipWithIndex.map { case (line, i) => (if(i == 0 || line.isEmpty) "" else " " * tree.pos.startColumn) + line }.mkString("\n")
      edits += ((tree.pos.start, tree.pos.end, rendered))
    }
    def member(t: Template, text: String): Unit = {
      val first = t.body.stats.head
      insert(first.pos.start, text + "\n" + " " * first.pos.startColumn)
    }
    def record(kind: String, tree: Tree, symbol: String): Unit = evidence += List(kind, path, (tree.pos.startLine + 1).toString, symbol, tree.syntax, tree.structure)
    val defs = doc.tree.collect { case d: Defn.Def => d }
    val classes = doc.tree.collect { case c: Defn.Class => c }
    if(path.endsWith("/domain/library/sources/SourceNote.scala")) {
      defs.filter(d => Set("parseFile", "fullText")(d.name.value)).foreach { d =>
        insert(d.paramClauseGroups.last.paramClauses.last.pos.end, s"(using files: ${storage}TextFiles)")
      }
      doc.tree.collect { case s: Term.Select if s.name.symbol.value.startsWith("java/nio/file/Files#") =>
        require(Set("readString", "exists")(s.name.value), "Unreviewed note filesystem operation")
        filesystemCalls += 1
        record("note-filesystem", s, s.name.symbol.value)
        edit(s, "files." + (if(s.name.value == "readString") "read" else "exists"))
      }
    }
    if(path.endsWith("/util/storage/HandedFile.scala")) {
      val obj = doc.tree.collect { case o: Defn.Object if o.name.value == "HandedFile" => o }.head
      member(obj.templ, "final case class Snapshot(name: String, size: Long, bytes: ByteSource)\n\n  def snapshot(file: HandedFile): Snapshot = Snapshot(file.getName, file.length(), source(file))")
    }
    if(path.endsWith("/domain/library/ingest/Upload.scala")) {
      val handed = defs.find(_.name.value == "handed").get
      record("upload-metadata", handed, handed.name.symbol.value)
      edit(handed, "def handed(file: HandedFile.Snapshot): Upload = Upload(file.name, file.size, file.bytes)")
    }
    if(path.endsWith("/server/SeedNoteFiles.scala")) {
      insert(classes.find(_.name.value == "SeedNoteFiles").get.ctor.pos.end, s"(using ${storage}TextFiles)")
    }
    if(path.endsWith("/server/Deps.scala")) {
      member(classes.find(_.name.value == "Deps").get.templ, s"val textFiles: ${storage}TextFiles = new ${storage}DiskTextFiles")
    }
    if(path.endsWith("/ingest/SeedImportSuite.scala")) {
      member(classes.find(_.name.value == "SeedImportSuite").get.templ, s"private given ${storage}TextFiles = new ${storage}DiskTextFiles")
    }
    doc.tree.collect {
      case call: Term.Apply if call.fun.symbol.value.startsWith("com/histmersion/domain/library/ingest/Upload.handed") =>
        require(call.argClause.values.size == 1)
        uploadCalls += 1; record("upload-caller", call, call.fun.symbol.value)
        val file = call.argClause.values.head
        edit(file, s"${storage}HandedFile.snapshot(${file.syntax})")
      case call: Term.Apply if call.fun.symbol.value.startsWith("com/histmersion/domain/library/sources/SourceNote.parseFile") =>
        noteCalls += 1; record("note-caller", call, call.fun.symbol.value)
      case call: Term.Apply if path.endsWith("/instance/SeedControllers.scala") && call.fun.syntax == "SeedNoteFiles" =>
        insert(call.pos.end, "(using deps.textFiles)")
    }
    if(edits.nonEmpty) {
      val sorted = edits.toList.sortBy(e => (e._1, e._2))
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping edits: " + path)
      val result = sorted.reverse.foldLeft(doc.input.text) { case (text, (start,end,replacement)) => text.patch(start,replacement,end-start) }
      save(path, Some(doc.input.text), result)
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    require(uploadCalls == 5 && noteCalls == 2 && filesystemCalls == 3, s"Unreviewed source shape/count: upload=$uploadCalls note=$noteCalls filesystem=$filesystemCalls")
    List("TextFiles.scala" -> "files", "DiskTextFiles.scala" -> "server").foreach { case (name, module) =>
      val path = s"modules/$module/src/main/scala/com/histmersion/util/storage/$name"
      require(!Files.exists(Paths.get(path)))
      save(path, None, Files.readString(Paths.get("tools/refactor/note-upload", name)))
    }
    val report = Paths.get("refactor-audit/note-upload"); Files.createDirectories(report)
    def escaped(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    Files.write(report.resolve("plan.tsv"), ("path\tbefore-sha256\tafter-sha256" :: rows.toList.sorted).asJava)
    Files.write(report.resolve("shapes.tsv"), ("kind\tpath\tline\tsymbol\tsyntax\tAST" :: evidence.toList.sortBy(_.take(3).mkString("\t")).map(_.map(escaped).mkString("\t"))).asJava)
    println(s"Note/upload preview: ${rows.size} files; 5 upload callers, 2 note callers and 3 note filesystem operations; application unchanged")
  }
}

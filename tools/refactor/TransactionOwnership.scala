import scalafix.v1._
import scala.meta._
import metaconfig.{Configured,ConfDecoder,generic}
import java.nio.file.{Files,Paths,Path}
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object TransactionOwnership {
  case class Policy(mode: String = "preview")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class TransactionOwnership(val policy: TransactionOwnership.Policy) extends SyntacticRule("TransactionOwnership") {
  def this() = this(TransactionOwnership.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("TransactionOwnership")(TransactionOwnership.Policy()).map(new TransactionOwnership(_))
  private implicit val dialect: Dialect = dialects.Scala3
  private val root = Paths.get("").toAbsolutePath.normalize
  private val preview = Paths.get("target/transaction-repair-preview")
  private val manifest = Paths.get("refactor-audit/transaction-repair/plan.tsv")
  private val rows = new ConcurrentLinkedQueue[String]()
  private def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private val frozen = if(policy.mode == "apply") {
    val input = Files.readAllLines(manifest).asScala.drop(1).toList
    input.foreach { row =>
      val r = row.split("\t",-1); val path = Paths.get(r(0))
      require(if(r(2) == "new") !Files.exists(path) else Files.exists(path) && hash(Files.readString(path)) == r(2),"Stale transaction plan: " + path)
      if(r(0) != r(1)) require(!Files.exists(Paths.get(r(1))),"Move destination exists: " + r(1))
    }
    input.toSet
  } else Set.empty[String]
  private def write(path: Path, value: String): Unit = { Files.createDirectories(path.getParent); Files.writeString(path,value) }
  private def code(t: Tree): String = {
    val lines = t.pos.text.linesIterator.toList
    (lines.head :: lines.tail.map(line => if(line.take(t.pos.startColumn).forall(_.isWhitespace)) line.drop(t.pos.startColumn) else line)).mkString("\n")
  }
  private def indent(text: String, n: Int): String = text.linesIterator.map(" " * n + _).mkString("\n")
  private def record(from: String, to: String, before: Option[String], after: String): Unit = {
    after.parse[Source].get
    val row = List(from,to,before.fold("new")(hash),hash(after)).mkString("\t")
    require(policy.mode != "apply" || frozen(row),"Unplanned transaction patch: " + from)
    rows.add(row)
    if(policy.mode == "preview") write(preview.resolve(to),after)
  }
  override def fix(implicit doc: SyntacticDocument): Patch = {
    require(Set("preview","apply")(policy.mode),"Unknown mode")
    val path = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    val relative = root.relativize(path.toAbsolutePath.normalize).toString
    val name = path.getFileName.toString
    val targets = Set("Transactor.scala","QueryRepo.scala","QueryService.scala","SearchFixtures.scala","VacuumRepo.scala","VacuumJobs.scala",
      "GazetteerRepo.scala","GazetteerLoad.scala","GazetteerRepoSuite.scala","ImportedPlaceTimeSuite.scala","PointAreaRepo.scala")
    if(!relative.startsWith("modules/") || !targets(name)) return Patch.empty
    val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
    def removeStandalone(start: Int, end: Int, column: Int): Unit = {
      val lineStart = start - column
      val nextLine = doc.input.text.indexOf('\n',end) match { case -1 => doc.input.text.length; case n => n }
      if(doc.input.text.substring(lineStart,start).forall(_.isWhitespace) && doc.input.text.substring(end,nextLine).forall(_.isWhitespace))
        edits += ((lineStart,math.min(nextLine + 1,doc.input.text.length),""))
      else edits += ((start,end,""))
    }
    def edit(tree: Tree, text: String): Unit = {
      val rendered = text.linesIterator.toList match { case head :: tail => (head :: tail.map(" " * tree.pos.startColumn + _)).mkString("\n"); case Nil => "" }
      if(text.isEmpty) removeStandalone(tree.pos.start,tree.pos.end,tree.pos.startColumn)
      else edits += ((tree.pos.start,tree.pos.end,rendered))
    }
    def removeDoc(tree: Tree): Unit = doc.tokens.collect { case t: Token.Comment if t.pos.end <= tree.pos.start &&
      doc.input.text.substring(t.pos.end,tree.pos.start).trim.isEmpty => t }.lastOption.foreach(t => removeStandalone(t.pos.start,t.pos.end,t.pos.startColumn))
    def removeParameter(p: Term.Param, parameters: List[Term.Param]): Unit = {
      val i = parameters.indexOf(p); require(i >= 0 && i < parameters.size - 1)
      edits += ((p.pos.start,parameters(i+1).pos.start,""))
    }
    val definitions = doc.tree.collect { case d: Defn.Def => d }
    if(name == "Transactor.scala") edit(doc.tree,Files.readString(Paths.get("tools/refactor/transaction-repair/Transactor.scala")))
    if(name == "QueryRepo.scala") {
      val run = definitions.find(_.name.value == "run").get
      edit(run,"""def run(statement: String, maxRows: Int, maxCellChars: Int)(using DbCon): Either[String, QueryResult] =
  com.histmersion.api.library.Query.check(statement).flatMap(text => exec(text, maxRows, maxCellChars))""")
      val exec = definitions.find(_.name.value == "exec").get
      val inner = exec.body.collect { case t: Term.Try if t.finallyp.exists(_.syntax == "st.close()") => t }.head
      val statements = inner.expr.asInstanceOf[Term.Block].stats
      require(statements.take(3).map(_.syntax).mkString.contains("_readOnly") && statements.take(3).map(_.syntax).mkString.contains("_timeout"))
      val body = statements.drop(3).map(code).mkString("\n")
      edit(exec,"""private def exec(text: String, maxRows: Int, maxCellChars: Int)(using con: DbCon): Either[String, QueryResult] =
  val c: Connection = con.connection
  try
    val st = c.createStatement()
    try
""" + indent(body,6) + "\n    finally st.close()\n  catch case e: SQLException => Left(message(e))")
      removeDoc(exec)
      val message = definitions.find(_.name.value == "message").get
      val keyword = message.tokens.collectFirst { case t: Token.KwDef => t }.get
      edits += ((message.pos.start,keyword.pos.start,""))
    }
    if(name == "QueryService.scala" || name == "SearchFixtures.scala") {
      definitions.filter(d => d.name.value == "run" && d.body.syntax.contains("QueryRepo.run")).foreach { run =>
        val transactor = if(name == "QueryService.scala") "queries" else "_root_.com.histmersion.util.database.Transactor(ds)"
        edit(run.body,s"""try $transactor.readOnlyRollback(timeoutMs)(QueryRepo.run(statement, maxRows, maxCellChars))
catch case error: java.sql.SQLException => Left(QueryRepo.message(error))""")
      }
    }
    if(name == "VacuumRepo.scala") doc.tree.collect {
      case a: Term.Apply if a.fun.syntax == "c.setAutoCommit" => edit(a,"")
    }
    if(name == "VacuumJobs.scala") doc.tree.collect {
      case a: Term.Apply if a.fun.syntax == "transactor.read" && a.argClause.values.exists(_.syntax.startsWith("VacuumRepo.vacuum")) =>
        edit(a.fun.asInstanceOf[Term.Select].name,"autocommit")
    }
    if(name == "GazetteerRepo.scala") {
      doc.tree.collect { case i: Import if i.syntax == "import scala.annotation.tailrec" || i.syntax == "import hearth.kindlings.optics.syntax.*" => edit(i,"") }
      val factory = definitions.find(_.name.value == "session").get
      factory.paramClauseGroups.flatMap(_.paramClauses).foreach { clause => clause.values.find(_.name.value == "apply").foreach(removeParameter(_,clause.values)) }
      factory.body.collect { case a: Term.Apply if a.fun.syntax == "Session" =>
        val values = a.argClause.values; val arg = values.find(_.syntax == "apply").get
        edits += ((arg.pos.start,values(values.indexOf(arg)+1).pos.start,""))
      }
      removeDoc(factory)
      val session = doc.tree.collect { case c: Defn.Class if c.name.value == "Session" => c }.head
      session.ctor.paramClauses.foreach { clause => clause.values.find(_.name.value == "apply").foreach(removeParameter(_,clause.values)) }
      removeDoc(session)
      session.templ.body.stats.collect { case v: Defn.Val if v.pats.exists(_.syntax == "jdbc") => edit(v,"") }
      val batch = definitions.find(_.name.value == "batch").get
      edit(batch,"""def writeBatch(records: List[GazRecord]): Gazetteer.LoadCounts = write(records)

def recordSeen(records: List[GazRecord]): Unit =
  val refs = records.map(_.ref)
  val _ = sql"INSERT INTO gaz_seen SELECT unnest($refs::text[]) ON CONFLICT DO NOTHING".update.run()""")
      removeDoc(batch)
      val prune = definitions.find(_.name.value == "prune").get
      edit(prune,"""def pruneCandidates: List[String] =
  if pointAreaKinds.isEmpty || !Gazetteer.PointAreaSources.contains(source) then Nil
  else PointAreaRepo.ofSourceIn(source, pointAreaKinds.toList.sorted)

def deletePruneChunk(ids: List[String]): Int = PointAreaRepo.deletePlacesIn(ids)""")
      removeDoc(prune)
      val sweep = definitions.find(_.name.value == "sweep").get
      val transaction = sweep.body.collect { case t: Term.Try => t }.head
      val work = transaction.expr.asInstanceOf[Term.Block].stats
      require(work.size == 6 && work(4).syntax.contains("jdbc.commit"))
      edit(sweep,"def sweep(): Gazetteer.LoadCounts =\n" + indent((work.take(4) :+ work.last).map(code).mkString("\n"),2))
      removeDoc(sweep)
    }
    if(Set("GazetteerLoad.scala","GazetteerRepoSuite.scala","ImportedPlaceTimeSuite.scala")(name)) doc.tree.collect {
      case s: Term.Select if s.qual.syntax == "GazetteerRepo" && s.name.value == "session" => edit(s.qual,"GazetteerImport")
    }
    if(name == "PointAreaRepo.scala" || name == "GazetteerLoad.scala") doc.tokens.collect {
      case c: Token.Comment if c.text.contains("GazetteerRepo.Session") =>
        edits += ((c.pos.start,c.pos.end,c.text.replace("GazetteerRepo.Session","GazetteerImport.Session")))
    }
    if(edits.isEmpty) return Patch.empty
    val ordered = edits.toList.sortBy(_._1)
    require(ordered.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true },"Overlapping transaction patches: " + relative)
    val result = ordered.reverse.foldLeft(doc.input.text) { case (text,(start,end,replacement)) => text.patch(start,replacement,end-start) }
    val move = Set("GazetteerRepoSuite.scala","ImportedPlaceTimeSuite.scala")(name) && relative.startsWith("modules/db/src/test/")
    val destination = if(move) "modules/server/" + relative.stripPrefix("modules/db/") else relative
    record(relative,destination,Some(doc.input.text),result)
    if(policy.mode == "apply") {
      if(move) { write(Paths.get(destination),result); Files.delete(path); Patch.empty }
      else Patch.replaceTree(doc.tree,result)
    } else Patch.empty
  }
  override def afterComplete(): Unit = {
    val destination = "modules/server/src/main/scala/com/histmersion/domain/catalogue/places/GazetteerImport.scala"
    val service = Files.readString(Paths.get("tools/refactor/transaction-repair/GazetteerImport.scala"))
    require(!Files.exists(Paths.get(destination)),"Generated service destination exists")
    record(destination,destination,None,service)
    if(policy.mode == "apply") {
      require(rows.asScala.toSet == frozen,"Transaction plan coverage changed")
      write(Paths.get(destination),service)
    } else write(manifest,"source\tdestination\tbefore-sha256\tafter-sha256\n" + rows.asScala.toList.sorted.mkString("\n") + "\n")
    println(s"Transaction ownership ${policy.mode}: ${rows.size} files planned")
  }
}

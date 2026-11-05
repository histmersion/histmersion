import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class WallClockPreview extends SyntacticRule("WallClockPreview") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def read(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    val review = read("refactor-audit/capability-treatment/review-2/findings.tsv").tail.map(_.split("\t", -1).toList).filter(_(10) == "wall-clock")
    require(review.map(_(1).toInt).sum == 29)
    val frozen = read("refactor-audit/transaction-query-comments/build/sources.tsv").tail.map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val clocks = Map("AgentsControllers" -> "clock", "Engine" -> "agents.clock", "RunCtx" -> "engine.agents.clock", "OaBacklog" -> "agents.clock", "AcquireRecheckService" -> "agents.clock", "ModelPicker" -> "wallClock", "RunTokens" -> "wallClock")
    val required = Map("CoreBudget" -> "clock", "CandidateSearch" -> "today", "SttpHttp" -> "clock")
    val out = Paths.get("refactor-audit/wall-clock/preview-1")
    require(!Files.exists(out))
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val outputs = review.groupBy(_(5)).toList.sortBy(_._1).map { case(path, rows) =>
      val source = Files.readString(Paths.get(path)); require(hash(source) == frozen(path))
      val tree = source.parse[Source].get
      val name = Paths.get(path).getFileName.toString.stripSuffix(".scala")
      val edits = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
      def edit(t: Tree, replacement: String): Unit = {
        edits += ((t.pos.start, t.pos.end, replacement))
        evidence += List(path, (t.pos.startLine + 1).toString, t.syntax, t.structure, replacement)
      }
      if(required.contains(name)) {
        require(rows.map(_(1).toInt).sum == 1)
        val params = tree.collect { case p: Term.Param if p.name.value == required(name) && p.default.nonEmpty => p }
        require(params.size == 1)
        edit(params.head, params.head.copy(default = None).syntax)
      } else {
        val receiver = clocks(name)
        val symbols = rows.map(_(7)).toSet
        var changed = 0
        tree.collect { case a: Term.Apply => a }.foreach { a =>
          val method = a.fun.syntax
          if(a.argClause.values.isEmpty && Set("System.currentTimeMillis", "java.lang.System.currentTimeMillis")(method)) {
            require(symbols("java/lang/System#currentTimeMillis()."))
            edit(a, receiver + ".millis()"); changed += 1
          } else if(a.argClause.values.isEmpty && Set("Instant.now", "java.time.Instant.now")(method)) {
            require(symbols("java/time/Instant#now()."))
            edit(a, method + "(" + receiver + ")"); changed += 1
          }
        }
        require(changed == rows.map(_(1).toInt).sum, "Unexpected wall-clock shape in " + path)
        if(Set("ModelPicker", "RunTokens")(name)) {
          val classes = tree.collect { case c: Defn.Class if c.name.value == name => c }; require(classes.size == 1)
          val c = classes.head
          val at = if(c.ctor.paramClauses.isEmpty) c.name.pos.end else c.ctor.pos.end
          edits += ((at, at, "(using wallClock: java.time.Clock)"))
        }
      }
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true })
      val next = sorted.reverse.foldLeft(source) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
      next.parse[Source].get
      (path,source,next)
    }
    require(outputs.size == 10 && evidence.size == 29)
    Files.createDirectories(out)
    outputs.foreach { case(p,b,n) =>
      List("before" -> b,"sources" -> n).foreach { case(dir,s) => val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) }
    }
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.map { case(p,b,n) => s"$p\t${hash(b)}\t${hash(n)}" }).asJava)
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(out.resolve("shapes.tsv"),("path\tline\tsource\tast\treplacement" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    Files.writeString(out.resolve("status.txt"), "PREVIEW_ONLY\nwall-clock-sites=29\nfiles=10\nnew-contextual-clock-constructors=2\nrequired-callbacks=3\nCaller propagation and compilation remain required; no verified repair credit.\n")
    println("Previewed all 29 wall-clock sites in ten files; caller propagation remains required")
  }
}

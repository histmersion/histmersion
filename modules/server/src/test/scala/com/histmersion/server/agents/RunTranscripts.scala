package com.histmersion
package server.agents
import hearth.kindlings.jsoniterjson.JsonObject
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson


import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import javax.sql.DataSource
import scala.collection.mutable
import com.histmersion.db.TestDb

/** The record of what each test of a scripted-model suite left in the database, compared with the one written on the
  * code before the automation engine moved: every row a test inserted or updated (its transaction id at or after the
  * test's start), in every table but the bulky reference ones, plus the texts a test hands to [[note]] (the scheduler's
  * and the autopilot's answers).
  *
  * A row is its JSON with what a run mints masked: timestamps as `TS`, UUIDs as `UUID` (a run's eight-character short
  * form as `RUN`), serial ids as `ID` (in every column that names one; `#<n>` in a text as `#ID`), durations as `MS`. A
  * long text is its digest and length. Rows are sorted, so the order parallel steps write in does not show. A count of
  * minutes in a text is `N min` (how long an item waited depends on the wall clock). `HISTMERSION_UPDATE_ORACLE=1`
  * rewrites `oracle/automation/runs/<suite>.txt`; otherwise each test's block is compared with its recorded one, and
  * the actual record is written under `target/oracle-actual/`.
  */
trait RunTranscripts extends munit.FunSuite:

  /** The database the suite's runs write to. */
  protected def transcriptDs: DataSource

  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  /** Recorded and compared only when asked (`HISTMERSION_ORACLE_RUNS=1`) and with a database: the rows a test leaves
    * depend on what the other suites of its forked group left in the group's database, so the record holds for one
    * invocation only, `server/testOnly com.histmersion.server.agents.*`, the one it was written under.
    */
  protected def transcriptOn: Boolean =
    TestDb.url.isDefined && sys.env.get("HISTMERSION_ORACLE_RUNS").contains("1")
  private def suiteName = getClass.getSimpleName
  private def recorded = Path.of(s"modules/server/src/test/resources/oracle/automation/runs/$suiteName.txt")
  private def actual = Path.of(s"target/oracle-actual/automation/runs/$suiteName.txt")

  private val notes = mutable.ListBuffer.empty[String]
  @volatile private var startXid: Long = 0L
  private val blocks = mutable.LinkedHashMap.empty[String, String]

  /** A text the test wants in its record (a status, a decision), under `label`. */
  protected def note(label: String, text: String): Unit = notes.synchronized(notes += s"## $label\n$text")

  private val Skipped = Set(
    "flyway_schema_history",
    "source_chunks",
    "land",
    "places",
    "place_names",
    "place_types",
    "place_descriptions",
    "place_footprints",
    "place_links",
    "place_relations",
    "periods",
    "gazetteer_sources",
    "seed_versions",
    "site_undated_cells",
    "site_undated_build",
    "map_changes",
    "retraction_watch",
    "instance"
  )

  private val transcript = new Fixture[Unit]("run-transcript"):
    def apply(): Unit = ()
    override def beforeEach(context: BeforeEach): Unit =
      if transcriptOn then
        notes.synchronized(notes.clear())
        startXid = query(transcriptDs, "SELECT (pg_current_xact_id()::text::bigint % 4294967296)::text").head.toLong
    override def afterEach(context: AfterEach): Unit =
      if transcriptOn then
        val block = record()
        val name = context.test.name
        blocks.synchronized(blocks.put(name, block))
        write(actual)
        if update then write(recorded)
        else
          val expected = RunTranscripts.blocksOf(
            if Files.exists(recorded) then Files.readString(recorded, UTF_8) else ""
          )
          expected.get(name) match
            case None                            => fail(s"$suiteName: no recorded transcript for '$name' in $recorded")
            case Some(e) if e.trim != block.trim =>
              val el = e.trim.linesIterator.toVector
              val al = block.trim.linesIterator.toVector
              val at = el.zip(al).indexWhere((a, b) => a != b) match
                case -1 => math.min(el.size, al.size)
                case i  => i
              fail(
                s"$suiteName: '$name' differs from its recorded transcript at line ${at + 1}:\n" +
                  s"  recorded: ${el.lift(at).getOrElse("<end>")}\n  actual:   ${al.lift(at).getOrElse("<end>")}\n" +
                  s"(the whole record: $actual)"
              )
            case _ => ()

  override def munitFixtures: Seq[munit.AnyFixture[?]] = super.munitFixtures :+ transcript

  private def write(to: Path): Unit = blocks.synchronized {
    Files.createDirectories(to.getParent)
    val text = blocks.map((n, b) => s"${RunTranscripts.Mark}$n\n${b.trim}\n").mkString("\n")
    val _ = Files.writeString(to, text, UTF_8)
  }

  private def query(ds: DataSource, sql: String): List[String] =
    val c = ds.getConnection
    try
      val st = c.createStatement()
      try
        val rs = st.executeQuery(sql)
        val out = mutable.ListBuffer.empty[String]
        while rs.next() do out += Option(rs.getString(1)).getOrElse("")
        out.toList
      finally st.close()
    finally c.close()

  private def record(): String =
    val tables = query(
      transcriptDs,
      """SELECT table_name FROM information_schema.tables
         WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name"""
    ).filterNot(Skipped)
    val rows: List[(String, JsonObject)] = tables.flatMap { t =>
      query(
        transcriptDs,
        s"SELECT to_jsonb(x)::text FROM $t x WHERE (x.xmin::text::bigint) >= $startXid"
      ).flatMap(r => RawJson.parse(r).toOption.flatMap(_.asObject)).map(t -> _)
    }
    val lines = rows.map((t, o) => s"$t ${RunTranscripts.masked(Json.fromJsonObject(o)).noSpaces}")
    val noted = notes.synchronized(notes.toList).map(RunTranscripts.maskText(_, long = false))
    (lines.sorted ++ noted).mkString("\n")

object RunTranscripts:
  val Mark = "=== "

  def blocksOf(text: String): Map[String, String] =
    text
      .split("(?m)^" + java.util.regex.Pattern.quote(Mark))
      .toList
      .filter(_.nonEmpty)
      .map { b =>
        val (name, rest) = b.span(_ != '\n')
        name -> rest.drop(1)
      }
      .toMap

  private val Uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}".r
  private val Stamp =
    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}(:?\\d{2})?)?".r
  private val Day = "\\b\\d{4}-\\d{2}-\\d{2}\\b".r
  private val Minutes = "\\b\\d+ min\\b".r
  private val ShortRun = "\\b[0-9a-f]{8}\\b".r
  private val Hash = "#(\\d+)".r
  private val Numbered = "\\b(approvals?|items?|hints?|tasks?|versions?) (\\d+)\\b".r
  private val Serials = Set("approval", "item", "hint", "task", "previousVersion", "version", "versionId")
  private val Durations = Set("latency_ms", "duration_ms", "elapsed_ms", "pid")

  private def digest(s: String): String =
    val d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(UTF_8))
    d.take(6).map(b => f"${b & 0xff}%02x").mkString

  /** `text` with what a run mints masked; a long one (with `long`) as its digest and length. */
  def maskText(text: String, long: Boolean = true): String =
    val t1 = Uuid.replaceAllIn(text, "UUID")
    val t2 = Stamp.replaceAllIn(t1, "TS")
    val t3 = ShortRun.replaceAllIn(Day.replaceAllIn(t2, "DAY"), "RUN")
    val t4 = Numbered.replaceAllIn(Hash.replaceAllIn(t3, "#ID"), m => s"${m.group(1)} ID")
    val t5 = Minutes.replaceAllIn(t4, "N min")
    if long && t5.length > 160 then s"<${digest(t5)}:${t5.length}>" else t5

  /** A column or field that names a serial row: its number is whatever the sequence gave. */
  private def idKey(key: String): Boolean =
    key == "id" || key == "ref" || key.endsWith("_id") || key.endsWith("_item") || Serials(key)

  def masked(j: Json, key: String = ""): Json =
    j.fold(
      Json.Null,
      Json.fromBoolean,
      n =>
        if Durations(key) then Json.fromString("MS")
        else if idKey(key) then Json.fromString("ID")
        else Json.fromJsonNumber(n),
      s =>
        if key == "sha256" || (idKey(key) && s.forall(_.isDigit) && s.nonEmpty) then
          Json.fromString(if key == "sha256" then "SHA" else "ID")
        else
          val inner = if s.startsWith("{") || s.startsWith("[") then RawJson.parse(s).toOption else None
          inner match
            case Some(p) if p.isObject || p.isArray => Json.fromString(masked(p, key).noSpaces)
            case _                                  => Json.fromString(maskText(s))
      ,
      a => Json.fromValues(a.map(masked(_, key))),
      o =>
        val digest = o("scheme").contains(Json.fromString("sha256"))
        Json.fromJsonObject(
          JsonObject.fromIterable(
            o.toList.sortBy(_._1).map((k, v) => k -> masked(v, if digest && k == "value" then "sha256" else k))
          )
        )
    )

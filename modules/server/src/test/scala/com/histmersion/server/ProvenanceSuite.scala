package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class ProvenanceSuite extends munit.FunSuite:
  import ProvenanceSuite.*

  private lazy val inserts: List[Insert] = Insert.all(Path.of("modules"))

  test("main code inserts into record tables, and the scan finds them"):
    assert(inserts.count(i => Records.contains(i.table)) >= Records.size, inserts.map(_.table).distinct.sorted)

  test("every table main code inserts into is a record table or one of the kinds that are not records"):
    val unclassified = inserts.map(_.table).distinct.filterNot(t => Records.contains(t) || NotRecords.contains(t))
    assertEquals(unclassified.sorted, Nil, "classify each here and in ADR 0029 section 3")

  test("the classification names each table once, and only tables main code inserts into"):
    val inserted = inserts.map(_.table).toSet
    val all = Records.toList ++ NotRecords.keys.toList
    assertEquals(all.diff(all.distinct), Nil)
    assertEquals(all.filterNot(inserted).sorted, Nil)

  test("every INSERT into a record table writes its creator"):
    val missing = inserts.filter(i => Records.contains(i.table)).filterNot(_.writesCreator).map(_.at)
    assertEquals(missing, Nil, "an INSERT into a record table names the creator column (ADR 0029 section 6)")

  test(
    "each one binds a creator the compiler demanded: the def's own, a waiting edge's, or a bundle's carried or unrecorded"
  ):
    val unbound = inserts.filter(i => Records.contains(i.table) && i.writesCreator).filterNot(_.creatorBound).map(_.at)
    assertEquals(unbound, Nil)

  test("the scan reads an INSERT's columns, its def's signature and what it binds"):
    val text =
      """object R:
        |  def put(x: Int)(using DbCon)(using creator: Creator.New): Unit =
        |    sql\"\"\"INSERT INTO sources (citekey, creator) VALUES ($x, ${StoredCreator.column(creator)}::jsonb)\"\"\"
        |  def bad(x: Int)(using DbCon): Unit =
        |    sql\"\"\"INSERT INTO public.findings (id) VALUES ($x)\"\"\"
        |  def in(a: Bundle.Asset)(using DbCon): Unit =
        |    sql\"\"\"INSERT INTO assets (uid, creator) VALUES (${a.uid}, ${StoredCreator.carried(a.creator)}::jsonb)\"\"\"
        |  def guessed(a: Bundle.Asset)(using DbCon): Unit =
        |    sql\"\"\"INSERT INTO assets (uid, creator) VALUES (${a.uid}, ${someone}::jsonb)\"\"\"
        |""".stripMargin.replace("\\\"", "\"")
    val found = Insert.of(Path.of("R.scala"), text)
    assertEquals(found.map(_.table), List("sources", "findings", "assets", "assets"))
    assertEquals(found.map(_.writesCreator), List(true, false, true, true))
    assertEquals(found.map(_.creatorBound), List(true, false, true, false))

object ProvenanceSuite:

  val Records: Set[String] = Set(
    "sources",
    "findings",
    "edges",
    "unresolved_edges",
    "assessments",
    "source_candidates",
    "candidate_watches",
    "articles",
    "article_versions",
    "review_ledgers",
    "assets",
    "asset_links",
    "events",
    "places",
    "periods",
    "attestations",
    "corrections",
    "place_overrides",
    "event_link_candidates",
    "queue_items",
    "goals",
    "goal_links",
    "goal_schedules",
    "approvals",
    "jobs",
    "agent_managers",
    "manager_hints",
    "notes",
    "bookmarks"
  )

  enum NotRecord derives CanEqual:
    case PartOfRecord, Derived, Identity, State, Log

  val NotRecords: Map[String, NotRecord] =
    List(
      NotRecord.PartOfRecord -> List(
        "event_datings",
        "event_edges",
        "event_participants",
        "place_names",
        "place_links",
        "place_relations",
        "place_types",
        "place_descriptions",
        "media_links",
        "article_links",
        "article_critique_reasons",
        "source_identifiers",
        "finding_places",
        "manager_items",
        "manager_hint_uses",
        "goal_focus"
      ),
      NotRecord.Derived -> List(
        "source_chunks",
        "place_footprints",
        "site_undated_cells",
        "site_undated_build",
        "land",
        "map_changes"
      ),
      NotRecord.Identity -> List(
        "source_aliases",
        "article_aliases",
        "goal_aliases",
        "event_aliases",
        "place_aliases",
        "uid_aliases",
        "tombstones",
        "alias_tombstones",
        "place_removals"
      ),
      NotRecord.State -> List(
        "agent_state",
        "agent_leases",
        "agent_allowances",
        "agent_autopilot",
        "agent_role_chains",
        "agent_role_fallthrough",
        "upstream_quota",
        "plan_pools",
        "plan_quota_readings",
        "plan_quota_refreshes",
        "ui_seen",
        "gaz_seen",
        "retraction_watch",
        "seed_versions",
        "media_checks",
        "event_link_checks",
        "event_import_parts",
        "gazetteer_sources",
        "event_catalogue",
        "acquire_attempts",
        "read_marks"
      ),
      NotRecord.Log -> List(
        "log_events",
        "manager_events",
        "agent_steps",
        "agent_messages",
        "merges",
        "merge_sessions",
        "merge_ledger",
        "merge_decisions",
        "flyway_schema_history"
      )
    ).flatMap((kind, tables) => tables.map(_ -> kind)).toMap

  final case class Insert(path: String, line: Int, table: String, statement: String, signature: String):
    def at: String = s"$path:$line"

    def writesCreator: Boolean =
      statement.dropWhile(_ != '(').takeWhile(_ != ')').split("[(,\\s]+").contains("creator")

    def creatorBound: Boolean =
      signature.contains("Creator.New") || signature.contains(": Creator") ||
        statement.replaceAll("\\s+", "").contains("StoredCreator.write(Creator.unrecorded)") ||
        statement
          .replaceAll("\\s+", "")
          .matches("(?s).*\\$\\{StoredCreator\\.carried\\(\\w+\\.creator\\)\\}::jsonb.*") ||
        statement.matches("(?s).*\\bcreator\\s+FROM\\s+(public\\.)?unresolved_edges\\b.*")

  object Insert:
    private val InsertInto = "INSERT INTO\\s+(?:public\\.)?([a-z_]+)".r
    private val Def = "(?m)^\\s*(?:(?:private|protected|override|final|inline)(?:\\[[\\w.]+\\])?\\s+)*def\\s".r

    def all(modules: Path): List[Insert] =
      Files
        .walk(modules)
        .iterator
        .asScala
        .filter(p => p.toString.endsWith(".scala") && p.toString.contains("/src/main/"))
        .toList
        .sortBy(_.toString)
        .flatMap(p => of(p, Files.readString(p, UTF_8)))

    def of(p: Path, text: String): List[Insert] =
      InsertInto
        .findAllMatchIn(text)
        .map { m =>
          val defStart = Def.findAllMatchIn(text.substring(0, m.start)).map(_.start).toList.lastOption.getOrElse(0)
          val end = text.indexOf("\"\"\"", m.start) match
            case -1 => math.min(text.length, m.start + 2000)
            case e  => e
          Insert(
            p.toString,
            text.substring(0, m.start).count(_ == '\n') + 1,
            m.group(1),
            text.substring(m.start, end),
            text.substring(defStart, m.start)
          )
        }
        .toList

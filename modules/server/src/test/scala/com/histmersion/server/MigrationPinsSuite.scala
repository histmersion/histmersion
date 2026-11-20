package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

/** G93: an applied migration never changes. Each Flyway migration's SHA-256 is pinned in `architecture/migrations.tsv`;
  * this suite fails when a pinned file's bytes change, when a pinned file disappears, and when a migration has no pin.
  *
  * A database that ran a migration keeps its old result whatever the file says now: the squashed `V109__schema.sql` is
  * baselined with no checksum on a database the old chain built, so Flyway would neither re-run nor refuse an edited
  * copy, and a column added there would exist on fresh databases only (notes/fable-architecture-review.md A5).
  *
  *   - A new migration appends its pin: `HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *MigrationPinsSuite'`.
  *     The update mode only appends; it refuses while a pinned file changed or went away, and it creates the file only
  *     when git has never had it (a deleted file is restored from git, never regenerated: that would pin any edit).
  *   - History may still be rewritten on purpose before publishing (owner decision: no compatibility engineering), but
  *     only explicitly: the owner's `notes/accept-repin-migrations.sh` re-pins the named files and appends an accepted
  *     row for each to `architecture/accepted-rises.tsv`. Against the merge base with the pilot, a pin the base has
  *     that is changed or gone here fails unless such a row, added on this branch, names it.
  */
class MigrationPinsSuite extends munit.FunSuite:
  import MigrationPinsSuite.*

  test("every migration is pinned, and no pinned migration changed or went away"):
    val actual = Pins.of(MigrationsDir)
    val tracked = RatchetBase.Git(Path.of(".")).show("HEAD", PinsPath).isDefined
    val update = sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1")
    if !Files.isRegularFile(PinsFile) then
      assert(
        update && !tracked,
        s"$PinsPath is missing: restore it from git (git checkout -- $PinsPath); it is never regenerated, since a " +
          "regenerated file would pin any edit of an applied migration"
      )
      val _ = Files.write(PinsFile, Pins.render(actual).asJava, UTF_8)
    else
      val pinned = Pins.parse(Files.readAllLines(PinsFile, UTF_8).asScala.toList).fold(e => fail(e), identity)
      if update then
        Pins.appended(pinned, actual) match
          case Left(drifts) => fail(Pins.describe(drifts))
          case Right(next)  => if next != pinned then { val _ = Files.write(PinsFile, Pins.render(next).asJava, UTF_8) }
      else
        val wrong = Pins.check(pinned, actual)
        assert(wrong.isEmpty, Pins.describe(wrong))

  test("a pin the merge base has is unchanged here, unless the owner's accepted row re-pins it"):
    val git = RatchetBase.Git(Path.of("."))
    val outcome =
      for
        base <- git.base(sys.env)
        before <- Pins.parse(git.show(base.commit, PinsPath).getOrElse(Nil))
        now <-
          if Files.isRegularFile(PinsFile) then Pins.parse(Files.readAllLines(PinsFile, UTF_8).asScala.toList)
          else Right(Vector.empty)
        accepted <- RatchetBase.Approvals.AddedInBranch(git).granted(base)
      yield Pins.unaccepted(before, now, accepted.filter(_.file == PinsPath).map(_.key))
    outcome match
      case Left(why)   => fail(why)
      case Right(Nil)  => ()
      case Right(left) =>
        fail(
          (s"$PinsPath changes the pins of applied migrations against the merge base:" :: left.map(p =>
            s"  ${p.file}: ${p.sha256}"
          ) ++ List(
            "An applied migration is immutable (G93): add a new migration instead. A deliberate rewrite of history is " +
              "re-pinned by the owner's notes/accept-repin-migrations.sh, which writes the accepted row."
          )).mkString("\n")
        )

  test("the pins file reads back what it was written from, in the migrations' order"):
    val pins = Vector(Pin("V110__problems_as_entries.sql", "b" * 64), Pin("V109__schema.sql", "a" * 64))
    val rendered = Pins.render(pins)
    assert(rendered.head.startsWith("#"))
    assertEquals(Pins.parse(rendered), Right(Vector(pins(1), pins(0))))
    assert(summon[Ordering[(String, String)]].lt(Pins.order("V2__b.sql"), Pins.order("V10__a.sql")))
    assert(summon[Ordering[(String, String)]].lt(Pins.order("V1.2__b.sql"), Pins.order("V1.10__a.sql")))
    assert(Pins.parse(List("V1__x.sql\tnot-a-hash")).isLeft, "a malformed row is an error, never a row skipped")
    assert(Pins.parse(List("V1__x.sql\t" + "a" * 64, "V1__x.sql\t" + "b" * 64)).isLeft, "a file pinned twice")

  test("a changed, a gone and an unpinned migration are each found; an unchanged one is not"):
    val pinned = Vector(Pin("V1__a.sql", "1" * 64), Pin("V2__b.sql", "2" * 64), Pin("V3__c.sql", "3" * 64))
    val actual = Vector(Pin("V1__a.sql", "1" * 64), Pin("V2__b.sql", "9" * 64), Pin("V4__d.sql", "4" * 64))
    assertEquals(
      Pins.check(pinned, actual),
      List(
        Drift.Changed("V2__b.sql", "2" * 64, "9" * 64),
        Drift.Gone("V3__c.sql"),
        Drift.Unpinned("V4__d.sql", "4" * 64)
      )
    )
    assertEquals(Pins.check(pinned, pinned), Nil)

  test("the update mode appends a new migration's pin and refuses to change or drop an old one"):
    val pinned = Vector(Pin("V1__a.sql", "1" * 64))
    assertEquals(
      Pins.appended(pinned, Vector(Pin("V1__a.sql", "1" * 64), Pin("V2__b.sql", "2" * 64))),
      Right(Vector(Pin("V1__a.sql", "1" * 64), Pin("V2__b.sql", "2" * 64)))
    )
    assertEquals(
      Pins.appended(pinned, Vector(Pin("V1__a.sql", "9" * 64))),
      Left(List(Drift.Changed("V1__a.sql", "1" * 64, "9" * 64)))
    )
    assertEquals(Pins.appended(pinned, Vector.empty), Left(List(Drift.Gone("V1__a.sql"))))

  test("against the base: a changed or dropped pin needs an accepted row naming its new pin; a new pin needs none"):
    val before = Vector(Pin("V1__a.sql", "1" * 64), Pin("V2__b.sql", "2" * 64))
    val now = Vector(Pin("V1__a.sql", "8" * 64), Pin("V3__c.sql", "3" * 64))
    assertEquals(
      Pins.unaccepted(before, now, Nil),
      List(Pin("V1__a.sql", "8" * 64), Pin("V2__b.sql", Pins.GoneMark))
    )
    assertEquals(
      Pins.unaccepted(before, now, List(List("V1__a.sql", "8" * 64), List("V2__b.sql", Pins.GoneMark))),
      Nil
    )
    assertEquals(Pins.unaccepted(before, before :+ Pin("V3__c.sql", "3" * 64), Nil), Nil)

  test("a migration's pin is the SHA-256 of its bytes"):
    assertEquals(
      Pins.sha256("abc".getBytes(UTF_8)),
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    )

object MigrationPinsSuite:

  val PinsPath: String = "architecture/migrations.tsv"
  val PinsFile: Path = Path.of(PinsPath)
  val MigrationsDir: Path = Path.of("modules/db/src/main/resources/db/migration")

  /** A migration's file name and the SHA-256 (hex) of its bytes. */
  final case class Pin(file: String, sha256: String) derives CanEqual

  /** How a migration differs from its pin. */
  enum Drift derives CanEqual:
    case Changed(file: String, pinned: String, now: String)
    case Gone(file: String)
    case Unpinned(file: String, now: String)

    def describe: String = this match
      case Changed(f, p, n) => s"  $f changed: pinned $p, now $n"
      case Gone(f)          => s"  $f is pinned but gone"
      case Unpinned(f, n)   => s"  $f is not pinned (now $n)"

  object Pins:
    val Header: String =
      "# file\tsha256 — the Flyway migrations as applied (G93): a pinned file never changes. A new migration is " +
        "appended by HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *MigrationPinsSuite'; a deliberate " +
        "rewrite of history is re-pinned only by the owner's notes/accept-repin-migrations.sh"

    /** The key an accepted row names for a pin dropped on purpose. */
    val GoneMark: String = "gone"

    private val Hex = "[0-9a-f]{64}".r
    private val Version = "^[VR]([0-9]+(?:[._][0-9]+)*)__.*".r

    /** Flyway's order: the version's numbers (each padded, so they compare as numbers), then the name; a file with no
      * version last.
      */
    def order(file: String): (String, String) = file match
      case Version(v) => (v.split("[._]").map(n => "0" * math.max(0, 20 - n.length) + n).mkString("."), file)
      case _          => ("~", file)

    def sha256(bytes: Array[Byte]): String =
      MessageDigest.getInstance("SHA-256").digest(bytes).map(b => f"${b & 0xff}%02x").mkString

    /** The migrations under `dir`, each pinned to its bytes now. */
    def of(dir: Path): Vector[Pin] =
      val stream = Files.list(dir)
      try
        stream.iterator.asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".sql"))
          .map(p => Pin(p.getFileName.toString, sha256(Files.readAllBytes(p))))
          .toVector
          .sortBy(p => order(p.file))
      finally stream.close()

    def render(pins: Vector[Pin]): List[String] =
      Header :: pins.sortBy(p => order(p.file)).map(p => s"${p.file}\t${p.sha256}").toList

    /** The file's pins; a malformed row, or a file pinned twice, is an error, never a row skipped. */
    def parse(lines: List[String]): Either[String, Vector[Pin]] =
      val rows = lines.filterNot(l => l.startsWith("#") || l.isBlank).map { l =>
        l.split("\t", -1).toList match
          case file :: hash :: Nil if file.nonEmpty && Hex.matches(hash) => Right(Pin(file, hash))
          case _ => Left(s"$PinsPath: malformed row (a file name and its SHA-256 in hex): $l")
      }
      rows.collectFirst { case Left(e) => e } match
        case Some(e) => Left(e)
        case None    =>
          val pins = rows.collect { case Right(p) => p }.toVector
          val twice = pins.groupBy(_.file).collect { case (f, ps) if ps.size > 1 => f }.toList.sorted
          Either.cond(twice.isEmpty, pins.sortBy(p => order(p.file)), s"$PinsPath pins twice: ${twice.mkString(", ")}")

    def check(pinned: Vector[Pin], actual: Vector[Pin]): List[Drift] =
      val now = actual.map(p => p.file -> p.sha256).toMap
      val known = pinned.map(_.file).toSet
      val wrong = pinned.toList.flatMap { p =>
        now.get(p.file) match
          case None                     => List(Drift.Gone(p.file))
          case Some(h) if h != p.sha256 => List(Drift.Changed(p.file, p.sha256, h))
          case Some(_)                  => Nil
      }
      wrong ++ actual.toList.filterNot(p => known(p.file)).map(p => Drift.Unpinned(p.file, p.sha256))

    /** The pins with the new migrations' appended; refused while a pinned one changed or went away. */
    def appended(pinned: Vector[Pin], actual: Vector[Pin]): Either[List[Drift], Vector[Pin]] =
      val drifts = check(pinned, actual)
      val added = drifts.collect { case Drift.Unpinned(f, h) => Pin(f, h) }
      val refused = drifts.filter {
        case Drift.Unpinned(_, _) => false
        case _                    => true
      }
      Either.cond(refused.isEmpty, pinned ++ added, refused)

    /** The base's pins changed (as the new pin) or dropped (as [[GoneMark]]) here that no accepted key names. */
    def unaccepted(before: Vector[Pin], now: Vector[Pin], accepted: List[List[String]]): List[Pin] =
      val here = now.map(p => p.file -> p.sha256).toMap
      before.toList
        .flatMap { p =>
          here.get(p.file) match
            case Some(h) if h == p.sha256 => None
            case Some(h)                  => Some(Pin(p.file, h))
            case None                     => Some(Pin(p.file, GoneMark))
        }
        .filterNot(p => accepted.contains(List(p.file, p.sha256)))

    def describe(drifts: List[Drift]): String =
      (s"applied migrations are immutable (G93), against $PinsPath:" :: drifts.map(_.describe) ++ List(
        "A new migration is pinned by HISTMERSION_UPDATE_ARCHITECTURE=1 sbt 'server/testOnly *MigrationPinsSuite' " +
          "(it only appends). A changed or removed migration is a new migration instead; a deliberate rewrite of " +
          "history is re-pinned by the owner's notes/accept-repin-migrations.sh."
      )).mkString("\n")

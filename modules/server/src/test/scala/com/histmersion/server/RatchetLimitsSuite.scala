package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.LocalDate
import scala.jdk.CollectionConverters.*

import com.histmersion.server.RatchetBase.{Accepted, Base, Git, Rise, Spec}

/** [[RatchetBase]] over throwaway git repositories in the temporary directory, never the repository's own state: the
  * limits are the files at the merge base with the pilot, and only the owner's accepted rises pass above them.
  */
class RatchetLimitsSuite extends munit.FunSuite:
  import RatchetLimitsSuite.*

  private val Ratchet = Spec.GuidelineRatchet
  private val Orders = Spec.TotalOrder
  private val Header = "# rule\tmodule\tscope\tcount"
  private val NoVariable = Map.empty[String, String]

  test("a count the branch raises above the base fails, written in the working tree or committed"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2", "G49\tweb\tmain\t5"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3", "G49\tweb\tmain\t5")
    val why = Ratchet.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains("G44\tcore\tmain: 2 at the base -> 3 here"), why)
    assert(why.contains("needs the owner's accept script"), why)
    assert(!why.contains("G49"), why)
    r.commit("raise")
    assert(Ratchet.checkFile(r.git, NoVariable).isLeft, "committed in the branch")

  test("a fall written in the branch passes, and so does a row gone"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t3", "G49\tweb\tmain\t5"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t2")
    r.commit("fall")
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(Nil))

  test("a deleted ratchet file fails, instead of being regenerated at any count"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t3"))
    r.branch("work")
    Files.delete(r.dir.resolve(Ratchet.path))
    val why = Ratchet.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains(s"${Ratchet.path} is missing"), why)

  test("a key the base does not list starts at zero there, and a file the base does not have lists none"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t3"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3", "silentPure\tcli\ttest\t1")
    val why = Ratchet.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains("silentPure\tcli\ttest: not at the base -> 1 here"), why)
    assertEquals(
      RatchetBase.rises("f", Map(List("a") -> 2), Map(List("a") -> 2, List("b") -> 0, List("c") -> 1)),
      List(Rise("f", List("c"), 0, 1))
    )
    val fresh = Repo.pilot("README" -> List("x"))
    fresh.branch("work")
    fresh.write(Orders.path, "db:x/A.scala\tid\treviewed")
    assert(Orders.checkFile(fresh.git, NoVariable).swap.exists(_.contains("a row not at the base")))

  test("a rise passes when the branch adds the owner's accepted row for it, up to its delta, and only then"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3")
    def withRows(rows: Accepted*) =
      r.write(RatchetBase.AcceptedFile, Accepted.Header :: rows.map(_.row).toList*)
      Ratchet.checkFile(r.git, NoVariable)
    assertEquals(withRows(g44(1)), Right(List(g44(1))))
    assertEquals(withRows(g44(2)), Right(List(g44(2))), "a delta above the rise")
    assert(withRows(g44(1, key = List("G44", "core", "test"))).isLeft, "another key's row")
    assert(withRows(g44(1, file = Spec.Contracts.path)).isLeft, "another file's row")
    val used = withRows(g44(1)).getOrElse(Nil)
    val banner = RatchetBase.announce(used).getOrElse("")
    assert(banner.contains("RISES ACCEPTED"), banner)
    assert(banner.contains("G44\tcore\tmain +1 over the base (2026-11-19: the honest rise)"), banner)
    assertEquals(RatchetBase.announce(Nil), None)

  test("a rise larger than the accepted delta fails, and says how far the rows allow"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t5")
    r.write(RatchetBase.AcceptedFile, Accepted.Header, g44(2).row)
    val why = Ratchet.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains("G44\tcore\tmain: 2 at the base -> 5 here (+3; the accepted rows allow +2)"), why)

  test("a rebased branch whose own rise is unchanged keeps passing when the pilot's count moved under it"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3")
    r.write(RatchetBase.AcceptedFile, Accepted.Header, g44(1).row)
    r.commit("the lane's rise, +1 over 2")
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(List(g44(1))))
    // another lane merges first: the pilot's count is 10, with that lane's own row
    val _ = r.must("checkout", "-q", "contracts3-pilot")
    val other = g44(8, reason = "the other lane")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t10")
    r.write(RatchetBase.AcceptedFile, Accepted.Header, other.row)
    r.commit("the other lane, +8")
    // the lane rebased: its rise is still +1, now over 10; its row (and the first form of it) still approves it
    val _ = r.must("checkout", "-q", "-B", "work", "contracts3-pilot")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t11")
    r.write(RatchetBase.AcceptedFile, Accepted.Header, other.row, g44(1).row)
    r.commit("the lane's rise, rebased")
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(List(g44(1))))
    val firstForm = s"${Ratchet.path}\tG44\\tcore\\tmain\t2\t3\tthe honest rise\t2026-11-19"
    r.write(RatchetBase.AcceptedFile, Accepted.Header, other.row, firstForm)
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(List(g44(1))), "the row measured at the old base")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t12")
    assert(Ratchet.checkFile(r.git, NoVariable).isLeft, "the lane's rise grew to +2 in the rebase")

  test("a key missing from the base counts as 0: the delta covers the whole new count"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t2", "silentPure\tcli\ttest\t3")
    val key = List("silentPure", "cli", "test")
    r.write(RatchetBase.AcceptedFile, Accepted.Header, g44(3, key = key).row)
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(List(g44(3, key = key))))
    r.write(RatchetBase.AcceptedFile, Accepted.Header, g44(2, key = key).row)
    val why = Ratchet.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains("silentPure\tcli\ttest: not at the base -> 3 here (+3; the accepted rows allow +2)"), why)

  test("a fall needs no row, and a row for a key that fell is not used"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t5"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3")
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(Nil))
    r.write(RatchetBase.AcceptedFile, Accepted.Header, g44(1).row)
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(Nil))

  test("two rows the branch adds for one key sum: each accept script approves its own part of the rise"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2"))
    r.branch("work")
    val first = g44(1, reason = "the first script")
    val second = g44(2, reason = "the second script")
    r.write(RatchetBase.AcceptedFile, Accepted.Header, first.row, second.row)
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t5")
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(List(first, second)))
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t6")
    val why = Ratchet.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains("(+4; the accepted rows allow +3)"), why)
    assertEquals(Rise(Ratchet.path, first.key, 2, 6).allowance(List(first, second)), 3)

  test("an accepted row already at the base grants nothing: only the branch that adds a row is approved by it"):
    val row = Accepted(Ratchet.path, List("G44", "core", "main"), 1, "old", LocalDate.of(2026, 1, 1))
    val r = Repo.pilot(
      Ratchet.path -> List(Header, "G44\tcore\tmain\t2"),
      RatchetBase.AcceptedFile -> List(Accepted.Header, row.row)
    )
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3")
    assert(Ratchet.checkFile(r.git, NoVariable).isLeft)

  test("a reviewed row added or changed in the branch fails, one removed passes, a second copy fails"):
    val a = "db:x/A.scala\tid\tid is the key"
    val b = "db:x/B.scala\tlabel\tties do not matter: a picker"
    val r = Repo.pilot(Orders.path -> List("# file\tclause\twhy", a, b))
    r.branch("work")
    r.write(Orders.path, "# file\tclause\twhy", a, "db:x/B.scala\tlabel\tchanged reason")
    val changed = Orders.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(changed.contains("a row not at the base (new, or changed): db:x/B.scala\tlabel\tchanged reason"), changed)
    r.write(Orders.path, "# file\tclause\twhy", b)
    assertEquals(Orders.checkFile(r.git, NoVariable), Right(Nil))
    r.write(Orders.path, "# file\tclause\twhy", a, a, b)
    val copied = Orders.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(copied.contains("1 copies at the base -> 2"), copied)

  test("a counted row's key changed in the branch (a moved violation) is a new row"):
    val spec = Spec.Contracts
    val r = Repo.pilot(spec.path -> List("# rule\tfile\tdetail\tcount", "copy\tcore:x/A.scala\t.copy(\t2"))
    r.branch("work")
    r.write(spec.path, "# rule\tfile\tdetail\tcount", "copy\tcore:y/A.scala\t.copy(\t2")
    val why = spec.checkFile(r.git, NoVariable).swap.getOrElse("")
    assert(why.contains("copy\tcore:y/A.scala\t.copy(: not at the base -> 2 here"), why)

  test("a boundaries row is reviewed text; its header line is none"):
    assertEquals(
      Spec.Boundaries.counts(List("path\tkind\treason", "m/A.scala\tcli-args\tparses", "")),
      Right(Map(List("m/A.scala", "cli-args", "parses") -> 1))
    )

  test("a malformed row is an error, never a row skipped"):
    assert(Ratchet.counts(List("G44\tcore\tmain\tmany")).isLeft)
    assert(Ratchet.counts(List("G44\tcore\t3")).isLeft)
    assert(Accepted.parse(List("a\tb\t1\t\t2026-11-19")).isLeft, "no reason")
    assert(Accepted.parse(List("a\tb\t1\twhy\tyesterday")).isLeft, "no date")
    assert(Accepted.parse(List("a\tb\t0\twhy\t2026-11-19")).isLeft, "a delta of zero")
    assert(Accepted.parse(List("a\tb\t-2\twhy\t2026-11-19")).isLeft, "a negative delta")
    assert(Accepted.parse(List("a\tb\tmany\twhy\t2026-11-19")).isLeft, "no number")
    assert(Accepted.parse(List("a\tb\t1\t2\t\t2026-11-19")).isLeft, "the first form, no reason")
    assert(Accepted.parse(List("a\tb\t3\t3\twhy\t2026-11-19")).isLeft, "the first form, no rise")
    assert(Accepted.parse(List("a\tb\t1\twhy")).isLeft, "four cells")

  test("an accepted row reads back what it was written from, tabs and backslashes in its key and reason"):
    val a = Accepted("f.tsv", List("db:x/A.scala", "${rank(sql\"\")} \\ t"), 1, "a\tb\\c", LocalDate.of(2026, 11, 19))
    assertEquals(Accepted.parse(List(Accepted.Header, a.row)), Right(List(a)))
    assertEquals(a.row.split("\t", -1).length, 5)

  test("a first-form row (from, to) reads as the delta to - from, and the rewrite keeps every row's value"):
    val old = List(
      "# file\tkey\tfrom\tto\treason\tdate — the first form's header",
      s"${Ratchet.path}\tG89unguarded\\tdb\\tmain\t0\t9\tnew rule G89\t2026-11-20",
      s"${Ratchet.path}\tG44\\tserver\\ttest\t3960\t3971\tweb safety\t2026-11-20",
      s"${Ratchet.path}\tG44\\tserver\\ttest\t3960\t3971\tweb safety\t2026-11-20"
    )
    val read = Accepted.parse(old)
    val g89 = List("G89unguarded", "db", "main")
    val serverTest = List("G44", "server", "test")
    assertEquals(read.map(_.map(a => (a.key, a.delta))), Right(List(g89 -> 9, serverTest -> 11, serverTest -> 11)))
    val rewritten = Accepted.rewrite(old)
    assertEquals(rewritten.map(_.head), Right(Accepted.Header))
    assertEquals(rewritten.map(_(1)), Right(s"${Ratchet.path}\tG89unguarded\\tdb\\tmain\t9\tnew rule G89\t2026-11-20"))
    assertEquals(rewritten.flatMap(Accepted.parse), read)
    assertEquals(rewritten.flatMap(Accepted.rewrite), rewritten, "a second rewrite changes nothing")
    assert(Accepted.rewrite(old :+ "a\tb\t3\t3\twhy\t2026-11-20").isLeft, "a malformed row stops the rewrite")

  test("the rewrite tool writes the delta form in place, and writes nothing when a row is malformed"):
    val dir = Files.createTempDirectory("accepted-rises")
    val file = dir.resolve("accepted-rises.tsv")
    Repo.writeLines(file, List("# first form", s"${Ratchet.path}\tG44\\tcore\\tmain\t2\t5\twhy\t2026-11-20"))
    assertEquals(AcceptedRisesDelta.rewriteFile(file), Right(1))
    assertEquals(
      Repo.readLines(file),
      List(Accepted.Header, s"${Ratchet.path}\tG44\\tcore\\tmain\t3\twhy\t2026-11-20")
    )
    val bad = List("# first form", "a\tb\t3\t3\twhy\t2026-11-20")
    Repo.writeLines(file, bad)
    assert(AcceptedRisesDelta.rewriteFile(file).isLeft)
    assertEquals(Repo.readLines(file), bad)

  test("rewriting the base's first-form rows on a branch grants nothing: the rows read back equal"):
    val r = Repo.pilot(
      Ratchet.path -> List(Header, "G44\tcore\tmain\t2"),
      RatchetBase.AcceptedFile -> List("# first form", s"${Ratchet.path}\tG44\\tcore\\tmain\t1\t2\told\t2026-11-19")
    )
    r.branch("work")
    val rewritten = Accepted.rewrite(Repo.readLines(r.dir.resolve(RatchetBase.AcceptedFile))).getOrElse(Nil)
    r.write(RatchetBase.AcceptedFile, rewritten*)
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3")
    assert(Ratchet.checkFile(r.git, NoVariable).isLeft, "the base's row, rewritten, is not the branch's")
    r.write(RatchetBase.AcceptedFile, rewritten :+ g44(1).row*)
    assertEquals(Ratchet.checkFile(r.git, NoVariable), Right(List(g44(1))))

  test("without a git repository the check fails and says why"):
    val dir = Files.createTempDirectory("ratchet-no-git")
    Repo.writeLines(dir.resolve(Ratchet.path), List(Header, "G44\tcore\tmain\t2"))
    val why = Ratchet.checkFile(Git(dir, Repo.isolated(dir)), NoVariable).swap.getOrElse("")
    assert(why.contains("not a git repository"), why)

  test("the base is the merge base with the variable's ref, else the pilot's, else origin's pilot, else a failure"):
    val r = Repo.pilot("README" -> List("x"))
    val forked = r.head
    r.branch("work")
    r.write("README", "y")
    r.commit("work")
    val _ = r.must("checkout", "-q", "contracts3-pilot")
    r.write("README", "z")
    r.commit("pilot moves on")
    val _ = r.must("checkout", "-q", "work")
    assertEquals(r.git.base(NoVariable), Right(Base(forked, "contracts3-pilot")))
    val _ = r.must("branch", "-q", "-m", "contracts3-pilot", "elsewhere")
    assert(r.git.base(NoVariable).isLeft, "neither the pilot nor origin's")
    val _ = r.must("update-ref", "refs/remotes/origin/contracts3-pilot", forked)
    assertEquals(r.git.base(NoVariable), Right(Base(forked, "origin/contracts3-pilot")))
    assertEquals(r.git.base(Map(RatchetBase.BaseVariable -> "elsewhere")), Right(Base(forked, "elsewhere")))
    val bogus = r.git.base(Map(RatchetBase.BaseVariable -> "no-such-ref"))
    assert(bogus.swap.exists(_.contains("HISTMERSION_RATCHET_BASE=no-such-ref")), bogus)
    val _ = r.must("update-ref", "-d", "refs/remotes/origin/contracts3-pilot")
    val none = r.git.base(NoVariable)
    assert(none.swap.exists(_.contains("no merge base of HEAD and contracts3-pilot")), none)

object RatchetLimitsSuite:

  /** An accepted row of the guideline ratchet, by default for `G44 core main`. */
  def g44(
      delta: Int,
      key: List[String] = List("G44", "core", "main"),
      file: String = Spec.GuidelineRatchet.path,
      reason: String = "the honest rise"
  ): Accepted = Accepted(file, key, delta, reason, LocalDate.of(2026, 11, 19))

  /** A throwaway repository whose first commit is on `contracts3-pilot`; git reads no global or system configuration
    * (no signing, no hooks) and never climbs out of the temporary directory.
    */
  final case class Repo(dir: Path):
    import Repo.{isolated, writeLines}

    val git: Git = Git(dir, isolated(dir))

    def must(args: String*): String =
      val (code, out) = git.run(args*)
      assert(code == 0, s"git ${args.mkString(" ")} exited $code")
      out

    def write(path: String, lines: String*): Unit = writeLines(dir.resolve(path), lines.toList)

    def commit(message: String): Unit =
      val _ = must("add", "-A")
      val _ = must("commit", "-q", "--no-verify", "-m", message)

    def branch(name: String): Unit =
      val _ = must("checkout", "-q", "-b", name)

    def head: String = must("rev-parse", "HEAD").trim

  object Repo:
    def isolated(dir: Path): Map[String, String] = Map(
      "GIT_CONFIG_GLOBAL" -> "/dev/null",
      "GIT_CONFIG_NOSYSTEM" -> "1",
      "GIT_CEILING_DIRECTORIES" -> dir.toRealPath().getParent.toString,
      "GIT_AUTHOR_NAME" -> "t",
      "GIT_AUTHOR_EMAIL" -> "t@example.invalid",
      "GIT_COMMITTER_NAME" -> "t",
      "GIT_COMMITTER_EMAIL" -> "t@example.invalid"
    )

    def writeLines(file: Path, lines: List[String]): Unit =
      Files.createDirectories(file.getParent)
      val _ = Files.writeString(file, lines.map(_ + "\n").mkString, UTF_8)

    def readLines(file: Path): List[String] = Files.readAllLines(file, UTF_8).asScala.toList

    def pilot(files: (String, List[String])*): Repo =
      val r = Repo(Files.createTempDirectory("ratchet-base"))
      val _ = r.must("init", "-q", "-b", "contracts3-pilot")
      files.foreach((path, lines) => writeLines(r.dir.resolve(path), lines))
      r.commit("base")
      r

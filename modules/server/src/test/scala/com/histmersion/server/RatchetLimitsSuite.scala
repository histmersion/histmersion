package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.LocalDate

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

  test("a rise passes when the branch adds the owner's accepted row for it, and only then"):
    val r = Repo.pilot(Ratchet.path -> List(Header, "G44\tcore\tmain\t2"))
    r.branch("work")
    r.write(Ratchet.path, Header, "G44\tcore\tmain\t3")
    def accept(from: Int, to: Int): Accepted =
      Accepted(Ratchet.path, List("G44", "core", "main"), from, to, "the honest rise", LocalDate.of(2026, 11, 19))
    def withRow(a: Accepted) =
      r.write(RatchetBase.AcceptedFile, Accepted.Header, a.row)
      Ratchet.checkFile(r.git, NoVariable)
    assertEquals(withRow(accept(2, 3)), Right(List(accept(2, 3))))
    assertEquals(withRow(accept(2, 4)), Right(List(accept(2, 4))))
    assert(withRow(accept(2, 2)).isLeft, "to below the count")
    assert(withRow(accept(1, 3)).isLeft, "from is not the base's count")
    assert(RatchetBase.announce(List(accept(2, 3))).exists(_.contains("RISES ACCEPTED")))
    assertEquals(RatchetBase.announce(Nil), None)

  test("an accepted row already at the base grants nothing: only the branch that adds a row is approved by it"):
    val row = Accepted(Ratchet.path, List("G44", "core", "main"), 2, 3, "old", LocalDate.of(2026, 1, 1))
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
    assert(Accepted.parse(List("a\tb\t1\t2\t\t2026-11-19")).isLeft, "no reason")
    assert(Accepted.parse(List("a\tb\t1\t2\twhy\tyesterday")).isLeft, "no date")

  test("an accepted row reads back what it was written from, tabs and backslashes in its key and reason"):
    val a =
      Accepted("f.tsv", List("db:x/A.scala", "${rank(sql\"\")} \\ t"), 0, 1, "a\tb\\c", LocalDate.of(2026, 11, 19))
    assertEquals(Accepted.parse(List(Accepted.Header, a.row)), Right(List(a)))
    assertEquals(a.row.split("\t", -1).length, 6)

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

    def pilot(files: (String, List[String])*): Repo =
      val r = Repo(Files.createTempDirectory("ratchet-base"))
      val _ = r.must("init", "-q", "-b", "contracts3-pilot")
      files.foreach((path, lines) => writeLines(r.dir.resolve(path), lines))
      r.commit("base")
      r

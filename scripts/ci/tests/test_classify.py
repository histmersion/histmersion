import contextlib
import importlib.util
import io
import os
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("classify", os.path.join(os.path.dirname(__file__), "..",
                                                                        "classify.py"))
classify = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(classify)

SOURCES = {
    "db/TestDb.scala": "package com.x\npackage db\n\nobject TestDb:\n  val Env = \"HISTMERSION_TEST_DB_URL\"\n",
    "db/DbSuite.scala": "package com.x.db\n\ntrait DbSuite extends munit.FunSuite:\n  val url = TestDb.url\n",
    "db/RepoSuite.scala": "package com.x.db\n\nclass RepoSuite extends DbSuite:\n  test(\"x\")(())\n",
    "db/PureSuite.scala": "package com.x.db\n\n// TestDb is not used here, and DbSuite only in this comment\n"
                          "class PureSuite extends munit.FunSuite:\n  val s = \"DbSuite in a string\"\n",
    "server/Fixtures.scala": "package com.x.server\n\nobject Fixtures:\n  def load() = com.x.db.RepoHelper()\n",
    "server/Helper.scala": "package com.x.db\n\nfinal case class RepoHelper():\n"
                           "  def url = sys.env.get(\"HISTMERSION_TEST_DB_URL\")\n",
    "server/HttpSuite.scala": "package com.x.server\n\nclass HttpSuite extends munit.FunSuite:\n  Fixtures.load()\n",
    "server/RepoSuite.scala": "package com.x.server\n\nclass RepoSuite extends munit.FunSuite:\n  test(\"y\")(())\n",
}


def read(d):
    for path, text in sorted(SOURCES.items()):
        if path.startswith(d + "/"):
            yield path, text


class ClassifyTest(unittest.TestCase):
    def test_parse_reads_chained_packages_top_level_names_and_uses(self):
        pkg, names, markers, used = classify.parse(SOURCES["db/TestDb.scala"])
        self.assertEqual((pkg, names, markers), ("com.x.db", ["TestDb"], {"TestDb", "HISTMERSION_TEST_DB_URL"}))
        _, _, markers, used = classify.parse(SOURCES["db/PureSuite.scala"])
        self.assertEqual(markers, set())
        self.assertNotIn("DbSuite", used)
        self.assertNotIn("PureSuite", used)

    def test_reaching_is_transitive(self):
        files = {p: classify.parse(t) for p, t in SOURCES.items()}
        self.assertEqual(classify.reaching(files), {"db/TestDb.scala", "db/DbSuite.scala", "db/RepoSuite.scala",
                                                    "server/Helper.scala", "server/Fixtures.scala",
                                                    "server/HttpSuite.scala"})

    def test_classify_suites(self):
        suites = [("db", "com.x.db.RepoSuite"), ("db", "com.x.db.PureSuite"), ("server", "com.x.server.HttpSuite"),
                  ("server", "com.x.server.RepoSuite"), ("server", "com.x.server.Gone")]
        got = classify.classify(suites, {"db": ["db"], "server": ["server"]}, read)
        self.assertEqual(got, {("db", "com.x.db.RepoSuite"): "it", ("db", "com.x.db.PureSuite"): "unit",
                               ("server", "com.x.server.HttpSuite"): "it", ("server", "com.x.server.RepoSuite"): "unit",
                               ("server", "com.x.server.Gone"): "unclassified"})

    def test_a_suite_in_the_empty_package(self):
        got = classify.classify([("core", "BuildSuite")], {"core": ["core"]},
                                lambda d: iter([("core/BuildSuite.scala", "class BuildSuite extends munit.FunSuite\n")]))
        self.assertEqual(got, {("core", "BuildSuite"): "unit"})

    def test_main_writes_the_classes_and_fails_on_an_unclassified_suite(self):
        with tempfile.TemporaryDirectory() as d:
            src = os.path.join(d, "src")
            os.makedirs(src)
            with open(os.path.join(src, "A.scala"), "w") as f:
                f.write("package p\n\nclass ASuite extends munit.FunSuite:\n  val u = TestDb.url\n")
            os.makedirs(f"{d}/suites")
            os.makedirs(f"{d}/sources")
            with open(f"{d}/suites/db.tsv", "w") as f:
                f.write("db\tp.ASuite\n")
            with open(f"{d}/sources/db.tsv", "w") as f:
                f.write(f"db\t{src}\n")
            out = f"{d}/classes.tsv"
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(classify.main([f"{d}/suites", f"{d}/sources", "--out", out]), 0)
                with open(out) as f:
                    self.assertEqual(f.read(), "db\tp.ASuite\tit\n")
                with open(f"{d}/suites/db.tsv", "a") as f:
                    f.write("db\tp.Missing\n")
                self.assertEqual(classify.main([f"{d}/suites", f"{d}/sources", "--out", out]), 1)


if __name__ == "__main__":
    unittest.main()

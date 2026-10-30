"""Tests of affected.py's pure mapping — no git, no sbt.

    python3 -m unittest scripts/dev/tests/test_affected.py      (also: just test-scripts)
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import affected  # noqa: E402

A = affected


class AffectedTest(unittest.TestCase):
    def test_a_leaf_module_tests_itself_only(self):
        # (and the lint suites of api and core, which read every module's main Scala — not their dependents)
        self.assertEqual(A.affected(["modules/cli/src/main/scala/com/histmersion/cli/Main.scala"]),
                         (["api", "config", "core", "cli"], False, ["modules/cli/src/main/scala/com/histmersion/cli/Main.scala → cli",
                          "api: its lint suites read modules/cli/src/main/scala/com/histmersion/cli/Main.scala",
                          "config: its lint suites read modules/cli/src/main/scala/com/histmersion/cli/Main.scala",
                          "core: its lint suites read modules/cli/src/main/scala/com/histmersion/cli/Main.scala"]))

    def test_api_shared_reaches_every_dependent(self):
        tests, db, _ = A.affected(["modules/api/src/main/scala/X.scala"])
        self.assertEqual(tests, ["api", "apiJS", "config", "files", "core", "pluginHost", "pluginTestkit", "db", "cli", "web", "server"])
        self.assertTrue(db)

    def test_core_reaches_its_dependents_not_web_nor_cli(self):
        tests, _, _ = A.affected(["modules/core/src/main/scala/X.scala"])
        self.assertEqual(tests, ["api", "config", "core", "pluginHost", "pluginTestkit", "db", "server"])

    def test_files_reach_core_and_cli(self):
        tests, _, _ = A.affected(["modules/files/src/main/scala/X.scala"])
        self.assertEqual(tests, ["api", "config", "files", "core", "pluginHost", "pluginTestkit", "db", "cli", "server"])

    def test_api_js_reaches_web_not_server(self):
        # (and the lint suites, which read every module's main Scala)
        self.assertEqual(A.affected(["modules/api/src/main/scalajs/Y.scala"])[0], ["api", "apiJS", "config", "core", "web"])

    def test_web_change_does_not_rerun_the_server(self):
        tests, db, _ = A.affected(["modules/web/src/main/scala/com/histmersion/web/Main.scala"])
        # the lint suites read the web sources (StyleLintSuite, MapEngineBoundarySuite)
        self.assertEqual(tests, ["api", "config", "core", "web"])
        self.assertFalse(db)

    def test_main_sources_are_read_by_cores_lints(self):
        self.assertIn("core", A.affected(["modules/cli/src/main/scala/com/histmersion/cli/Main.scala"])[0])
        self.assertIn("core", A.affected(["modules/server/src/main/scala/com/histmersion/server/Main.scala"])[0])
        # a test of its own, or a resource, is not
        self.assertEqual(A.affected(["modules/cli/src/test/scala/X.scala"])[0], ["cli"])

    def test_db_reaches_server(self):
        self.assertEqual(A.affected(["modules/db/src/main/resources/db/migration/V099__x.sql"])[0],
                         ["db", "server"])

    def test_plugin_api_reaches_vaults_and_server_but_fixtures_have_no_tests(self):
        tests, _, _ = A.affected(["modules/plugin-api/src/main/scala/P.scala"])
        self.assertEqual(tests, ["api", "config", "core", "pluginApi", "pluginHost", "pluginTestkit", "pluginVaults", "pluginResolver", "server"])
        self.assertEqual(A.affected(["modules/plugin-fixtures/hello/src/main/scala/H.scala"])[0], ["api", "config", "core", "server"])
        self.assertEqual(A.affected(["modules/plugin-fixtures/hello-ai/src/main/resources/x.conf"])[0], ["server"])
        # the fixture with a dependency is read by the resolver's suites and the server's
        self.assertEqual(A.affected(["modules/plugin-fixtures/hello-lib/src/main/scala/S.scala"])[0],
                         ["api", "config", "core", "pluginResolver", "server"])

    def test_build_definition_means_everything(self):
        for p in ["build.sbt", "project/Dependencies.scala", ".sdkmanrc", ".jvmopts"]:
            tests, db, _ = A.affected([p])
            self.assertEqual(tests, [m for m in A.ORDER if m not in A.NO_TESTS], p)
            self.assertTrue(db)

    def test_unknown_paths_are_safe_not_skipped(self):
        self.assertEqual(A.modules_for("somewhere/new.txt"), [A.ALL])
        self.assertEqual(A.modules_for("NEWFILE"), [A.ALL])

    def test_docs_and_scripts_need_no_sbt_suite(self):
        for p in ["docs/user-guide/index.md", "README.md", "AGENTS.md", "justfile",
                  "scripts/bundle/server.sh", "converter/app.py", "modules/web/test/perf.test.mjs",
                  ".github/workflows/ci.yml", "docs/research-build-speed.md"]:
            self.assertEqual(A.affected([p])[0], [], p)

    def test_files_the_suites_read(self):
        self.assertEqual(A.affected(["skills/histmersion-dev/SKILL.md"])[0], ["core", "pluginHost", "pluginTestkit", "db", "server"])
        self.assertEqual(A.affected(["docs/writing-guide.md"])[0], ["server"])
        self.assertEqual(A.affected(["docs/user-guide/reference/cli.md"])[0][0], "core")
        self.assertEqual(A.affected(["modules/web/test/fixtures/basemap/terrain.pmtiles"])[0], ["server"])
        self.assertIn("db", A.affected(["db/periods/periodo-seed.json"])[0])

    def test_leading_dot_slash_is_ignored(self):
        self.assertEqual(A.modules_for("./modules/cli/x.scala"), ["cli"])
        self.assertEqual(A.modules_for("./.sdkmanrc"), [A.ALL])

    def test_dependents_closure_is_transitive(self):
        self.assertEqual(A.dependents({"pluginApi"}),
                         {"pluginApi", "pluginHost", "pluginTestkit", "pluginVaults", "pluginHello", "pluginHelloAi", "pluginHelloDeps", "pluginResolver",
                          "server"})
        self.assertEqual(A.dependents(set()), set())

    def test_reasons_name_the_dependents(self):
        _, _, why = A.affected(["modules/db/src/main/scala/D.scala"])
        self.assertIn("server: depends on a changed module", why)

    def test_scripts_touched(self):
        self.assertTrue(A.scripts_touched(["scripts/bundle/relink.sh"]))
        self.assertTrue(A.scripts_touched(["db/places/build.py"]))
        self.assertFalse(A.scripts_touched(["modules/cli/x.scala"]))

    def test_every_module_has_a_place_in_the_order(self):
        self.assertEqual(sorted(A.ORDER), sorted(A.DEPS))
        for m, ds in A.DEPS.items():
            for d in ds:
                self.assertLess(A.ORDER.index(d), A.ORDER.index(m), f"{d} before {m}")


if __name__ == "__main__":
    unittest.main()

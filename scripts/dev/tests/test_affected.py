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
        self.assertEqual(A.affected(["modules/cli/src/main/scala/com/histmersion/cli/Main.scala"]),
                         (["cli"], False, ["modules/cli/src/main/scala/com/histmersion/cli/Main.scala → cli"]))

    def test_core_shared_reaches_every_dependent(self):
        tests, db, _ = A.affected(["modules/core/shared/src/main/scala/X.scala"])
        self.assertEqual(tests, ["coreJVM", "coreJS", "db", "cli", "web", "server"])
        self.assertTrue(db)

    def test_core_js_reaches_web_not_server(self):
        self.assertEqual(A.affected(["modules/core/js/src/main/scala/Y.scala"])[0], ["coreJS", "web"])

    def test_web_change_does_not_rerun_the_server(self):
        tests, db, _ = A.affected(["modules/web/src/main/scala/com/histmersion/web/Main.scala"])
        self.assertEqual(tests, ["web"])
        self.assertFalse(db)

    def test_db_reaches_server(self):
        self.assertEqual(A.affected(["modules/db/src/main/resources/db/migration/V099__x.sql"])[0],
                         ["db", "server"])

    def test_plugin_api_reaches_vaults_and_server_but_fixtures_have_no_tests(self):
        tests, _, _ = A.affected(["modules/plugin-api/src/main/scala/P.scala"])
        self.assertEqual(tests, ["pluginApi", "pluginVaults", "server"])
        self.assertEqual(A.affected(["modules/plugin-fixtures/hello/src/main/scala/H.scala"])[0], ["server"])
        self.assertEqual(A.affected(["modules/plugin-fixtures/hello-ai/src/main/scala/H.scala"])[0], ["server"])

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
        self.assertEqual(A.affected(["skills/histmersion-dev/SKILL.md"])[0], ["coreJVM", "db", "cli", "server"])
        self.assertEqual(A.affected(["docs/writing-guide.md"])[0], ["server"])
        self.assertEqual(A.affected(["docs/user-guide/reference/cli.md"])[0][0], "coreJVM")
        self.assertEqual(A.affected(["modules/web/test/fixtures/basemap/terrain.pmtiles"])[0], ["server"])
        self.assertIn("db", A.affected(["db/periods/periodo-seed.json"])[0])

    def test_leading_dot_slash_is_ignored(self):
        self.assertEqual(A.modules_for("./modules/cli/x.scala"), ["cli"])
        self.assertEqual(A.modules_for("./.sdkmanrc"), [A.ALL])

    def test_dependents_closure_is_transitive(self):
        self.assertEqual(A.dependents({"pluginApi"}),
                         {"pluginApi", "pluginVaults", "pluginHello", "pluginHelloAi", "server"})
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

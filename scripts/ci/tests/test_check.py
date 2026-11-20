"""scripts/ci/check.py: the shards' reports against the suites listed — each run exactly once."""
import importlib.util
import os
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("check", os.path.join(os.path.dirname(__file__), "..", "check.py"))
check = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(check)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


class CheckTest(unittest.TestCase):
    def test_compare_finds_missing_repeated_and_unexpected(self):
        expected = [("db", "A"), ("db", "B"), ("server", "C"), ("api", "D")]
        ran = {"db": [("db", "A")], "server-1": [("server", "C"), ("db", "A")], "pure": [("api", "D"), ("api", "D"),
                                                                                        ("api", "E")]}
        missing, repeated, unexpected = check.compare(expected, ran)
        self.assertEqual(missing, [("db", "B")])
        self.assertEqual(repeated, {("api", "D"): ["pure", "pure"], ("db", "A"): ["db", "server-1"]})
        self.assertEqual(unexpected, [("api", "E")])

    def test_compare_passes_an_exact_cover(self):
        expected = [("db", "A"), ("api", "A")]  # one name in two projects (JVM and JS) is two suites
        self.assertEqual(check.compare(expected, {"x": [("db", "A")], "y": [("api", "A")]}), ([], {}, []))

    def test_report_rows_parse_and_refuse_malformed(self):
        got = check.read_report(["db\tcom.x.A\tpassed\t3\t0\t0\t1\t0\n", "\n"])
        self.assertEqual(got, [("db", "com.x.A", "passed", {"passed": 3, "failed": 0, "errors": 0, "skipped": 1,
                                                           "ignored": 0})])
        with self.assertRaises(ValueError):
            check.read_report(["db\tcom.x.A\tpassed\n"])

    def test_main_fails_on_a_missing_suite_and_summarises_skips(self):
        with tempfile.TemporaryDirectory() as d:
            write(f"{d}/suites/db.tsv", "db\tcom.x.A\ndb\tcom.x.B\n")
            write(f"{d}/reports/db/db.tsv", "db\tcom.x.A\tpassed\t2\t0\t0\t1\t0\n")
            write(f"{d}/reports/db/db.skips.tsv", "db\tcom.x.A\tsome test\tno pg_dump\n")
            summary = f"{d}/summary.md"
            self.assertEqual(check.main([f"{d}/suites", f"{d}/reports", "--summary", summary]), 1)
            with open(summary, encoding="utf-8") as f:
                text = f.read()
            self.assertIn("`db` `com.x.B`", text)
            self.assertIn("| db | `A` | some test | no pg_dump |", text)
            write(f"{d}/reports/server-1/db.tsv", "db\tcom.x.B\tfailed\t0\t1\t0\t0\t0\n")
            # every suite ran once: the cover holds (the failure is the shard job's to report)
            self.assertEqual(check.main([f"{d}/suites", f"{d}/reports"]), 0)

    def test_main_fails_without_suites(self):
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(f"{d}/suites")
            os.makedirs(f"{d}/reports")
            self.assertEqual(check.main([f"{d}/suites", f"{d}/reports"]), 1)


if __name__ == "__main__":
    unittest.main()

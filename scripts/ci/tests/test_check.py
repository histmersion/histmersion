import contextlib
import importlib.util
import io
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


def quiet(f, *args):
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        return f(*args)


class CheckTest(unittest.TestCase):
    def test_compare_finds_every_kind_of_hole(self):
        classes = {("db", "A"): "it", ("db", "B"): "it", ("server", "C"): "unit", ("api", "D"): "unit",
                   ("server", "Z"): "unclassified"}
        ran = {"it-db": [("db", "A")], "it-server-1": [("server", "C"), ("db", "A")],
               "unit-modules": [("api", "D"), ("api", "D"), ("api", "E")]}
        unclassified, missing, repeated, unexpected, misplaced = check.compare(classes, ran)
        self.assertEqual(unclassified, [("server", "Z")])
        self.assertEqual(missing, [("db", "B")])
        self.assertEqual(repeated, {("api", "D"): ["unit-modules", "unit-modules"],
                                    ("db", "A"): ["it-db", "it-server-1"]})
        self.assertEqual(unexpected, [("api", "E")])
        self.assertEqual(misplaced, {("server", "C"): "it-server-1"})

    def test_compare_passes_an_exact_cover(self):
        classes = {("db", "A"): "it", ("api", "A"): "unit"}
        self.assertEqual(check.compare(classes, {"it-db": [("db", "A")], "unit-modules": [("api", "A")]}),
                         ([], [], {}, [], {}))

    def test_report_rows_parse_with_or_without_millis_and_refuse_malformed(self):
        got = check.read_report(["db\tcom.x.A\tpassed\t3\t0\t0\t1\t0\t1500\n", "db\tcom.x.B\tfailed\t0\t1\t0\t0\t0\n"])
        self.assertEqual(got[0], ("db", "com.x.A", "passed",
                                  {"passed": 3, "failed": 0, "errors": 0, "skipped": 1, "ignored": 0}, 1500))
        self.assertEqual(got[1][4], 0)
        with self.assertRaises(ValueError):
            check.read_report(["db\tcom.x.A\tpassed\n"])

    def test_main_fails_on_a_hole_and_summarises_skips(self):
        with tempfile.TemporaryDirectory() as d:
            write(f"{d}/classes.tsv", "db\tcom.x.A\tit\ndb\tcom.x.B\tunit\n")
            write(f"{d}/reports/report-it-db/db.tsv", "db\tcom.x.A\tpassed\t2\t0\t0\t1\t0\t2000\n")
            write(f"{d}/reports/report-it-db/db.skips.tsv", "db\tcom.x.A\tsome test\tno pg_dump\n")
            summary = f"{d}/summary.md"
            self.assertEqual(quiet(check.main, [f"{d}/classes.tsv", f"{d}/reports", "--summary", summary]), 1)
            with open(summary, encoding="utf-8") as f:
                text = f.read()
            self.assertIn("`db` `com.x.B`", text)
            self.assertIn("| it-db | `A` | some test | no pg_dump |", text)
            self.assertIn("| it-db | 1 | 3 | 2 | 0 | 1 |", text)
            write(f"{d}/reports/report-unit-db/db.tsv", "db\tcom.x.B\tfailed\t0\t1\t0\t0\t0\t10\n")
            self.assertEqual(quiet(check.main, [f"{d}/classes.tsv", f"{d}/reports"]), 0)
            write(f"{d}/reports/report-it-server-1/db.tsv", "db\tcom.x.B\tpassed\t1\t0\t0\t0\t0\t10\n")
            self.assertEqual(quiet(check.main, [f"{d}/classes.tsv", f"{d}/reports"]), 1)

    def test_main_fails_without_suites_or_with_an_unclassified_one(self):
        with tempfile.TemporaryDirectory() as d:
            write(f"{d}/classes.tsv", "")
            os.makedirs(f"{d}/reports")
            self.assertEqual(quiet(check.main, [f"{d}/classes.tsv", f"{d}/reports"]), 1)
            write(f"{d}/classes.tsv", "db\tcom.x.A\tunclassified\n")
            self.assertEqual(quiet(check.main, [f"{d}/classes.tsv", f"{d}/reports"]), 1)


if __name__ == "__main__":
    unittest.main()

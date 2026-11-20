"""scripts/ci/plan.py: every suite in exactly one shard of its class, the server's dealt by weight."""
import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("plan", os.path.join(os.path.dirname(__file__), "..", "plan.py"))
plan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(plan)


def rows(project, cls, *names):
    return [(project, f"com.x.{n}", cls) for n in names]


class PlanTest(unittest.TestCase):
    def test_classes_parse_and_refuse_malformed_lines(self):
        self.assertEqual(plan.read_classes(["# c\n", "\n", "db\tcom.x.A\tit\n"]), [("db", "com.x.A", "it")])
        with self.assertRaises(ValueError):
            plan.read_classes(["db\tcom.x.A\n"])
        with self.assertRaises(ValueError):
            plan.read_classes(["db\t\tit\n"])

    def test_weights_skip_bad_lines(self):
        self.assertEqual(plan.weights(["# x", "A\t3", "B\tx", "C\t0", "D 2.5", "E"]), {"A": 3.0, "D": 2.5})

    def test_deal_is_longest_first_and_balanced(self):
        w = {"a": 5, "b": 4, "c": 3, "d": 2, "e": 1}
        self.assertEqual(plan.deal(list(w), 2, w.get), [["a", "d", "e"], ["b", "c"]])
        self.assertEqual(plan.deal(["x"], 3, lambda _: 1), [["x"]])
        self.assertEqual(plan.deal([], 3, lambda _: 1), [])

    def test_every_suite_in_exactly_one_shard_of_its_class(self):
        all_rows = (rows("server", "it", *[f"I{i}" for i in range(9)]) + rows("server", "unit", "U1", "U2", "U3")
                    + rows("db", "it", "D1") + rows("db", "unit", "D2") + rows("core", "unit", "C1")
                    + rows("api", "unit", "A1", "A2") + rows("apiJS", "unit", "A1"))
        shards = plan.plan(all_rows, 2, 3, {"I0": 50})
        flat = [r for v in shards.values() for r in v]
        self.assertEqual(sorted(flat), sorted((p, s) for p, s, _ in all_rows))
        self.assertEqual(sorted(shards), ["it-db", "it-server-1", "it-server-2", "it-server-3", "unit-db",
                                          "unit-modules-1", "unit-modules-2", "unit-server-1", "unit-server-2"])
        self.assertEqual(shards["it-server-1"], [("server", "com.x.I0")])  # the heavy one alone
        # the other projects whole, the biggest first: api (2 suites) alone, apiJS and core together
        self.assertEqual(shards["unit-modules-1"], [("api", "com.x.A1"), ("api", "com.x.A2")])
        self.assertEqual(shards["unit-modules-2"], [("apiJS", "com.x.A1"), ("core", "com.x.C1")])
        cls = {(p, s): c for p, s, c in all_rows}
        for name, suites in shards.items():
            self.assertTrue(all(cls[s] == name.split("-")[0] for s in suites), name)

    def test_refusals(self):
        with self.assertRaises(ValueError):
            plan.plan(rows("db", "it", "D1", "D1"), 2, 2, {})
        # a suite of no class is dealt nowhere (the shards check fails on it)
        self.assertEqual(plan.plan(rows("db", "unclassified", "D1") + rows("db", "it", "D2"), 2, 2, {}),
                         {"it-db": [("db", "com.x.D2")]})
        with self.assertRaises(ValueError):  # only db and server can reach the database
            plan.plan(rows("cli", "it", "C1"), 2, 2, {})

    def test_empty_shards_are_left_out_and_only_it_shards_start_a_database(self):
        shards = plan.plan(rows("server", "it", "S1") + rows("values", "unit", "V1"), 2, 3, {})
        self.assertEqual(sorted(shards), ["it-server-1", "unit-modules-1"])
        self.assertEqual(sorted(plan.matrix(shards), key=lambda m: m["shard"]),
                         [{"shard": "it-server-1", "db": True}, {"shard": "unit-modules-1", "db": False}])
        json.dumps(plan.matrix(shards))

    def test_main_writes_one_file_per_shard_and_fails_without_suites(self):
        with tempfile.TemporaryDirectory() as d, contextlib.redirect_stdout(io.StringIO()), \
                contextlib.redirect_stderr(io.StringIO()):
            classes, out = os.path.join(d, "classes.tsv"), os.path.join(d, "shards")
            with open(classes, "w") as f:
                f.write("db\tcom.x.D1\tit\ndb\tcom.x.D2\tunit\nvalues\tcom.x.V\tunit\n")
            self.assertEqual(plan.main([classes, out, "--weights", os.path.join(d, "none.tsv")]), 0)
            self.assertEqual(sorted(os.listdir(out)), ["it-db.tsv", "unit-db.tsv", "unit-modules-1.tsv"])
            with open(os.path.join(out, "it-db.tsv")) as f:
                self.assertEqual(f.read(), "db\tcom.x.D1\n")
            with open(classes, "w") as f:
                f.write("")
            self.assertEqual(plan.main([classes, os.path.join(d, "s3")]), 1)


if __name__ == "__main__":
    unittest.main()

"""scripts/ci/plan.py: every suite in exactly one shard, the server's dealt by weight."""
import importlib.util
import json
import os
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("plan", os.path.join(os.path.dirname(__file__), "..", "plan.py"))
plan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(plan)


def rows(project, *names):
    return [(project, f"com.x.{n}") for n in names]


class PlanTest(unittest.TestCase):
    def test_rows_parse_and_refuse_malformed_lines(self):
        self.assertEqual(plan.read_rows(["# c\n", "\n", "db\tcom.x.A\n"]), [("db", "com.x.A")])
        with self.assertRaises(ValueError):
            plan.read_rows(["db com.x.A\n"])
        with self.assertRaises(ValueError):
            plan.read_rows(["db\t\n"])

    def test_weights_skip_bad_lines(self):
        self.assertEqual(plan.weights(["# x", "A\t3", "B\tx", "C\t0", "D 2.5", "E"]), {"A": 3.0, "D": 2.5})

    def test_deal_is_longest_first_and_balanced(self):
        w = {"a": 5, "b": 4, "c": 3, "d": 2, "e": 1}
        got = plan.deal(list(w), 2, w.get)
        self.assertEqual(got, [["a", "d", "e"], ["b", "c"]])
        self.assertEqual(plan.deal(["x"], 3, lambda _: 1), [["x"]])
        self.assertEqual(plan.deal([], 3, lambda _: 1), [])

    def test_every_suite_in_exactly_one_shard(self):
        all_rows = (rows("server", *[f"S{i}" for i in range(10)]) + rows("db", "D1", "D2") + rows("core", "C1")
                    + rows("api", "A1") + rows("apiJS", "A1") + rows("cli", "Cl1"))
        shards = plan.plan(all_rows, 3, {"S0": 50})
        flat = [r for v in shards.values() for r in v]
        self.assertEqual(sorted(flat), sorted(all_rows))
        self.assertEqual(sorted(shards), ["core", "db", "pure", "server-1", "server-2", "server-3"])
        self.assertEqual(shards["server-1"], [("server", "com.x.S0")])  # the heavy one alone
        self.assertEqual(shards["pure"], sorted(rows("api", "A1") + rows("apiJS", "A1") + rows("cli", "Cl1")))

    def test_a_suite_listed_twice_is_refused(self):
        with self.assertRaises(ValueError):
            plan.plan(rows("db", "D1", "D1"), 2, {})

    def test_empty_shards_are_left_out_and_the_matrix_says_which_need_a_database(self):
        shards = plan.plan(rows("server", "S1") + rows("values", "V1"), 3, {})
        self.assertEqual(sorted(shards), ["pure", "server-1"])
        self.assertEqual(sorted(plan.matrix(shards), key=lambda m: m["shard"]),
                         [{"shard": "pure", "db": False}, {"shard": "server-1", "db": True}])

    def test_main_writes_one_file_per_shard(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = os.path.join(d, "suites"), os.path.join(d, "shards")
            os.makedirs(src)
            with open(os.path.join(src, "db.tsv"), "w") as f:
                f.write("db\tcom.x.D1\ndb\tcom.x.D2\n")
            with open(os.path.join(src, "values.tsv"), "w") as f:
                f.write("values\tcom.x.V\n")
            self.assertEqual(plan.main([src, out, "--weights", os.path.join(d, "none.tsv")]), 0)
            self.assertEqual(sorted(os.listdir(out)), ["db.tsv", "pure.tsv"])
            with open(os.path.join(out, "db.tsv")) as f:
                self.assertEqual(f.read(), "db\tcom.x.D1\ndb\tcom.x.D2\n")
            empty = os.path.join(d, "empty")
            os.makedirs(empty)
            self.assertEqual(plan.main([empty, os.path.join(d, "shards2")]), 1)

    def test_matrix_is_json(self):
        json.dumps(plan.matrix({"db": rows("db", "D")}))


if __name__ == "__main__":
    unittest.main()

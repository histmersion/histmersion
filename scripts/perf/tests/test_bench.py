import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import bench  # noqa: E402


class HelpersTest(unittest.TestCase):
    def test_percentile_is_nearest_rank(self):
        xs = list(range(1, 101))
        self.assertEqual(bench.percentile(xs, 50), 50)
        self.assertEqual(bench.percentile(xs, 95), 95)
        self.assertEqual(bench.percentile([7], 95), 7)
        self.assertIsNone(bench.percentile([], 50))

    def test_summarise_reports_latency_and_median_payload(self):
        s = bench.summarise({"a": [(10.0, 100), (20.0, 300), (30.0, 200)]})
        self.assertEqual(s["a"], {"n": 3, "p50": 20.0, "p95": 30.0, "max": 30.0, "bytes": 200})

    def test_typing_sends_each_prefix(self):
        self.assertEqual(bench.prefixes("Troy"), ["T", "Tr", "Tro", "Troy"])
        self.assertEqual(bench.prefixes("Babylon", 3), ["B", "Ba", "Bab"])
        self.assertEqual(bench.prefixes("  "), [])

    def test_query_drops_none_and_repeats_lists(self):
        self.assertEqual(bench.query([("a", 1), ("b", None), ("place", ["x", "y"])]), "a=1&place=x&place=y")

    def test_bucket_size_matches_the_timeline(self):
        self.assertEqual(bench.bucket_size(3000, 1200), 3)
        self.assertEqual(bench.bucket_size(14026, 100, 1500), 141)
        self.assertEqual(bench.bucket_size(5), 1)

    def test_over_budget_with_slack(self):
        s = {"hover.place": {"p95": 80.0}, "map.year": {"p95": 100.0}}
        self.assertEqual(bench.over_budget(s, {"hover.place": 50, "map.year": 150}), [("hover.place", 80.0, 50)])
        self.assertEqual(bench.over_budget(s, {"hover.place": 50}, slack=2), [])

    def test_compare_table_has_the_ratio(self):
        t = bench.compare({"x": {"p50": 100.0, "p95": 1000.0}}, {"x": {"p50": 2.0, "p95": 10.0}}, "k")
        self.assertIn("| `x` | 100 | 1000 | 2.0 | 10 | 100 |", t)


if __name__ == "__main__":
    unittest.main()

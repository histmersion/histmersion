import importlib.util
import os
import unittest

_p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test-weights.py")
_s = importlib.util.spec_from_file_location("test_weights", _p)
tw = importlib.util.module_from_spec(_s)
_s.loader.exec_module(tw)


class WeightsTest(unittest.TestCase):
    def test_db_and_server_suites_at_two_seconds_or_more(self):
        log = [
            "  12.0 | Test run com.histmersion.server.HardeningHttpSuite finished: 0 failed, 0 ignored, 10 total 43.8s",
            "Test run \x1b[36mcom.histmersion.db.BundleSuite\x1b[0m finished: 0 failed, 0 ignored, 4 total 34.2s",
            "Test run com.histmersion.util.basemap.BasemapHttpSuite finished: 0 failed, 0 ignored, 3 total 13.2s",
            "Test run com.histmersion.server.CssTokensSuite finished: 0 failed, 0 ignored, 3 total 0.2s",
            "Test run com.histmersion.config.ConfigSuite finished: 0 failed, 0 ignored, 3 total 9.0s",
        ]
        self.assertEqual(tw.weights(log), {"HardeningHttpSuite": 44, "BundleSuite": 34, "BasemapHttpSuite": 13})

    def test_render_sorts_heaviest_first(self):
        self.assertEqual(tw.render({"A": 3, "B": 9}).splitlines()[1:], ["B\t9", "A\t3"])


if __name__ == "__main__":
    unittest.main()

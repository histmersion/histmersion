import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "watched.sh"


def run(command, quiet, dumps):
    env = dict(os.environ)
    env.update(
        HISTMERSION_WATCHDOG_QUIET_SECONDS=str(quiet),
        HISTMERSION_WATCHDOG_POLL_SECONDS="1",
        HISTMERSION_THREAD_DUMPS=str(dumps),
    )
    return subprocess.run(["bash", str(SCRIPT), "sh", "-c", command], env=env, capture_output=True, text=True, timeout=60)


class WatchedTest(unittest.TestCase):
    def test_passes_output_and_exit_status_through(self):
        with tempfile.TemporaryDirectory() as tmp:
            dumps = Path(tmp) / "dumps"
            r = run("echo one; echo two >&2; exit 7", 60, dumps)
            self.assertEqual(r.returncode, 7)
            self.assertIn("one", r.stdout)
            self.assertIn("two", r.stdout)
            self.assertFalse(dumps.exists())

    def test_a_quiet_command_gets_its_processes_dumped(self):
        with tempfile.TemporaryDirectory() as tmp:
            dumps = Path(tmp) / "dumps"
            r = run("echo start; sleep 4; echo end", 2, dumps)
            self.assertEqual(r.returncode, 0)
            self.assertIn("watchdog: no output for 2s", r.stdout)
            self.assertIn("end", r.stdout)
            self.assertTrue(any(p.name.endswith("-ps.txt") for p in dumps.iterdir()))

    def test_a_chatty_command_is_left_alone(self):
        with tempfile.TemporaryDirectory() as tmp:
            dumps = Path(tmp) / "dumps"
            r = run("for i in 1 2 3 4 5; do echo $i; sleep 1; done", 3, dumps)
            self.assertEqual(r.returncode, 0)
            self.assertNotIn("watchdog", r.stdout)
            self.assertFalse(dumps.exists())


if __name__ == "__main__":
    unittest.main()

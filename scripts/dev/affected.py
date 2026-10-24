#!/usr/bin/env python3
"""Which sbt modules a change touches, and which of them to test — `just test-quick` without arguments.

    python3 scripts/dev/affected.py [--base master] [paths…]

Without paths it asks git for every file changed since the merge base with `--base` (committed on this
branch, staged, unstaged and untracked). It prints one line: the sbt project ids to test, in build
order, or nothing when no Scala module is affected; `--explain` prints the reasoning to stderr.

A module is affected when one of its files changed, when a module it depends on changed (the
dependents are tested too: a change in core can break db, server and cli), or when a file it reads
from the repository changed (the skills and agents, the reference pages, the seed data). A change to
the build itself (build.sbt, project/) affects everything. `just test-it` stays the full gate.
"""
import argparse
import subprocess
import sys

# the sbt project ids and what each depends on (compile or test) — build.sbt's dependsOn, plus the
# TEST resources (the hello / hello-ai plugin jars, the hello-deps fixture repository). web → server (the bundle) is a BUILD
# edge only: no server suite tests the Scala.js code, so a web change does not re-run the server.
DEPS = {
    "coreJVM": [],
    "coreJS": [],
    "pluginApi": [],
    "pluginVaults": ["pluginApi"],
    "pluginHello": ["pluginApi"],
    "pluginHelloAi": ["pluginApi"],
    "pluginHelloLib": [],
    "pluginHelloDeps": ["pluginApi", "pluginHelloLib"],
    "pluginResolver": ["pluginHelloDeps"],
    "chimneyIron": [],
    "chimneyIronTests": ["chimneyIron"],
    "db": ["coreJVM"],
    "cli": ["coreJVM"],
    "web": ["coreJS"],
    "server": ["coreJVM", "db", "pluginApi", "pluginVaults", "pluginHello", "pluginHelloAi", "pluginHelloDeps"],
}
ORDER = ["coreJVM", "coreJS", "pluginApi", "pluginVaults", "pluginHello", "pluginHelloAi", "pluginHelloLib",
         "pluginHelloDeps", "pluginResolver", "chimneyIron", "chimneyIronTests", "db", "cli", "web", "server"]
# modules with no test sources of their own
NO_TESTS = {"pluginHello", "pluginHelloAi", "pluginHelloLib", "pluginHelloDeps", "chimneyIron"}
# modules whose suites need the throwaway Postgres (HISTMERSION_TEST_DB_URL)
NEEDS_DB = {"db", "server"}
ALL = "*"

# path prefix → modules (first match wins; longest prefixes first)
RULES = [
    ("build.sbt", [ALL]),
    ("project/", [ALL]),
    (".sdkmanrc", [ALL]),
    (".jvmopts", [ALL]),
    ("modules/core/shared/", ["coreJVM", "coreJS"]),
    ("modules/core/jvm/", ["coreJVM"]),
    ("modules/core/js/", ["coreJS"]),
    ("modules/db/", ["db"]),
    ("modules/server/", ["server"]),
    ("modules/cli/", ["cli"]),
    # the basemap fixture is read by the server's basemap suites; the rest of test/ is the
    # Playwright browser suite (npm), not sbt
    ("modules/web/test/fixtures/", ["server"]),
    ("modules/web/test/", []),
    ("modules/web/", ["web"]),
    ("modules/plugin-api/", ["pluginApi"]),
    ("modules/plugin-vaults/", ["pluginVaults"]),
    ("modules/plugin-fixtures/hello-ai/", ["pluginHelloAi"]),
    ("modules/plugin-fixtures/hello-deps/", ["pluginHelloDeps"]),
    ("modules/plugin-fixtures/hello-lib/", ["pluginHelloLib"]),
    ("modules/plugin-resolver/", ["pluginResolver"]),
    ("modules/plugin-fixtures/hello/", ["pluginHello"]),
    ("modules/chimney-iron-tests/", ["chimneyIronTests"]),
    ("modules/chimney-iron/", ["chimneyIron"]),
    # repository files the suites read: agents and skills (server resources, AgentsSuite), the
    # reference pages (ApiSurfaceSuite, OpsHttpSuite), the seed data (PeriodsSuite, place seeds)
    ("agents/", ["coreJVM", "server"]),
    ("skills/", ["coreJVM", "server"]),
    ("plugin/", ["server"]),
    (".claude-plugin/", ["server"]),
    ("docs/writing-guide.md", ["server"]),
    ("docs/user-guide/reference/", ["coreJVM", "server"]),
    ("docs/", []),
    ("db/", ["coreJVM", "db", "server"]),
    ("runtime/", ["server"]),
    ("scripts/", []),
    ("converter/", []),
    ("tools/", []),
    ("spike/", []),
    (".github/", []),
    (".claude/", []),
    (".opencode/", []),
]
# top-level files that no suite reads
INERT_TOP = {"README.md", "AGENTS.md", "CLAUDE.md", ".gitignore", ".env.example", ".mcp.json.example",
             "justfile"}


# core's lint suites read the other modules' main sources: StyleLintSuite and MapEngineBoundarySuite the
# web page's, PluralSuite every module's Scala (#3765: a web change passed test-quick and failed test-it)
def read_by_core(path):
    """Is `path` a file core's lint suites read (the web page's sources, any module's main Scala)?"""
    p = path.removeprefix("./")
    return p.startswith("modules/web/src/main/") or (
        p.startswith("modules/") and not p.startswith("modules/core/") and "/src/main/" in p and p.endswith(".scala"))


def modules_for(path):
    """The modules a changed path touches directly; [ALL] for anything unknown (be safe)."""
    p = path.removeprefix("./")
    if "/" not in p and p in INERT_TOP:
        return []
    for prefix, mods in RULES:
        if p == prefix or p.startswith(prefix):
            return list(mods)
    return [ALL]


def dependents(mods):
    """`mods` plus every module that depends on one of them, transitively."""
    out = set(mods)
    changed = True
    while changed:
        changed = False
        for m, ds in DEPS.items():
            if m not in out and any(d in out for d in ds):
                out.add(m)
                changed = True
    return out


def affected(paths):
    """(the modules to test in build order, whether any needs the test database, the reasons)."""
    direct, why = set(), []
    for p in paths:
        ms = modules_for(p)
        if ALL in ms:
            direct |= set(ORDER)
            why.append(f"{p} → everything")
        elif ms:
            direct |= set(ms)
            why.append(f"{p} → {', '.join(ms)}")
    closure = dependents(direct)
    for m in sorted(closure - direct, key=ORDER.index):
        why.append(f"{m}: depends on a changed module")
    # a suite that only READS a changed file: its module is tested, its dependents are not
    readers = [p for p in paths if read_by_core(p)]
    if readers and "coreJVM" not in closure:
        closure.add("coreJVM")
        why.append(f"coreJVM: its lint suites read {readers[0]}")
    tests = [m for m in ORDER if m in closure and m not in NO_TESTS]
    return tests, any(m in NEEDS_DB for m in tests), why


def scripts_touched(paths):
    return any(p.startswith(("scripts/", "db/gazetteer/", "db/places/")) for p in paths)


def git_changed(base):
    def git(*a):
        return subprocess.run(["git", *a], capture_output=True, text=True, check=True).stdout
    mb = git("merge-base", base, "HEAD").strip()
    files = set(git("diff", "--name-only", mb).split())  # committed since the base + unstaged + staged
    files |= set(git("ls-files", "--others", "--exclude-standard").split())  # untracked
    return sorted(files)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--base", default="master")
    ap.add_argument("--explain", action="store_true")
    ap.add_argument("--needs-db", action="store_true", help="print yes/no: does any affected module need the test database")
    ap.add_argument("paths", nargs="*")
    a = ap.parse_args(argv)
    paths = a.paths or git_changed(a.base)
    tests, db, why = affected(paths)
    if a.explain:
        for w in why:
            print(f"  {w}", file=sys.stderr)
        if scripts_touched(paths):
            print("  scripts or seed builders changed: run `just test-scripts` too", file=sys.stderr)
    print(("yes" if db else "no") if a.needs_db else " ".join(tests))


if __name__ == "__main__":
    main()

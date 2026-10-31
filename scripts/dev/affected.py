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
    "values": [],
    "valuesJS": [],
    "api": ["values"],
    "apiJS": ["valuesJS"],
    "config": ["values", "api"],
    "files": ["api"],
    "core": ["values", "api", "config", "files"],
    "pluginApi": [],
    "pluginVaults": ["pluginApi"],
    "upstreams": ["pluginApi"],
    "pluginHost": ["core", "config", "pluginApi", "upstreams"],
    "pluginTestkit": ["pluginHost"],
    "pluginsFetchers": ["upstreams", "pluginTestkit"],
    "pluginsEvents": ["upstreams", "pluginTestkit"],
    "pluginsMedia": ["upstreams", "pluginTestkit"],
    "pluginsGazetteers": ["upstreams", "pluginTestkit"],
    "pluginsAiHttp": ["pluginApi", "pluginTestkit"],
    "pluginHello": ["pluginApi"],
    "pluginHelloAi": ["pluginApi"],
    "pluginHelloLib": [],
    "pluginHelloDeps": ["pluginApi", "pluginHelloLib"],
    "pluginResolver": ["pluginHelloDeps"],
    "chimneyIron": [],
    "chimneyIronTests": ["chimneyIron"],
    "db": ["config", "core"],
    "cli": ["api", "config", "files"],
    "web": ["apiJS"],
    "server": ["core", "db", "pluginApi", "pluginHost", "pluginVaults", "pluginsFetchers", "pluginsEvents", "pluginsMedia", "pluginsAiHttp", "pluginHello",
               "pluginHelloAi", "pluginHelloDeps", "pluginsGazetteers"],
}
ORDER = ["values", "valuesJS", "api", "apiJS", "config", "files", "core", "pluginApi", "upstreams", "pluginHost", "pluginTestkit",
         "pluginsFetchers", "pluginsEvents", "pluginsMedia", "pluginsGazetteers", "pluginsAiHttp", "pluginVaults", "pluginHello", "pluginHelloAi", "pluginHelloLib",
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
    ("modules/values/", ["values", "valuesJS"]),
    ("modules/api/src/main/scalajvm/", ["api"]),
    ("modules/api/src/test/scalajvm/", ["api"]),
    ("modules/api/src/main/scalajs/", ["apiJS"]),
    ("modules/api/src/test/scalajs/", ["apiJS"]),
    ("modules/api/", ["api", "apiJS"]),
    ("modules/config/", ["config"]),
    ("modules/files/", ["files"]),
    ("modules/core/", ["core"]),
    ("modules/db/", ["db"]),
    ("modules/server/", ["server"]),
    ("modules/cli/", ["cli"]),
    # the basemap fixture is read by the server's basemap suites; the rest of test/ is the
    # Playwright browser suite (npm), not sbt
    ("modules/web/test/fixtures/", ["server"]),
    ("modules/web/test/", []),
    ("modules/web/", ["web"]),
    ("modules/plugin-api/", ["pluginApi"]),
    ("modules/plugin-host/", ["pluginHost"]),
    ("modules/upstreams/", ["upstreams"]),
    ("modules/plugins/fetchers/", ["pluginsFetchers"]),
    ("modules/plugins/events/", ["pluginsEvents"]),
    ("modules/plugins/media/", ["pluginsMedia"]),
    ("modules/plugins/gazetteers/", ["pluginsGazetteers"]),
    ("modules/plugins/ai-http/", ["pluginsAiHttp"]),
    ("modules/plugin-testkit/", ["pluginTestkit"]),
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
    ("agents/", ["core", "server"]),
    ("skills/", ["core", "server"]),
    ("plugin/", ["server"]),
    (".claude-plugin/", ["server"]),
    ("docs/writing-guide.md", ["server"]),
    ("docs/user-guide/reference/", ["core", "server"]),
    ("docs/", []),
    ("db/", ["api", "db", "server"]),
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


# the lint suites read the other modules' main sources: api's StyleLintSuite the web page's and api's own,
# PluralSuite every module's Scala; config's ConfigSuite every module's Scala; core's MapEngineBoundarySuite
# the web page's (#3765: a web change passed test-quick and failed test-it)
LINT_READERS = ["api", "config", "core"]


def read_by_lints(path):
    """Is `path` a file the lint suites read (the web page's sources, any module's main Scala)?"""
    p = path.removeprefix("./")
    return p.startswith("modules/web/src/main/") or (p.startswith("modules/") and "/src/main/" in p and p.endswith(".scala"))


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
    readers = [p for p in paths if read_by_lints(p)]
    for m in LINT_READERS:
        if readers and m not in closure:
            closure.add(m)
            why.append(f"{m}: its lint suites read {readers[0]}")
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

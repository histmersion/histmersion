"""Macros for the Histmersion documentation (read by Zensical's built-in macros support, and by
mkdocs-macros-plugin when the site is built with MkDocs instead).

  {{ histmersion_version() }}           the version the build declares (build.sbt), else `git describe`
  {{ repo_file("docs/plan.md") }}       a link to a file in the repository (outside the docs site)
  {{ repo_file("docs/plan.md", "the plan") }}   …with its own link text
"""

import os
import re
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)


def _version() -> str:
    try:
        with open(os.path.join(ROOT, "build.sbt"), encoding="utf-8") as f:
            m = re.search(r'ThisBuild\s*/\s*version\s*:=\s*"([^"]+)"', f.read())
            if m:
                return m.group(1)
    except OSError:
        pass
    try:
        out = subprocess.run(["git", "describe", "--tags"], cwd=ROOT, capture_output=True, text=True, timeout=5)
        if out.returncode == 0 and out.stdout.strip():
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return "unknown"


def define_env(env):
    version = _version()
    extra = env.conf.get("extra", {}) if hasattr(env, "conf") else {}
    blob = extra.get("repo_blob", "https://github.com/histmersion/histmersion/blob/master/")

    @env.macro
    def histmersion_version() -> str:
        return version

    @env.macro
    def repo_file(path: str, text: str = "") -> str:
        """A Markdown link to a repository file that is not part of the site (design notes, scripts)."""
        label = text or f"`{path}`"
        return f"[{label}]({blob}{path.lstrip('/')})"

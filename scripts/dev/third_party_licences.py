#!/usr/bin/env python3
"""The licences of the third-party libraries Histmersion is built from and ships — `just licences`.

    python3 scripts/dev/third_party_licences.py --classpaths <sbt show output> [--out THIRD-PARTY.md]

It reads
  - the JVM and Scala.js class paths, as `sbt "show <module>/Runtime/fullClasspath" …` prints them
    (one `[info] * Attributed(…)` line per entry; `export`'s `List(…)` lines are read too), and each jar's POM in the Coursier cache — following the parent
    POMs, fetched from Maven Central when the cache lacks one;
  - the converter's uv.lock: the packages the bundle installs (the main and `ocr` dependencies, not the
    `dev` group), each package's licence from PyPI;
  - the bundled PostgreSQL's pixi.lock (conda-forge records the licence of every package);
  - the web module's package-lock.json (build and test tools only: nothing of it ships).

Each licence is classed against the AGPL-3.0: `compatible`, `INCOMPATIBLE` (a licence that cannot be
combined with it into one program), or `check` (unknown or unrecognised wording: read it). The
bundled PostgreSQL, PostGIS and converter run as separate programs beside the server, so the AGPL
does not require their licences to be compatible; the table still flags them, for the record.
"""
import argparse
import json
import os
import re
import sys
import tomllib
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MAVEN_CENTRAL = "https://repo1.maven.org/maven2"

SPDX_PATTERNS = [
    (r"affero|agpl", "AGPL-3.0"),
    (r"lesser|lgpl|library general", "LGPL"),
    (r"gpl.*2.*classpath|classpath exception|gpl2 w/ cpe|gpl-2\.0-with-classpath", "GPL-2.0-with-classpath-exception"),
    (r"gpl-?2\.0-or-later|gpl-?2\.0\+|gplv2\+|gpl-2\.0-or-later", "GPL-2.0-or-later"),
    (r"gpl-?2\.0-only|gplv2$|gpl-?2\.0$|gnu general public license,? v(ersion )?2(\.0)?$|gpl v2$", "GPL-2.0-only"),
    (r"gpl-?3|gplv3|gnu general public license,? v(ersion )?3", "GPL-3.0"),
    (r"apache", "Apache-2.0"),
    (r"\bmit\b|expat", "MIT"),
    (r"\b0bsd\b|zero-clause bsd", "0BSD"),
    (r"bsd.*3|3-clause|new bsd|revised bsd|modified bsd|\bedl\b|eclipse distribution", "BSD-3-Clause"),
    (r"bsd.*2|2-clause|simplified bsd|freebsd", "BSD-2-Clause"),
    (r"^bsd$|bsd license|\bbsd\b", "BSD"),
    (r"\bisc\b", "ISC"),
    (r"mozilla public license,? (version )?2|mpl[- ]?2", "MPL-2.0"),
    (r"mozilla public license,? (version )?1|mpl[- ]?1", "MPL-1.1"),
    (r"eclipse public license,?\s*-?\s*v(ersion)?\s*2|epl[- ]?(v)?2", "EPL-2.0"),
    (r"eclipse public license|epl[- ]?1|\bepl\b", "EPL-1.0"),
    (r"cddl|common development and distribution", "CDDL"),
    (r"postgresql", "PostgreSQL"),
    (r"zlib", "Zlib"),
    (r"bzip2", "bzip2-1.0.6"),
    (r"python software foundation|\bpsfl?\b|python-2\.0|cnri-python", "PSF-2.0"),
    (r"\bijg\b|independent jpeg", "IJG"),
    (r"oldap|openldap", "OLDAP-2.8"),
    (r"^blessing$", "blessing"),
    (r"unicode", "Unicode"),
    (r"public[- ]domain|cc0|unlicense", "Public-domain"),
    (r"creative commons.*non-?commercial|by-nc|\bnc\b", "CC-BY-NC"),
    (r"cc-by-sa|attribution-sharealike", "CC-BY-SA"),
    (r"cc-by|creative commons attribution", "CC-BY"),
    (r"ofl|open font", "OFL-1.1"),
    (r"hpnd|historical permission", "HPND"),
    (r"curl", "curl"),
    (r"openssl|ssleay", "OpenSSL"),
    (r"\bx11\b", "X11"),
    (r"boost|bsl-1", "BSL-1.0"),
    (r"proprietary|commercial|all rights reserved", "Proprietary"),
    (r"non-?commercial", "CC-BY-NC"),
]

COMPATIBLE = {
    "AGPL-3.0", "LGPL", "GPL-3.0", "GPL-2.0-or-later", "Apache-2.0", "MIT", "BSD-3-Clause", "BSD-2-Clause",
    "BSD", "ISC", "MPL-2.0", "EPL-2.0", "PostgreSQL", "Zlib", "bzip2-1.0.6", "PSF-2.0", "Unicode",
    "Public-domain", "HPND", "curl", "X11", "BSL-1.0", "OFL-1.1", "GPL-2.0-with-classpath-exception", "0BSD",
    "IJG", "OLDAP-2.8", "blessing",
}
INCOMPATIBLE = {"GPL-2.0-only", "EPL-1.0", "CDDL", "MPL-1.1", "OpenSSL", "Proprietary", "CC-BY-NC"}


def spdx(wording: str) -> str:
    w = " ".join(wording.split())
    if not w:
        return "?"
    for pattern, ident in SPDX_PATTERNS:
        if re.search(pattern, w, re.IGNORECASE):
            return ident
    return f"?: {w[:60]}"


def split_expression(expr: str) -> list:
    return [p.strip(" ()") for p in re.split(r"\s+(?:OR|AND|or|and|/)\s+|;", expr) if p.strip(" ()")]


def classify(licences: list, any_of: bool = False) -> str:
    ids = [spdx(l) for l in licences]
    if not ids:
        return "check"
    ok = [i in COMPATIBLE for i in ids]
    if (any(ok) if any_of else all(ok)):
        return "compatible"
    if any(i in INCOMPATIBLE for i in ids) and not (any_of and any(ok)):
        return "INCOMPATIBLE"
    return "check"


SBT_SHOW_ENTRY = re.compile(r"^\[info\] \* Attributed\((.+)\)$")


def parse_sbt_export(text: str) -> list:
    out = []
    for line in text.splitlines():
        line = line.strip()
        shown = SBT_SHOW_ENTRY.match(line)
        if shown:
            if shown.group(1) not in out:
                out.append(shown.group(1))
        elif line.startswith("List(") and line.endswith(")"):
            for entry in line[len("List("):-1].split(", "):
                if entry and entry not in out:
                    out.append(entry)
        elif line and not line.startswith("[") and (os.pathsep in line or line.endswith(".jar")):
            for entry in line.split(os.pathsep):
                if entry and entry not in out:
                    out.append(entry)
    return out


def maven_coords(entry: str):
    m = re.search(r"/maven2/(.+)/([^/]+)/([^/]+)/\2-\3(?:-[^/]*)?\.jar$", entry)
    if not m:
        return None
    return m.group(1).replace("/", "."), m.group(2), m.group(3)


def _strip_ns(root):
    for el in root.iter():
        if isinstance(el.tag, str) and "}" in el.tag:
            el.tag = el.tag.split("}", 1)[1]
    return root


def pom_licences(pom_xml: str) -> list:
    root = _strip_ns(ET.fromstring(pom_xml))
    out = []
    for lic in root.findall("./licenses/license"):
        name = (lic.findtext("name") or "").strip() or (lic.findtext("url") or "").strip()
        if name:
            out.append(name)
    return out


def pom_parent(pom_xml: str):
    root = _strip_ns(ET.fromstring(pom_xml))
    p = root.find("./parent")
    if p is None:
        return None
    return (p.findtext("groupId") or "").strip(), (p.findtext("artifactId") or "").strip(), (p.findtext("version") or "").strip()


def pom_path(group: str, artifact: str, version: str) -> str:
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"


def uv_shipped(lock_text: str, root_name: str, extras=("ocr",)) -> list:
    lock = tomllib.loads(lock_text)
    pkgs = {p["name"]: p for p in lock.get("package", [])}
    seen, todo = set(), []

    def want(dep):
        todo.append((dep["name"], tuple(dep.get("extra", []))))

    root = pkgs[root_name]
    for d in root.get("dependencies", []):
        want(d)
    for e in extras:
        for d in root.get("optional-dependencies", {}).get(e, []):
            want(d)
    while todo:
        name, wanted_extras = todo.pop()
        key = (name, wanted_extras)
        if key in seen or name not in pkgs:
            continue
        seen.add(key)
        p = pkgs[name]
        for d in p.get("dependencies", []):
            want(d)
        for e in wanted_extras:
            for d in p.get("optional-dependencies", {}).get(e, []):
                want(d)
    names = sorted({n for n, _ in seen})
    return [(n, pkgs[n].get("version", "")) for n in names]


def pixi_packages(lock_text: str, platform: str) -> list:
    out, current = [], None
    for line in lock_text.splitlines():
        m = re.match(r"^- conda: (\S+)$", line)
        if m:
            current = m.group(1)
            continue
        m = re.match(r"^  license: (.+)$", line)
        if m and current and (f"/{platform}/" in current or "/noarch/" in current):
            fname = current.rsplit("/", 1)[1]
            fname = re.sub(r"\.(conda|tar\.bz2)$", "", fname)
            name, version, _build = fname.rsplit("-", 2)
            out.append((name, version, m.group(1).strip().strip("'\"")))
            current = None
    return sorted(set(out))


def npm_packages(lock_json: str) -> list:
    lock = json.loads(lock_json)
    out = []
    for path, p in lock.get("packages", {}).items():
        if not path:
            continue
        name = p.get("name") or path.rsplit("node_modules/", 1)[-1]
        lic = p.get("license", "")
        if isinstance(lic, dict):
            lic = lic.get("type", "")
        out.append((name, p.get("version", ""), lic or "", bool(p.get("dev") or p.get("devOptional"))))
    return sorted(set(out))


def pypi_licence(info: dict) -> str:
    expr = (info.get("license_expression") or "").strip()
    if expr:
        return expr
    text = (info.get("license") or "").strip()
    if text and len(text) <= 80 and "\n" not in text:
        return text
    classifiers = [c.split("::")[-1].strip() for c in info.get("classifiers", []) if c.startswith("License ::")]
    classifiers = [c for c in classifiers if c != "OSI Approved"]
    if classifiers:
        return " OR ".join(classifiers)
    return text.splitlines()[0][:80] if text else ""


def markdown(rows_by_section: list) -> str:
    lines = [
        "# Third-party licences",
        "",
        "Generated by `just licences` (scripts/dev/third_party_licences.py) — do not edit by hand.",
        "",
        "The libraries Histmersion is built from or ships, each with the licence its own metadata states",
        "(the POM, PyPI, conda-forge, npm). **compat** is how that licence relates to Histmersion's",
        "AGPL-3.0-or-later: `compatible`; `INCOMPATIBLE` (cannot be combined with it into one program);",
        "`check` (nothing stated, or wording this script does not recognise: read the licence). The",
        "bundled PostgreSQL, PostGIS and converter are separate programs the server talks to, not parts of",
        "it. Material other than libraries (fonts, icons, data) is listed in NOTICE.",
        "",
    ]
    flagged = []
    for title, note, rows in rows_by_section:
        lines += [f"## {title}", ""]
        if note:
            lines += [note, ""]
        lines += ["| package | version | licence | compat |", "|---|---|---|---|"]
        for name, version, licence, compat in rows:
            lines.append(f"| {name} | {version} | {licence or '(none stated)'} | {compat} |")
            if compat != "compatible":
                flagged.append((title, name, version, licence, compat))
        lines.append("")
    lines += ["## Not `compatible`", ""]
    if flagged:
        lines += ["| part | package | version | licence | compat |", "|---|---|---|---|---|"]
        lines += [f"| {t} | {n} | {v} | {l or '(none stated)'} | {c} |" for t, n, v, l, c in flagged]
    else:
        lines.append("None.")
    lines.append("")
    return "\n".join(lines)


def coursier_cache() -> str:
    for c in (os.environ.get("COURSIER_CACHE"), os.path.expanduser("~/Library/Caches/Coursier/v1"),
              os.path.expanduser("~/.cache/coursier/v1")):
        if c and os.path.isdir(c):
            return c
    sys.exit("no Coursier cache found (set COURSIER_CACHE)")


def read_pom(cache: str, coords) -> str:
    local = os.path.join(cache, "https", "repo1.maven.org", "maven2", pom_path(*coords))
    if os.path.isfile(local):
        with open(local, encoding="utf-8", errors="replace") as f:
            return f.read()
    with urllib.request.urlopen(f"{MAVEN_CENTRAL}/{pom_path(*coords)}", timeout=30) as r:
        return r.read().decode("utf-8", errors="replace")


def jvm_rows(entries: list) -> list:
    cache = coursier_cache()
    rows = []
    for entry in entries:
        coords = maven_coords(entry)
        if coords is None:
            continue
        licences, current, depth = [], coords, 0
        while current and not licences and depth < 6:
            try:
                pom = read_pom(cache, current)
            except Exception as e:  # noqa: BLE001 — a missing POM is reported, not fatal
                licences = [f"(POM unreadable: {type(e).__name__})"]
                break
            licences = pom_licences(pom)
            current = pom_parent(pom)
            depth += 1
        stated = [l for l in licences if not l.startswith("(POM")]
        rows.append((f"{coords[0]}:{coords[1]}", coords[2], " / ".join(licences),
                     classify(stated, any_of=True)))
    return sorted(set(rows))


def python_rows(lock_path: str) -> list:
    with open(lock_path, encoding="utf-8") as f:
        shipped = uv_shipped(f.read(), "histmersion-converter")
    rows = []
    for name, version in shipped:
        try:
            with urllib.request.urlopen(f"https://pypi.org/pypi/{name}/{version.split('+')[0]}/json", timeout=30) as r:
                lic = pypi_licence(json.load(r).get("info", {}))
        except Exception as e:  # noqa: BLE001
            lic = f"(PyPI unreadable: {type(e).__name__})"
        parts = split_expression(lic) if not lic.startswith("(") else []
        rows.append((name, version, lic, classify(parts, any_of=" OR " in lic or " or " in lic)))
    return rows


def conda_rows(lock_path: str, platform: str) -> list:
    with open(lock_path, encoding="utf-8") as f:
        pkgs = pixi_packages(f.read(), platform)
    return [(n, v, l, classify(split_expression(l), any_of=" OR " in l or " or " in l)) for n, v, l in pkgs]


def npm_rows(lock_path: str) -> list:
    with open(lock_path, encoding="utf-8") as f:
        pkgs = npm_packages(f.read())
    return [(n, v, l + (" (dev)" if dev else ""), classify(split_expression(l), any_of=" OR " in l))
            for n, v, l, dev in pkgs]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--classpaths", required=True, help="the output of sbt's `export …/fullClasspath`")
    ap.add_argument("--out", default=os.path.join(ROOT, "THIRD-PARTY.md"))
    ap.add_argument("--platform", default="osx-arm64", help="the pixi.lock platform to list")
    a = ap.parse_args()
    with open(a.classpaths, encoding="utf-8") as f:
        entries = parse_sbt_export(f.read())
    sections = [
        ("Scala and Java libraries (server, CLI, web UI, plugin-api, plugins)",
         "Every jar on the class path of the server, the CLI, plugin-api and the first-party plugins, and "
         "the Scala.js libraries compiled into the web UI.", jvm_rows(entries)),
        ("Converter (Python, bundled)",
         "What the bundle installs from converter/uv.lock: the main and `ocr` dependencies.",
         python_rows(os.path.join(ROOT, "converter", "uv.lock"))),
        (f"PostgreSQL runtime (conda-forge, bundled; {a.platform})",
         "What runtime/postgres/pixi.lock installs: PostgreSQL, PostGIS and their libraries.",
         conda_rows(os.path.join(ROOT, "runtime", "postgres", "pixi.lock"), a.platform)),
        ("Web build and test tools (npm, not shipped)",
         "modules/web/package-lock.json: Vite and Playwright for development and browser tests. None of "
         "it is in the server or the bundle.",
         npm_rows(os.path.join(ROOT, "modules", "web", "package-lock.json"))),
    ]
    with open(a.out, "w", encoding="utf-8") as f:
        f.write(markdown(sections))
    flagged = sum(1 for _, _, rows in sections for r in rows if r[3] != "compatible")
    print(f"{a.out}: {sum(len(r) for _, _, r in sections)} packages, {flagged} not marked compatible")


if __name__ == "__main__":
    main()

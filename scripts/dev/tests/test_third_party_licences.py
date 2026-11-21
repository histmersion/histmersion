import json
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import third_party_licences as T  # noqa: E402


class SpdxTest(unittest.TestCase):
    def test_common_wordings(self):
        self.assertEqual(T.spdx("The Apache Software License, Version 2.0"), "Apache-2.0")
        self.assertEqual(T.spdx("MIT License"), "MIT")
        self.assertEqual(T.spdx("BSD 3-Clause"), "BSD-3-Clause")
        self.assertEqual(T.spdx("GNU Lesser General Public License v2.1"), "LGPL")
        self.assertEqual(T.spdx("GNU Affero General Public License v3"), "AGPL-3.0")
        self.assertEqual(T.spdx("GPL2 w/ CPE"), "GPL-2.0-with-classpath-exception")
        self.assertEqual(T.spdx("GPL-2.0-only"), "GPL-2.0-only")
        self.assertEqual(T.spdx("GPL-2.0-or-later"), "GPL-2.0-or-later")
        self.assertEqual(T.spdx("Eclipse Public License - v 2.0"), "EPL-2.0")
        self.assertEqual(T.spdx("Eclipse Public License 1.0"), "EPL-1.0")
        self.assertEqual(T.spdx("CC BY-NC 4.0"), "CC-BY-NC")
        self.assertEqual(T.spdx("PostgreSQL"), "PostgreSQL")
        self.assertEqual(T.spdx("0BSD"), "0BSD")
        self.assertEqual(T.spdx("PSFL"), "PSF-2.0")
        self.assertEqual(T.spdx("LicenseRef-Public-Domain"), "Public-domain")
        self.assertEqual(T.spdx("MPL-1.1"), "MPL-1.1")

    def test_unrecognised_wording_is_kept_for_a_reader(self):
        self.assertEqual(T.spdx("Frobnitz Licence"), "?: Frobnitz Licence")
        self.assertEqual(T.spdx("  "), "?")


class ClassifyTest(unittest.TestCase):
    def test_compatible_incompatible_and_check(self):
        self.assertEqual(T.classify(["Apache-2.0"]), "compatible")
        self.assertEqual(T.classify(["GPL-2.0-only"]), "INCOMPATIBLE")
        self.assertEqual(T.classify(["CDDL 1.1"]), "INCOMPATIBLE")
        self.assertEqual(T.classify([]), "check")
        self.assertEqual(T.classify(["Frobnitz"]), "check")

    def test_a_choice_needs_one_compatible_licence_and_a_conjunction_needs_all(self):
        self.assertEqual(T.classify(["CDDL", "GPL2 w/ CPE"], any_of=True), "compatible")
        self.assertEqual(T.classify(["CDDL", "GPL2 w/ CPE"]), "INCOMPATIBLE")

    def test_expressions_split_into_their_parts(self):
        self.assertEqual(T.split_expression("MIT OR Apache-2.0"), ["MIT", "Apache-2.0"])
        self.assertEqual(T.split_expression("(BSD-3-Clause AND LGPL-2.1)"), ["BSD-3-Clause", "LGPL-2.1"])


class SbtTest(unittest.TestCase):
    def test_export_lines_become_entries_once(self):
        text = "[info] welcome\nList(${OUT}/a.jar, ${CSR_CACHE}/https/repo1.maven.org/maven2/x/y/1/y-1.jar)\n" \
               "List(${CSR_CACHE}/https/repo1.maven.org/maven2/x/y/1/y-1.jar)\n[success] done\n"
        self.assertEqual(T.parse_sbt_export(text),
                         ["${OUT}/a.jar", "${CSR_CACHE}/https/repo1.maven.org/maven2/x/y/1/y-1.jar"])

    def test_show_lines_become_entries_once(self):
        text = "[info] welcome\n[info] * Attributed(${OUT}/a.jar>sha256-00ff/12)\n" \
               "[info] * Attributed(${CSR_CACHE}/https/repo1.maven.org/maven2/x/y/1/y-1.jar)\n" \
               "[info] * Attributed(${CSR_CACHE}/https/repo1.maven.org/maven2/x/y/1/y-1.jar)\n[success] done\n"
        self.assertEqual(T.parse_sbt_export(text),
                         ["${OUT}/a.jar>sha256-00ff/12", "${CSR_CACHE}/https/repo1.maven.org/maven2/x/y/1/y-1.jar"])

    def test_maven_coordinates_from_a_cache_path(self):
        self.assertEqual(T.maven_coords("${CSR_CACHE}/https/repo1.maven.org/maven2/com/softwaremill/sttp/tapir/"
                                        "tapir-core_3/1.13.31/tapir-core_3-1.13.31.jar"),
                         ("com.softwaremill.sttp.tapir", "tapir-core_3", "1.13.31"))
        self.assertIsNone(T.maven_coords("${OUT}/jvm/scala-3.10.0/histmersion-core/histmersion-core_3-0.1.0.jar"))

    def test_pom_licences_and_parent(self):
        pom = """<project xmlns="http://maven.apache.org/POM/4.0.0">
          <parent><groupId>org.x</groupId><artifactId>parent</artifactId><version>2</version></parent>
          <licenses><license><name>MIT</name></license><license><url>https://e.org/l</url></license></licenses>
        </project>"""
        self.assertEqual(T.pom_licences(pom), ["MIT", "https://e.org/l"])
        self.assertEqual(T.pom_parent(pom), ("org.x", "parent", "2"))
        self.assertEqual(T.pom_licences("<project/>"), [])
        self.assertIsNone(T.pom_parent("<project/>"))
        self.assertEqual(T.pom_path("org.x", "p", "2"), "org/x/p/2/p-2.pom")


class LockfileTest(unittest.TestCase):
    def test_uv_ships_main_and_ocr_dependencies_never_dev(self):
        lock = '''
version = 1
[[package]]
name = "app"
version = "0.1.0"
dependencies = [{ name = "fastapi" }]
[package.optional-dependencies]
ocr = [{ name = "docling" }]
[package.dev-dependencies]
dev = [{ name = "pytest" }]

[[package]]
name = "fastapi"
version = "1.0"
dependencies = [{ name = "uvicorn", extra = ["standard"] }]

[[package]]
name = "uvicorn"
version = "2.0"
[package.optional-dependencies]
standard = [{ name = "httptools" }]

[[package]]
name = "httptools"
version = "3.0"

[[package]]
name = "docling"
version = "4.0"

[[package]]
name = "pytest"
version = "8.0"
'''
        self.assertEqual(T.uv_shipped(lock, "app"),
                         [("docling", "4.0"), ("fastapi", "1.0"), ("httptools", "3.0"), ("uvicorn", "2.0")])

    def test_pixi_lists_one_platform_and_noarch(self):
        lock = "packages:\n" \
               "- conda: https://conda.anaconda.org/conda-forge/linux-64/zlib-1.3.2-h1_0.conda\n  license: Zlib\n" \
               "- conda: https://conda.anaconda.org/conda-forge/osx-arm64/postgis-3.6.1-h2_1.conda\n" \
               "  sha256: x\n  license: GPL-2.0-or-later\n" \
               "- conda: https://conda.anaconda.org/conda-forge/noarch/tzdata-2026a-h3_0.conda\n  license: LicenseRef-Public-Domain\n"
        self.assertEqual(T.pixi_packages(lock, "osx-arm64"),
                         [("postgis", "3.6.1", "GPL-2.0-or-later"), ("tzdata", "2026a", "LicenseRef-Public-Domain")])

    def test_npm_marks_dev_packages(self):
        lock = json.dumps({"packages": {"": {"name": "root"},
                                        "node_modules/vite": {"version": "8.3.5", "license": "MIT", "dev": True},
                                        "node_modules/a/node_modules/b": {"version": "1", "license": "ISC"}}})
        self.assertEqual(T.npm_packages(lock), [("b", "1", "ISC", False), ("vite", "8.3.5", "MIT", True)])

    def test_pypi_prefers_the_expression_then_a_short_text_then_classifiers(self):
        self.assertEqual(T.pypi_licence({"license_expression": "AGPL-3.0-or-later", "license": "x"}),
                         "AGPL-3.0-or-later")
        self.assertEqual(T.pypi_licence({"license": "BSD-3-Clause"}), "BSD-3-Clause")
        self.assertEqual(T.pypi_licence({"license": "long\ntext", "classifiers": [
            "License :: OSI Approved :: MIT License", "Programming Language :: Python"]}), "MIT License")


class MarkdownTest(unittest.TestCase):
    def test_flagged_rows_are_repeated_at_the_end(self):
        md = T.markdown([("Java", None, [("a:b", "1", "MIT", "compatible"), ("c:d", "2", "", "check")])])
        self.assertIn("| a:b | 1 | MIT | compatible |", md)
        self.assertIn("| Java | c:d | 2 | (none stated) | check |", md)
        self.assertEqual(T.markdown([("Java", None, [])]).split("## Not `compatible`")[1].strip(), "None.")


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""Tests of build.py's pure transformations (#987). Stdlib only:

    python3 -m unittest db/places/test_build.py
"""
import csv, json, os, sys, tempfile, unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402


def write_csv(path, header, rows):
    with open(path, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(header)
        w.writerows(rows)


PLACES_HEADER = ["id", "title", "reprLat", "reprLong", "locationPrecision", "featureTypes", "minDate", "maxDate",
                 "timePeriodsKeys"]
NAMES_HEADER = ["pid", "title", "nameTransliterated", "nameAttested", "nameLanguage"]


class PleiadesNames(unittest.TestCase):
    """The places dump has only titles: "Dead Sea" lives in the NAMES dump, which was never read."""

    def build(self, places, names):
        with tempfile.TemporaryDirectory() as d:
            p, n, out = (os.path.join(d, f) for f in ("places.csv", "names.csv", "out.json"))
            write_csv(p, PLACES_HEADER, places)
            write_csv(n, NAMES_HEADER, names)
            build.pleiades(p, n, out)
            return {f["@id"]: f for f in json.load(open(out))["features"]}

    def test_english_and_attested_names_join_the_title(self):
        fs = self.build(
            [["697709", "Mortuum Mare/Asphaltitis Limne", "31.5", "35.5", "precise", "lake", "-30", "2100", ""]],
            [["/places/697709", "Dead Sea", "Dead Sea", "Dead Sea", "en"],
             ["/places/697709", "Asphaltitis Limne", "Asphaltitis Limne", "ἀσφαλτῖτις λίμνη", "grc"],
             ["/places/697709", "Untitled", "Untitled", "", ""],
             ["/places/999", "Elsewhere", "Elsewhere", "", "en"]])
        names = [(n["toponym"], n.get("lang", "")) for n in fs["pleiades_697709"]["names"]]
        self.assertIn(("Dead Sea", "en"), names)
        self.assertIn(("ἀσφαλτῖτις λίμνη", "grc"), names)
        self.assertIn(("Mortuum Mare", ""), names)
        self.assertNotIn(("Untitled", ""), names)
        self.assertNotIn("when", fs["pleiades_697709"]["names"][-1], "names carry no date range")

    def test_a_slash_inside_an_aside_is_not_an_alternate(self):
        fs = self.build([["1", "Iberia (modern region/peninsula)", "40", "-5", "precise", "region", "", "", ""]], [])
        self.assertEqual([n["toponym"] for n in fs["pleiades_1"]["names"]], ["Iberia (modern region/peninsula)"])

    def test_outside_the_window_and_unlocated_are_dropped(self):
        fs = self.build([["1", "Far", "70", "100", "precise", "settlement", "", "", ""],
                         ["2", "Lost", "40", "20", "unlocated", "settlement", "", "", ""],
                         ["3", "Kept", "40", "20", "precise", "settlement", "-500", "300", ""]], [])
        self.assertEqual(list(fs), ["pleiades_3"])


class NaturalEarthNames(unittest.TestCase):

    def test_capitals_are_title_cased_and_abbreviations_spelled_out(self):
        self.assertEqual(build.title_case("BALKAN PEN."), "Balkan Pen.")
        self.assertEqual(build.expand("Balkan Pen."), "Balkan Peninsula")
        self.assertEqual(build.expand("Taurus Mts."), "Taurus Mountains")
        self.assertEqual(build.title_case("Sea of Galilee"), "Sea of Galilee", "mixed case is left alone")

    def test_the_label_is_ne_own_name_and_the_english_gloss_a_further_name(self):
        p = {"name": "BALKAN PEN.", "name_en": "Balkans", "name_de": "Balkanhalbinsel"}
        label = build.ne_label(p)
        self.assertEqual(label, "Balkan Peninsula")
        names = build.ne_names(p, label)
        self.assertIn(("Balkans", "en"), names)
        self.assertIn(("Balkanhalbinsel", "de"), names)
        self.assertEqual(build.ne_river_label({"name": "Dnipro", "name_en": "Dnieper"}), "Dnieper")

    def test_simplify_keeps_shape_ends_and_rings_closed(self):
        line = [[0, 0], [1, 0.0001], [2, 0], [3, 5], [4, 0]]
        self.assertEqual(build._dp(line, 0.01), [[0, 0], [2, 0], [3, 5], [4, 0]])
        ring = [[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]
        g = build.simplify({"type": "Polygon", "coordinates": [ring]})
        self.assertEqual(g["coordinates"][0][0], g["coordinates"][0][-1])
        self.assertGreaterEqual(len(g["coordinates"][0]), 4)

    def test_the_window_tests_the_bbox_centre(self):
        self.assertTrue(build.in_window({"type": "Point", "coordinates": [19.9, 50.1]}))
        self.assertFalse(build.in_window({"type": "Point", "coordinates": [-100, 40]}))
        self.assertFalse(build.in_window({"type": "Point", "coordinates": []}))


class Supplement(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        d = self.dir.name
        os.mkdir(os.path.join(d, "supplement"))
        build.write(os.path.join(d, "01-x.json"),
                    [build.feature("ne_lake_1", "Sea of Galilee", "feature", names=[("Sea of Galilee", "")])])
        write_csv(os.path.join(d, "supplement", "places.csv"),
                  ["id", "label", "kind", "lon", "lat", "names", "source"],
                  [["curated_arkona", "Arkona (Jaromarsburg)", "site", "13.4369", "54.6766", "Arkona|Jaromarsburg",
                    "Wikidata Q460230"]])
        write_csv(os.path.join(d, "supplement", "names.csv"), ["place_id", "name", "lang", "why"],
                  [["ne_lake_1", "Lake Kinneret", "en", "test"]])

    def tearDown(self):
        self.dir.cleanup()

    def features(self, f):
        return {x["@id"]: x for x in json.load(open(os.path.join(self.dir.name, f)))["features"]}

    def test_curated_places_and_names_land_and_a_second_run_changes_nothing(self):
        build.supplement(self.dir.name)
        lake = self.features("01-x.json")["ne_lake_1"]
        self.assertIn({"toponym": "Lake Kinneret", "lang": "en"}, lake["names"])
        arkona = self.features(build.CURATED_FILE)["curated_arkona"]
        self.assertEqual(arkona["geometry"], {"type": "Point", "coordinates": [13.4369, 54.6766]})
        self.assertEqual(arkona["properties"]["source"], "Wikidata Q460230")
        snapshot = {f: open(os.path.join(self.dir.name, f)).read() for f in ("01-x.json", build.CURATED_FILE)}
        build.supplement(self.dir.name)
        for f, text in snapshot.items():
            self.assertEqual(open(os.path.join(self.dir.name, f)).read(), text, f)

    def test_a_curated_point_needs_a_source(self):
        write_csv(os.path.join(self.dir.name, "supplement", "places.csv"),
                  ["id", "label", "kind", "lon", "lat", "names", "source"],
                  [["curated_x", "X", "site", "1", "2", "", ""]])
        with self.assertRaises(SystemExit):
            build.supplement(self.dir.name)

    def test_a_name_for_a_place_no_seed_file_has_is_an_error(self):
        write_csv(os.path.join(self.dir.name, "supplement", "names.csv"), ["place_id", "name", "lang", "why"],
                  [["pleiades_nope", "Nowhere", "en", "test"]])
        with self.assertRaises(SystemExit):
            build.supplement(self.dir.name)


class CommittedSupplement(unittest.TestCase):
    """The committed CSVs: every curated point cites a source, and no id is curated twice."""

    def test_the_committed_supplement_is_well_formed(self):
        here = os.path.dirname(os.path.abspath(__file__))
        rows = list(csv.DictReader(open(os.path.join(here, "supplement", "places.csv"), newline="")))
        self.assertTrue(rows)
        self.assertEqual(len({r["id"] for r in rows}), len(rows))
        for r in rows:
            self.assertTrue(r["id"].startswith("curated_"), r["id"])
            self.assertTrue(r["source"].strip(), r["id"])
            self.assertIn(r["kind"], ("site", "region", "feature"))
            self.assertTrue(-180 <= float(r["lon"]) <= 180 and -90 <= float(r["lat"]) <= 90, r["id"])


if __name__ == "__main__":
    unittest.main()

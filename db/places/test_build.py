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

    def test_unlocated_are_dropped_and_every_region_kept(self):
        fs = self.build([["1", "Muziris", "10.16", "76.2", "precise", "settlement", "", "", ""],
                         ["2", "Lost", "40", "20", "unlocated", "settlement", "", "", ""],
                         ["3", "Kept", "40", "20", "precise", "settlement", "-500", "300", ""],
                         ["4", "Aksum", "14.13", "38.72", "precise", "settlement", "", "", ""]], [])
        self.assertEqual(sorted(fs), ["pleiades_1", "pleiades_3", "pleiades_4"],
                         "South India and Ethiopia lay outside the window the seed was cut to until 2026-10-27")


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

    def test_the_window_is_the_world_and_tests_the_bbox_centre(self):
        self.assertTrue(build.in_window({"type": "Point", "coordinates": [19.9, 50.1]}))
        self.assertTrue(build.in_window({"type": "Point", "coordinates": [-100, 40]}), "the Americas too")
        self.assertTrue(build.in_window({"type": "Point", "coordinates": [151.2, -33.9]}), "and Oceania")
        self.assertFalse(build.in_window({"type": "Point", "coordinates": []}))
        line = {"type": "LineString", "coordinates": [[0, 0], [30, 10]]}
        self.assertTrue(build.in_window(line, (10, 0, 20, 10)), "centre (15, 5)")
        self.assertFalse(build.in_window(line, (20, 0, 40, 10)))

    def test_every_natural_earth_language(self):
        names = build.ne_names({"name": "Huang He", "name_zh": "黄河", "name_zht": "黃河", "name_hi": "ह्वांग हो"}, "Huang He")
        self.assertIn(("黄河", "zh"), names)
        self.assertIn(("黃河", "zh-Hant"), names)
        self.assertIn(("ह्वांग हो", "hi"), names)


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


class PleiadesKinds(unittest.TestCase):
    """What a point-only Pleiades "region" really is (2026-10-09): peoples and civic tribes, provinces,
    demes — the twin of core.PlaceReview's type rules."""

    def test_peoples_provinces_and_settlements(self):
        self.assertEqual(build.pleiades_kind(["people"]), "people")                    # Aiantis, Ubii
        self.assertEqual(build.pleiades_kind(["label", "people"]), "people")           # Dobunni
        self.assertEqual(build.pleiades_kind(["province"]), "polity")                  # Viennensis
        self.assertEqual(build.pleiades_kind(["province-2"]), "polity")                # was a site
        self.assertEqual(build.pleiades_kind(["region", "province-2"]), "polity")      # Hither Province (Pylos)
        self.assertEqual(build.pleiades_kind(["region", "label", "state", "people"]), "polity")
        self.assertEqual(build.pleiades_kind(["region", "settlement", "deme-attic"]), "site")  # Peiraieus
        self.assertEqual(build.pleiades_kind(["region"]), "region")
        self.assertEqual(build.pleiades_kind(["region", "label", "settlement-modern", "coast"],
                                             "Salmydessos (coastal region)"), "region", "the title says region")
        self.assertEqual(build.pleiades_kind(["river"]), "feature")
        self.assertEqual(build.pleiades_kind(["river", "people"]), "feature", "a feature stays a feature")
        self.assertEqual(build.pleiades_kind(["settlement", "province"]), "site", "a provincial capital stays a site")
        self.assertEqual(build.pleiades_kind(["temple"]), "site")

    def test_withdrawn_duplicates_are_left_out(self):
        self.assertTrue(build.withdrawn_duplicate("Withdrawn (duplicate): Creta et Cyrene"))
        self.assertTrue(build.withdrawn_duplicate("Withdrawn duplicate: Moesia Superior"))
        self.assertFalse(build.withdrawn_duplicate("Withdrawn: Untitled"))
        self.assertFalse(build.withdrawn_duplicate("Creta et Cyrene (Roman province)"))

    def test_build_and_reclassify(self):
        with tempfile.TemporaryDirectory() as d:
            p, n, out = (os.path.join(d, f) for f in ("places.csv", "names.csv", "out.json"))
            write_csv(p, PLACES_HEADER, [
                ["330976685", "Aiantis", "37.93731", "23.70625", "rough", "people", "", "", ""],
                ["981515", "Withdrawn (duplicate): Creta et Cyrene", "35.06", "24.95", "rough", "province", "", "", ""],
                ["580062", "Peiraieus/Piraeus", "37.93722", "23.64461", "precise", "region,settlement,deme-attic",
                 "-750", "2100", ""]])
            write_csv(n, NAMES_HEADER, [])
            build.pleiades(p, n, out)
            fs = {f["@id"]: f for f in json.load(open(out))["features"]}
            self.assertEqual(set(fs), {"pleiades_330976685", "pleiades_580062"})
            self.assertEqual(fs["pleiades_330976685"]["properties"]["kind"], "people")
            self.assertEqual(fs["pleiades_330976685"]["types"], [{"label": "people"}])
            self.assertEqual(fs["pleiades_580062"]["properties"]["kind"], "site")
            # an OLD build (kinds as before, the duplicate in) is brought up to date in place
            old = json.load(open(out))
            old["features"][0]["properties"]["kind"] = "region"
            old["features"].append(dict(old["features"][1], **{"@id": "pleiades_981515",
                "properties": dict(old["features"][1]["properties"], title="Withdrawn (duplicate): X")}))
            json.dump(old, open(out, "w"))
            build.reclassify(out)
            fs = {f["@id"]: f for f in json.load(open(out))["features"]}
            self.assertEqual(set(fs), {"pleiades_330976685", "pleiades_580062"})
            self.assertEqual(fs["pleiades_330976685"]["properties"]["kind"], "people")
            before = open(out).read()
            build.reclassify(out)
            self.assertEqual(open(out).read(), before, "idempotent")


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

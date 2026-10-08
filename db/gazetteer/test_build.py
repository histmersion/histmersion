#!/usr/bin/env python3
"""Tests of build.py's transformations (#1064). Stdlib only:

    python3 -m unittest db/gazetteer/test_build.py
"""
import gzip, json, os, sys, tempfile, unittest, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402


def gn_row(gid, name, lat, lon, fclass, fcode, cc="TR", population=0, ascii_name=None):
    cols = [str(gid), name, ascii_name or name, "", str(lat), str(lon), fclass, fcode, cc, "", "", "", "", "",
            str(population), "", "", "Europe/Istanbul", "2026-10-01"]
    return "\t".join(cols)


def alt_row(aid, gid, lang, name, historic="", frm="", to=""):
    return "\t".join([str(aid), str(gid), lang, name, "", "", "", historic, frm, to])


def features(path):
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return [json.loads(line) for line in fh]


class GeoNames(unittest.TestCase):
    ROWS = [
        gn_row(1, "Boğazkale", 40.02, 34.61, "P", "PPLA2", population=1500, ascii_name="Bogazkale"),
        gn_row(2, "Hamlet", 40.1, 34.7, "P", "PPL"),                          # a bare dot: dropped
        gn_row(3, "Old Hamlet", 40.2, 34.8, "P", "PPL"),                      # kept: a historic name
        gn_row(4, "Hotel Hattusa", 40.03, 34.62, "S", "HTL"),                 # a hotel: dropped
        gn_row(5, "Hattusa ruins", 40.02, 34.61, "S", "ANS"),                 # an ancient site: kept
        gn_row(6, "Somewhere far", 40.0, -100.0, "P", "PPLA"),                # outside the window
        gn_row(7, "Quarter", 40.0, 34.0, "P", "PPLX"),                        # a section: dropped
        gn_row(8, "Linked village", 40.0, 35.0, "P", "PPL"),                  # kept: a Wikidata link
    ]
    ALTS = [
        alt_row(10, 1, "hit", "Ḫattuša"),
        alt_row(11, 1, "tr", "Boğazköy", historic="1", to="1937"),
        alt_row(12, 1, "link", "https://en.wikipedia.org/wiki/Bo%C4%9Fazkale"),
        alt_row(13, 1, "zh", "博阿兹卡莱"),
        alt_row(14, 3, "en", "Ancient Hamlet", historic="1"),
        alt_row(15, 8, "wkdt", "Q999"),
        alt_row(16, 1, "en", "Mikoyan", frm="1935", to="1956"),
    ]

    def build(self):
        d = tempfile.mkdtemp()
        a, n, out = (os.path.join(d, f) for f in ("all.zip", "alt.zip", "out.ndjson.gz"))
        with zipfile.ZipFile(a, "w") as z:
            z.writestr("allCountries.txt", "\n".join(self.ROWS) + "\n")
        with zipfile.ZipFile(n, "w") as z:
            z.writestr("alternateNamesV2.txt", "\n".join(self.ALTS) + "\n")
        stats = build.geonames(a, n, out)
        return stats, out

    def test_filters_window_kinds_and_bare_hamlets(self):
        stats, out = self.build()
        ids = [f["@id"] for f in features(out)]
        self.assertEqual(ids, [f"https://sws.geonames.org/{i}/" for i in (1, 3, 5, 8)])
        self.assertEqual(stats["kept"], 4)

    def test_names_languages_and_dates(self):
        _, out = self.build()
        f = {x["@id"]: x for x in features(out)}["https://sws.geonames.org/1/"]
        names = {(n["toponym"], n.get("lang", "")): n for n in f["names"]}
        self.assertIn(("Ḫattuša", "hit"), names)
        self.assertIn(("Bogazkale", ""), names)
        self.assertNotIn(("博阿兹卡莱", "zh"), names, "a language outside the research window's")
        self.assertFalse(any("wikipedia" in n["toponym"] for n in f["names"]), "a link is no name")
        self.assertEqual(names[("Boğazköy", "tr")]["when"], {"timespans": [{"end": {"in": "1937"}}]})
        self.assertEqual(names[("Mikoyan", "en")]["when"], {"timespans": [{"start": {"in": "1935"}, "end": {"in": "1956"}}]})
        self.assertEqual(f["properties"]["kind"], "site")
        self.assertTrue(f["properties"]["modern"])
        self.assertEqual(f["geometry"], {"type": "Point", "coordinates": [34.61, 40.02]})

    def test_wikidata_link(self):
        _, out = self.build()
        f = {x["@id"]: x for x in features(out)}["https://sws.geonames.org/8/"]
        self.assertEqual(f["links"], [{"type": "exactMatch", "identifier": "wd:Q999"}])

    def test_deterministic_bytes(self):
        _, a = self.build()
        _, b = self.build()
        self.assertEqual(build.sha256(a), build.sha256(b))


class Wikidata(unittest.TestCase):
    def write(self, d, name, header, rows):
        with open(os.path.join(d, name + ".tsv"), "w", encoding="utf-8") as fh:
            fh.write("\t".join("?" + h for h in header) + "\n")
            for r in rows:
                fh.write("\t".join(r) + "\n")

    def build(self):
        d = tempfile.mkdtemp()
        e = "<http://www.wikidata.org/entity/{}>"
        self.write(d, "core", ["item", "root", "lon", "lat"], [
            [e.format("Q5684"), e.format("Q15661340"), "44.42415", "32.5363"],
            [e.format("Q5684"), e.format("Q486972"), "44.42111", "32.5425"],
            [e.format("Q1"), e.format("Q486972"), "30.0", "40.0"],     # a plain settlement: dropped
            [e.format("Q2"), e.format("Q486972"), "31.0", "41.0"],     # a settlement with a GeoNames id: kept
        ])
        self.write(d, "labels", ["item", "name"], [
            [e.format("Q5684"), '"Babylon"@en'], [e.format("Q5684"), '"Βαβυλών"@grc'],
            [e.format("Q1"), '"Nowhere"@en'], [e.format("Q2"), '"Linked"@en'], [e.format("Q5684"), '"巴比伦"@zh']])
        self.write(d, "aliases", ["item", "name"], [[e.format("Q5684"), '"Babili \\"Gate of God\\""@en']])
        self.write(d, "time_P571", ["item", "t"], [[e.format("Q5684"), "-2200-01-01T00:00:00Z"]])
        for p in ("P576", "P580", "P582"):
            self.write(d, f"time_{p}", ["item", "t"], [])
        self.write(d, "id_P1667", ["item", "v"], [])
        self.write(d, "native", ["item", "name"], [])
        self.write(d, "id_P1566", ["item", "v"], [[e.format("Q5684"), '"98228"'], [e.format("Q2"), '"123"']])
        self.write(d, "id_P1584", ["item", "v"], [[e.format("Q5684"), '"893951"']])
        self.write(d, "official", ["item", "name", "start", "end"], [[e.format("Q2"), '"Linkedgrad"@ru', "1920-01-01T00:00:00Z", "1991-01-01T00:00:00Z"]])
        out = os.path.join(d, "out.ndjson.gz")
        return build.wikidata(d, out), out

    def test_items_names_dates_links(self):
        stats, out = self.build()
        fs = {f["@id"].rsplit("/", 1)[1]: f for f in features(out)}
        self.assertEqual(sorted(fs), ["Q2", "Q5684"])
        b = fs["Q5684"]
        self.assertEqual(b["properties"]["kind"], "site", "ancient city outranks settlement")
        self.assertEqual(b["properties"]["class"], "Q15661340")
        self.assertEqual(b["when"], {"timespans": [{"start": {"in": "-2201"}}]}, "XSD 1.1 year -2200 is 2201 BCE")
        self.assertNotIn("modern", b["properties"])
        names = {(n["toponym"], n.get("lang", "")) for n in b["names"]}
        self.assertIn(("Βαβυλών", "grc"), names)
        self.assertIn(('Babili "Gate of God"', "en"), names)
        self.assertNotIn(("巴比伦", "zh"), names)
        self.assertEqual(b["links"], [{"identifier": "gn:98228", "type": "exactMatch"},
                                      {"identifier": "pl:893951", "type": "closeMatch"}])
        self.assertEqual(b["geometry"]["coordinates"], [44.42111, 32.5425], "the smallest of several points")
        q2 = {(n["toponym"], n.get("lang", "")): n for n in fs["Q2"]["names"]}
        self.assertEqual(q2[("Linkedgrad", "ru")]["when"], {"timespans": [{"start": {"in": "1920"}, "end": {"in": "1991"}}]})
        self.assertTrue(fs["Q2"]["properties"]["modern"], "undated: never drawn on the time-sliced map")


class Helpers(unittest.TestCase):
    def test_years(self):
        self.assertEqual(build.year_of("201907"), 2019)
        self.assertEqual(build.year_of("1935"), 1935)
        self.assertEqual(build.year_of("-500"), -500)
        self.assertIsNone(build.year_of("soon"))
        self.assertEqual(build.wd_year("-0499-01-01T00:00:00Z"), -500)
        self.assertEqual(build.wd_year("0000-01-01T00:00:00Z"), -1)
        self.assertEqual(build.wd_year("1453-05-29T00:00:00Z"), 1453)

    def test_literals(self):
        self.assertEqual(build.literal('"Ἀθῆναι"@grc'), ("Ἀθῆναι", "grc"))
        self.assertEqual(build.literal('"a\\tb \\u00e9"@EN'), ("a\tb é", "en"))
        self.assertEqual(build.qid("<http://www.wikidata.org/entity/Q42>"), "Q42")

    def test_pin(self):
        d = tempfile.mkdtemp()
        f = os.path.join(d, "x.ndjson.gz")
        build.write_ndjson_gz(f, [{"@id": "a"}, {"@id": "b"}])
        m = os.path.join(d, "extracts.json")
        e = build.pin(m, "geonames", f, ["version=v1", "url="])
        self.assertEqual((e["features"], e["version"], e["url"]), (2, "v1", ""))
        self.assertEqual(json.load(open(m))["extracts"][0]["sha256"], build.sha256(f))


if __name__ == "__main__":
    unittest.main()

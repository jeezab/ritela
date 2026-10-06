"""Regression checks for upgrade ordering and distribution rejection rules."""

import json
from pathlib import Path
import tempfile
import unittest
from urllib.parse import unquote

import release


class ReleaseChecks(unittest.TestCase):
    def test_release_pages_include_every_page_and_empty_repository(self):
        old = {"tag_name": "v0.1.0"}
        newer = {"tag_name": "v0.3.0"}
        self.assertEqual(release.flatten_release_pages([[old], [newer], []]), [old, newer])
        self.assertEqual(release.flatten_release_pages([[]]), [])
        self.assertEqual(release.flatten_release_pages([]), [])
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "version.properties").write_text("versionName=0.2.0\n")
            (root / "README.md").write_text(release.obtainium_link())
            with self.assertRaises(ValueError):
                release.check(root, releases=release.flatten_release_pages([[old], [newer]]))

    def test_malformed_release_pages_fail_closed(self):
        for pages in [None, {}, [{"tag_name": "v0.1.0"}], [[None]], [[{}]]]:
            with self.subTest(pages=pages), self.assertRaises(ValueError):
                release.flatten_release_pages(pages)

    def test_version_order_across_component_rollovers(self):
        versions = ["0.0.1", "0.1.0", "0.999.999", "1.0.0", "2000.999.999"]
        codes = [release.version_code(v) for v in versions]
        self.assertEqual(codes, sorted(set(codes)))
        self.assertLess(codes[-1], 2_100_000_000)

    def test_invalid_or_ambiguous_versions(self):
        for name in ["0.0.0", "01.2.3", "1.2", "v1.2.3", "1.2.3-beta", "1.1000.0", "2001.0.0"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                release.version_code(name)

    def test_obtainium_config_round_trip(self):
        payload = release.obtainium_link().split("obtainium://app/", 1)[1]
        self.assertEqual(json.loads(unquote(payload)), release.CONFIG)
        self.assertEqual(release.CONFIG["id"], "app.ritela")

    def test_tag_and_release_order_guards(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "version.properties").write_text("versionName=0.2.0\n")
            (root / "README.md").write_text(release.obtainium_link())
            self.assertEqual(release.check(root, "v0.2.0"), ("0.2.0", 2000))
            for tag in ["v0.1.0", "0.2.0", "v0.2.0-beta"]:
                with self.subTest(tag=tag), self.assertRaises(ValueError):
                    release.check(root, tag)
            for tag in ["v0.2.0", "v0.3.0"]:
                with self.subTest(previous=tag), self.assertRaises(ValueError):
                    release.check(root, releases=[{"tag_name": tag}])
            release.check(root, releases=[{"tag_name": "v0.1.0"}])
            release.check(root, releases=[{"tag_name": "v0.3.0-beta", "prerelease": True}])
            (root / "README.md").write_text("No link")
            with self.assertRaises(ValueError):
                release.check(root)

    def test_artifact_identity_and_certificate(self):
        certificate = "ab" * 32
        badging = "package: name='app.ritela' versionCode='2000' versionName='0.2.0'\n"
        signature = "Signer #1 certificate SHA-256 digest: " + certificate + "\n"
        release.validate_apk(badging, signature, "0.2.0", 2000, certificate)
        cases = [
            (badging.replace("app.ritela", "another.app"), signature),
            (badging.replace("2000", "1"), signature),
            (badging.replace("0.2.0", "0.1.0"), signature),
            (badging + "application-debuggable\n", signature),
            (badging + "uses-permission: name='android.permission.INTERNET'\n", signature),
            (badging, signature.replace("ab", "cd")),
            (badging, signature + "Signer #2 certificate SHA-256 digest: " + certificate + "\n"),
        ]
        for metadata, signing in cases:
            with self.subTest(metadata=metadata, signing=signing), self.assertRaises(ValueError):
                release.validate_apk(metadata, signing, "0.2.0", 2000, certificate)


if __name__ == "__main__":
    unittest.main()

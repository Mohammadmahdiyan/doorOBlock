import os
import tempfile
import unittest

import build_domain_list as b


class ParseTests(unittest.TestCase):
    def test_formats(self):
        self.assertEqual(b.parse_line("0.0.0.0 Example.COM"), "example.com")
        self.assertEqual(b.parse_line("127.0.0.1 www.example.com # c"), "example.com")
        self.assertEqual(b.parse_line("example.com"), "example.com")
        self.assertEqual(b.parse_line("||sub.example.com^"), "sub.example.com")
        self.assertEqual(b.parse_line("example.com."), "example.com")

    def test_invalid(self):
        for bad in ["", "# c", "! c", "localhost", "127.0.0.1 localhost", "0.0.0.0 0.0.0.0",
                    "a b c", "http://x.com/p", "-bad-.com", "x.invalidtld1", "bad_domain.com",
                    "x" * 300 + ".com", "foo.local", "*.example.com", "a@b.com"]:
            self.assertIsNone(b.parse_line(bad), bad)

    def test_dedupe_protected_and_counts(self):
        text = "# h\n0.0.0.0 a.example.net\n0.0.0.0 a.example.net\nb.example.net\n0.0.0.0 google.com\njunk line here\n"
        d, valid, invalid = b.parse_text(text)
        self.assertEqual(d, {"a.example.net", "b.example.net"})
        self.assertEqual((valid, invalid), (4, 1))


class BuildTests(unittest.TestCase):
    def _dir(self, texts):
        d = tempfile.mkdtemp()
        for i, t in enumerate(texts):
            with open(os.path.join(d, f"{i}.txt"), "w", encoding="utf-8") as f:
                f.write(t)
        return d

    def test_merge_and_write(self):
        src = self._dir(["\n".join(f"0.0.0.0 s{i}.one.net" for i in range(5)),
                         "\n".join(f"s{i}.one.net" for i in range(3, 8))])
        out = os.path.join(tempfile.mkdtemp(), "o.txt")
        self.assertEqual(b.main(["--from-dir", src, "--out", out, "--min-per-source", "3"]), 0)
        lines = [l for l in open(out, encoding="utf-8").read().splitlines() if not l.startswith("#")]
        self.assertEqual(len(lines), 8)
        self.assertEqual(lines, sorted(set(lines)))

    def test_bad_source_skipped_and_existing_kept(self):
        out = os.path.join(tempfile.mkdtemp(), "o.txt")
        with open(out, "w", encoding="utf-8") as f:
            f.write("# old\nold.example.net\n")
        src = self._dir(["<html>not a list</html>\n" * 50, "also junk\n" * 50])
        self.assertEqual(b.main(["--from-dir", src, "--out", out, "--min-per-source", "3"]), 1)
        self.assertIn("old.example.net", open(out, encoding="utf-8").read())

    def test_one_good_one_bad(self):
        src = self._dir(["junk\n" * 20, "\n".join(f"h{i}.two.net" for i in range(10))])
        out = os.path.join(tempfile.mkdtemp(), "o.txt")
        self.assertEqual(b.main(["--from-dir", src, "--out", out, "--min-per-source", "3"]), 0)
        self.assertIn("h9.two.net", open(out, encoding="utf-8").read())


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""
فهرست پایه‌ی دامنه‌ها را از منابع عمومی می‌سازد و در assets/lists/domains_base.txt می‌نویسد.
روی GitHub Actions قبل از بیلد اجرا می‌شود. اگر هر چیزی خراب باشد، فایل فعلی (seed) دست‌نخورده
می‌ماند و کد خروج غیرصفر است تا بیلد با همان seed ادامه یابد.

منابع و مجوزها: docs/LISTS.md
قواعد پارس باید با DomainParser.kt یکی بماند.
"""
import argparse
import datetime
import os
import re
import sys
import tempfile
import urllib.request

SOURCES = {
    "blocklistproject/porn (Unlicense)": "https://blocklistproject.github.io/Lists/porn.txt",
    "StevenBlack/hosts porn-only (MIT)": "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
}
PROTECTED = {
    "quran.com", "google.com", "bing.com", "duckduckgo.com", "yahoo.com",
    "wikipedia.org", "github.com", "githubusercontent.com", "github.io",
}
IP_HEAD = {"0.0.0.0", "127.0.0.1", "::", "::1"}
BAD_TLDS = {"local", "localhost", "localdomain", "invalid", "test"}
LABEL = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
TLD = re.compile(r"^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")
MAX_BYTES = 64 * 1024 * 1024
MIN_PER_SOURCE = 1000


def is_valid_domain(d):
    if not d or len(d) > 253:
        return False
    labels = d.split(".")
    if len(labels) < 2:
        return False
    if not TLD.match(labels[-1]) or labels[-1] in BAD_TLDS:
        return False
    return all(LABEL.match(l) for l in labels)


def ignorable(raw):
    s = raw.strip()
    return not s or s[0] in "#!["


def parse_line(raw):
    if len(raw) > 1000:
        return None
    s = raw.strip().lower()
    if not s or s[0] in "#![":
        return None
    h = s.find("#")
    if h > 0:
        s = s[:h].strip()
    parts = s.split()
    if len(parts) == 1:
        s = parts[0]
    elif len(parts) == 2 and parts[0] in IP_HEAD:
        s = parts[1]
    else:
        return None
    if s.startswith("||"):
        s = s[2:].split("^")[0].split("$")[0]
    elif any(c in s for c in "^$*/:@|"):
        return None
    s = s.rstrip(".")
    while s.startswith("www."):
        s = s[4:]
    return s if is_valid_domain(s) else None


def parse_text(text):
    """-> (domains:set, valid:int, invalid:int)"""
    domains, valid, invalid = set(), 0, 0
    for line in text.splitlines():
        if ignorable(line):
            continue
        d = parse_line(line)
        if d is None:
            invalid += 1
        else:
            valid += 1
            if d not in PROTECTED:
                domains.add(d)
    return domains, valid, invalid


def check_source(name, domains, valid, invalid, min_entries=MIN_PER_SOURCE):
    total = valid + invalid
    if len(domains) < min_entries:
        raise ValueError(f"{name}: too few entries ({len(domains)})")
    if total and invalid * 5 > total:
        raise ValueError(f"{name}: too many invalid lines ({invalid}/{total})")


def download(url):
    req = urllib.request.Request(url, headers={"User-Agent": "doorOBlock-build"})
    with urllib.request.urlopen(req, timeout=60) as r:
        if not r.geturl().startswith("https://"):
            raise ValueError("redirected to non-https")
        data = r.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("download too large")
    return data.decode("utf-8", "replace")


def write_asset(out_path, per_source, merged, now=None):
    now = now or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        "# doorOBlock bundled domain list",
        f"# generated: {now}",
        f"# total unique domains: {len(merged)}",
    ]
    for name, n in per_source:
        lines.append(f"# source: {name} ({n} domains)")
    body = "\n".join(lines) + "\n" + "\n".join(sorted(merged)) + "\n"
    out_dir = os.path.dirname(os.path.abspath(out_path))
    fd, tmp = tempfile.mkstemp(dir=out_dir, suffix=".tmp")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(body)
    os.replace(tmp, out_path)  # جایگزینی اتمی؛ فایل قبلی تا لحظه‌ی آخر دست‌نخورده است


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/assets/lists/domains_base.txt")
    ap.add_argument("--from-dir", help="برای تست: فایل‌های محلی به‌جای دانلود (نام فایل = شماره‌ی منبع)")
    ap.add_argument("--min-per-source", type=int, default=MIN_PER_SOURCE)
    args = ap.parse_args(argv)

    merged, per_source = set(), []
    for i, (name, url) in enumerate(SOURCES.items()):
        try:
            if args.from_dir:
                with open(os.path.join(args.from_dir, f"{i}.txt"), encoding="utf-8") as f:
                    text = f.read()
            else:
                text = download(url)
            domains, valid, invalid = parse_text(text)
            check_source(name, domains, valid, invalid, args.min_per_source)
        except Exception as e:  # هر منبع خراب نادیده گرفته می‌شود، نه کل فهرست
            print(f"WARNING: skipping {name}: {e}", file=sys.stderr)
            continue
        per_source.append((name, len(domains)))
        merged |= domains
        print(f"{name}: {len(domains)} domains")

    if not per_source:
        print("ERROR: no valid source; keeping the existing list", file=sys.stderr)
        return 1
    write_asset(args.out, per_source, merged)
    print(f"wrote {len(merged)} unique domains to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

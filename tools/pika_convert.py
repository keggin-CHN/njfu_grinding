#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from __future__ import annotations
import argparse
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any
SOURCES: dict[str, str] = {
    "中国近代史纲要": "https://minirainer.top/api/question_test/json/中国近代史纲要.json",
    "习近平新时代中国特色社会主义思想": "https://minirainer.top/api/question_test/json/习近平新时代中国特色社会主义思想.json",
    "马克思主义基本原理": "https://minirainer.top/api/question_test/json/马克思主义基本原理.json",
    "思想道德与法治": "https://minirainer.top/api/question_test/json/思想道德与法治.json",
    "毛泽东思想和中国特色社会主义理论体系概论": "https://minirainer.top/api/question_test/json/毛泽东思想和中国特色社会主义理论体系概论.json",
    "大学生心理健康教育": "https://minirainer.top/api/question_test/json/大学生心理健康教育.json",
    "人工智能": "https://minirainer.top/api/question_test/json/人工智能.json",
}
TYPE_MAP = {
    "单选": "单选题",
    "单选题": "单选题",
    "多选": "多选题",
    "多选题": "多选题",
    "判断": "判断题",
    "判断题": "判断题",
}
OPTION_KEY_ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
def quote_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    path = urllib.parse.quote(urllib.parse.unquote(parsed.path), safe="/")
    return urllib.parse.urlunsplit(
        (parsed.scheme, parsed.netloc, path, parsed.query, parsed.fragment)
    )


def fetch_json(url: str, timeout: int) -> dict[str, Any]:
    request = urllib.request.Request(
        quote_url(url),
        headers={
            "User-Agent": "Mozilla/5.0",
            "Referer": "https://minirainer.top/",
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = response.read()
    return json.loads(body.decode("utf-8-sig"))


def normalize_type(raw_type: Any) -> str:
    text = str(raw_type or "").strip()
    for key, value in TYPE_MAP.items():
        if key in text:
            return value
    return "单选题"


def strip_option_prefix(value: Any) -> str:
    text = str(value or "").strip()
    return re.sub(r"^[A-Z]\s*[.、．:：]\s*", "", text)


def ordered_option_items(options: Any) -> list[tuple[str, str]]:
    if isinstance(options, dict):
        items: list[tuple[str, str]] = []
        for key in OPTION_KEY_ORDER:
            if key in options:
                items.append((key, strip_option_prefix(options[key])))
        for key, value in options.items():
            key_text = str(key).strip().upper()
            if key_text not in OPTION_KEY_ORDER:
                items.append((key_text, strip_option_prefix(value)))
        return items

    if isinstance(options, list):
        items = []
        for index, value in enumerate(options):
            key = OPTION_KEY_ORDER[index] if index < len(OPTION_KEY_ORDER) else str(index + 1)
            items.append((key, strip_option_prefix(value)))
        return items

    return []


def normalize_options(options: Any, question_type: str) -> list[str]:
    items = ordered_option_items(options)
    if question_type == "判断题":
        if not items:
            return ["A. 正确", "B. 错误"]
        return [f"{key}. {value}" for key, value in items if value]
    return [f"{key}. {value}" for key, value in items if value]


def extract_answer_letters(answer: Any) -> str:
    return "".join(ch for ch in str(answer or "").upper() if "A" <= ch <= "Z")


def normalize_judgement_answer(answer: Any, options: Any) -> str:
    letters = extract_answer_letters(answer)
    first_letter = letters[:1]
    option_lookup = {key: value for key, value in ordered_option_items(options)}
    selected_text = option_lookup.get(first_letter, "").strip()

    positive = {"正确", "对", "是", "true", "t", "yes", "y"}
    negative = {"错误", "错", "否", "false", "f", "no", "n"}
    lowered = selected_text.lower()
    if selected_text in positive or lowered in positive:
        return "正确"
    if selected_text in negative or lowered in negative:
        return "错误"

    raw_text = str(answer or "").strip()
    if raw_text in positive or raw_text.lower() in positive:
        return "正确"
    if raw_text in negative or raw_text.lower() in negative:
        return "错误"

    if first_letter == "B":
        return "错误"
    return "正确"


def normalize_answer(answer: Any, question_type: str, options: Any) -> str:
    if question_type == "判断题":
        return normalize_judgement_answer(answer, options)

    letters = extract_answer_letters(answer)
    if letters:
        # Keep multi-choice answers stable for app comparison.
        return "".join(sorted(set(letters), key=OPTION_KEY_ORDER.index))
    return str(answer or "").strip()


def unique_question_key(bucket: dict[str, Any], question: str, record_id: Any) -> str:
    if question not in bucket:
        return question
    suffix = f"（重复题 #{record_id}）" if record_id not in (None, "") else "（重复题）"
    candidate = question + suffix
    counter = 2
    while candidate in bucket:
        candidate = f"{question}{suffix}-{counter}"
        counter += 1
    return candidate


def convert_records(data: dict[str, Any]) -> dict[str, dict[str, Any]]:
    records = data.get("RECORDS")
    if not isinstance(records, list):
        raise ValueError("input JSON must contain a RECORDS array")

    converted: dict[str, dict[str, Any]] = {
        "单选题": {},
        "多选题": {},
        "判断题": {},
    }

    for record in records:
        if not isinstance(record, dict):
            continue

        question = str(record.get("question") or "").strip()
        if not question:
            continue

        question_type = normalize_type(record.get("type"))
        options = record.get("options")
        bucket = converted[question_type]
        key = unique_question_key(bucket, question, record.get("id"))

        item: dict[str, Any] = {
            "answer": normalize_answer(record.get("answer"), question_type, options),
            "options": normalize_options(options, question_type),
        }

        category = str(record.get("category") or "").strip()
        if category:
            item["source"] = category

        bucket[key] = item

    return converted


def convert_one(name: str, url: str, output_dir: Path, timeout: int) -> Path:
    data = fetch_json(url, timeout)
    converted = convert_records(data)
    output_path = output_dir / f"{name}.json"
    output_path.write_text(
        json.dumps(converted, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    total = sum(len(group) for group in converted.values())
    print(f"OK {name}: {total} questions -> {output_path}")
    return output_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert minirainer.top question banks to APP-compatible JSON."
    )
    parser.add_argument(
        "-o",
        "--output-dir",
        default="题库收集/converted_minirainer",
        help="directory to write converted JSON files",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=30,
        help="HTTP request timeout in seconds",
    )
    parser.add_argument(
        "--only",
        choices=sorted(SOURCES),
        nargs="+",
        help="convert only the selected source names",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    selected = SOURCES
    if args.only:
        selected = {name: SOURCES[name] for name in args.only}

    failed = 0
    for name, url in selected.items():
        try:
            convert_one(name, url, output_dir, args.timeout)
        except Exception as exc:
            failed += 1
            print(f"FAIL {name}: {exc}", file=sys.stderr)

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

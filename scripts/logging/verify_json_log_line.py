#!/usr/bin/env python3
"""Validate SSING dev/prod JSON log lines and representative event contracts."""

import argparse
import json
import sys
from pathlib import Path


BASE_STRING_FIELDS = {
    "@timestamp",
    "level",
    "logger_name",
    "thread_name",
    "message",
    "service",
    "env",
}

STRING_FIELDS = {
    "event",
    "request_id",
    "event_id",
    "job_run_id",
    "session_id",
    "error_code",
    "exception_type",
    "provider",
    "operation",
    "method",
    "path",
    "job_name",
    "job_status",
}

NUMBER_FIELDS = {
    "duration_ms",
    "processed_count",
    "success_count",
    "failure_count",
}

EVENT_REQUIRED_FIELDS = {
    "http.request.completed": {
        "request_id",
        "method",
        "path",
        "status",
        "duration_ms",
    },
    "http.request.unhandled_exception": {
        "request_id",
        "status",
        "error_code",
        "exception_type",
    },
    "external.kakao.request.failed": {
        "request_id",
        "provider",
        "operation",
        "error_code",
        "duration_ms",
        "exception_type",
    },
    "matching.offer.expiration.batch.completed": {
        "job_name",
        "job_status",
        "processed_count",
        "success_count",
        "failure_count",
        "duration_ms",
    },
}

SAFE_EVENTS_WITHOUT_STACK_TRACE = {
    "http.request.unhandled_exception",
    "external.kakao.request.failed",
    "matching.offer.expiration.failed",
    "matching.offer.expiration.batch.completed",
    "matching.offer.expiration.batch.failed",
}

FORBIDDEN_KEY_FRAGMENTS = {
    "authorization",
    "access_token",
    "refresh_token",
    "password",
    "cookie",
    "provider_body",
    "response_body",
}


def read_lines(path):
    if path is None:
        return sys.stdin.read().splitlines()
    return Path(path).read_text(encoding="utf-8").splitlines()


def validate_lines(lines, required_events=()):
    parsed_events = []
    for number, line in enumerate((line for line in lines if line.strip()), start=1):
        try:
            event = json.loads(line)
        except json.JSONDecodeError as exc:
            return False, f"line {number} is not valid JSON: {exc.msg}"
        if not isinstance(event, dict):
            return False, f"line {number} must be a JSON object"

        error = validate_event(event, number)
        if error:
            return False, error
        parsed_events.append(event)

    if not parsed_events:
        return False, "no JSON log line supplied"

    seen_events = {event.get("event") for event in parsed_events if "event" in event}
    missing_events = sorted(set(required_events) - seen_events)
    if missing_events:
        return False, f"missing required event(s): {', '.join(missing_events)}"

    return True, f"validated {len(parsed_events)} JSON log line(s)"


def validate_event(event, number):
    missing_base = sorted(field for field in BASE_STRING_FIELDS if field not in event)
    if missing_base:
        return f"line {number} is missing canonical fields: {', '.join(missing_base)}"

    invalid_strings = sorted(
        field
        for field in BASE_STRING_FIELDS | STRING_FIELDS
        if field in event and not isinstance(event[field], str)
    )
    if invalid_strings:
        return f"line {number} has non-string fields: {', '.join(invalid_strings)}"

    invalid_ids = sorted(
        field
        for field, value in event.items()
        if field.endswith("_id") and value is not None and not isinstance(value, str)
    )
    if invalid_ids:
        return f"line {number} has non-string id fields: {', '.join(invalid_ids)}"

    if "status" in event and (
        not isinstance(event["status"], int) or isinstance(event["status"], bool)
    ):
        return f"line {number} has non-integer HTTP status"

    for field in NUMBER_FIELDS:
        if field not in event:
            continue
        value = event[field]
        if not isinstance(value, (int, float)) or isinstance(value, bool):
            return f"line {number} has non-numeric {field}"
        if value < 0:
            return f"line {number} has negative {field}"

    if "job_status" in event and event["job_status"] not in {
        "success",
        "partial_failure",
        "failed",
        "skipped",
    }:
        return f"line {number} has unsupported job_status"

    event_name = event.get("event")
    required_fields = EVENT_REQUIRED_FIELDS.get(event_name, set())
    missing_fields = sorted(field for field in required_fields if field not in event)
    if missing_fields:
        return (
            f"line {number} event {event_name} is missing required fields: "
            f"{', '.join(missing_fields)}"
        )

    if (
        event_name == "matching.offer.expiration.batch.completed"
        and event.get("failure_count", 0) > 0
        and "job_run_id" not in event
    ):
        return f"line {number} partial/failed batch summary is missing job_run_id"

    if event_name in SAFE_EVENTS_WITHOUT_STACK_TRACE and "stack_trace" in event:
        return f"line {number} safe event must not include stack_trace"

    unsafe_keys = sorted(
        key
        for key in event
        if any(fragment in key.lower() for fragment in FORBIDDEN_KEY_FRAGMENTS)
    )
    if unsafe_keys:
        return f"line {number} contains forbidden key(s): {', '.join(unsafe_keys)}"

    return None


def main():
    parser = argparse.ArgumentParser(
        description="Validate SSING dev/prod JSON log lines."
    )
    parser.add_argument("--file", help="JSON Lines file. Reads stdin when omitted.")
    parser.add_argument(
        "--require-event",
        action="append",
        default=[],
        help="Event name that must appear at least once. Repeat for multiple events.",
    )
    args = parser.parse_args()

    valid, message = validate_lines(read_lines(args.file), args.require_event)
    if not valid:
        print(f"[FAIL] {message}")
        return 1

    print(f"[OK] {message}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

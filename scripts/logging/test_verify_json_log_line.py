import json
import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))

from verify_json_log_line import validate_lines


BASE_EVENT = {
    "@timestamp": "2026-07-12T00:00:00Z",
    "level": "INFO",
    "logger_name": "test",
    "thread_name": "main",
    "message": "Test log",
    "service": "ssing-server",
    "env": "dev",
}


class VerifyJsonLogLineTest(unittest.TestCase):

    def test_base_framework_log_is_valid(self):
        valid, message = validate_lines([json.dumps(BASE_EVENT)])

        self.assertTrue(valid, message)

    def test_http_event_requires_request_id(self):
        event = {
            **BASE_EVENT,
            "event": "http.request.completed",
            "method": "GET",
            "path": "/api/v1/test/{testId}",
            "status": 200,
            "duration_ms": 1,
        }

        valid, message = validate_lines([json.dumps(event)])

        self.assertFalse(valid)
        self.assertIn("request_id", message)

    def test_ids_must_be_strings(self):
        event = {**BASE_EVENT, "event": "domain.event.failed", "event_id": 42}

        valid, message = validate_lines([json.dumps(event)])

        self.assertFalse(valid)
        self.assertIn("non-string", message)

    def test_partial_batch_requires_job_run_id(self):
        event = {
            **BASE_EVENT,
            "event": "matching.offer.expiration.batch.completed",
            "job_name": "matching-offer-expiration",
            "job_status": "partial_failure",
            "processed_count": 2,
            "success_count": 1,
            "failure_count": 1,
            "duration_ms": 5,
        }

        valid, message = validate_lines([json.dumps(event)])

        self.assertFalse(valid)
        self.assertIn("job_run_id", message)

    def test_required_representative_event_must_be_present(self):
        valid, message = validate_lines(
            [json.dumps(BASE_EVENT)],
            required_events=["http.request.unhandled_exception"],
        )

        self.assertFalse(valid)
        self.assertIn("missing required event", message)


if __name__ == "__main__":
    unittest.main()

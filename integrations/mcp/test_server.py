"""Adapter boundary tests; real protocol + Java generation is a separate integration test."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("faker_mcp", Path(__file__).with_name("server.py"))
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class AdapterTest(unittest.TestCase):
    def test_generation_requires_authorized_write_before_any_api_access(self):
        with patch.object(server.api, "request") as request:
            with self.assertRaises(server.ToolError):
                server.generate_data(1)
            request.assert_not_called()

    def test_only_local_service_urls_are_accepted(self):
        for url in ("http://example.com", "https://localhost", "http://user:pass@127.0.0.1", "http://localhost?password=x"):
            with self.assertRaises(ValueError):
                server.JavaAPI(url, None)

    def test_scheme_output_excludes_connection_secrets(self):
        row = {"id": 1, "name": "orders", "dataSourceJson": "secret", "tablesJson": "[]"}
        with patch.object(server.api, "request", return_value=row):
            self.assertEqual({"id": 1, "name": "orders", "kind": "RULES", "tables": []}, server.get_scheme(1))

    def test_errors_redact_profile_url_and_password(self):
        with tempfile.TemporaryDirectory() as folder:
            profile = Path(folder) / "profiles.json"
            profile.write_text(json.dumps({"default": {"password": "private-pass", "url": "jdbc:mysql://private/db"}}))
            client = server.JavaAPI("http://127.0.0.1:8085", str(profile))
            self.assertEqual("[REDACTED] [REDACTED]", client.scrub("private-pass jdbc:mysql://private/db"))

    def test_invalid_task_id_cannot_change_request_path(self):
        with self.assertRaises(server.ToolError):
            server.get_task("../scheme/1")

    def test_timed_out_write_is_not_retried(self):
        client = server.JavaAPI("http://127.0.0.1:8085", None)
        with patch.object(client.opener, "open", side_effect=TimeoutError()) as request:
            with self.assertRaisesRegex(server.ToolError, "Do not blindly resubmit"):
                client.request("/publish/task/GEN_DATA", {"tables": []})
            self.assertEqual(request.call_count, 1)


if __name__ == "__main__":
    unittest.main()

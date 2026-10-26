"""Adapter boundary tests; real protocol + Java generation is a separate integration test."""
import importlib.util
import asyncio
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("faker_mcp", Path(__file__).with_name("server.py"))
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class AdapterTest(unittest.TestCase):
    def test_database_inspection_tools_are_discoverable_and_read_only(self):
        tools = {tool.name: tool for tool in asyncio.run(server.mcp.list_tools())}
        for name in ("list_tables", "describe_table", "read_rows", "preview_generation", "verify_batch"):
            self.assertIn(name, tools)
            self.assertTrue(tools[name].annotations.readOnlyHint)
            self.assertFalse(tools[name].annotations.destructiveHint)
            self.assertIsNotNone(tools[name].outputSchema)
        self.assertNotIn("sql", tools["read_rows"].inputSchema["properties"])

    def test_row_reads_use_the_selected_private_profile_and_preserve_values(self):
        fixture = {"type": "MYSQL", "url": "jdbc:mysql://localhost/demo", "username": "reader", "password": "test-secret"}
        where = [{"column": "name", "op": "eq", "value": "O'Reilly"}]
        with patch.object(server.api, "connection", return_value=fixture) as profile, patch.object(
                server.api, "request", return_value={"columns": ["id"], "rows": [["123"]]}) as request:
            result = server.read_rows("customers", ["id"], where=where, connection="readonly")
            profile.assert_called_once_with("readonly")
            self.assertEqual("/db/readRows", request.call_args.args[0])
            body = request.call_args.args[1]
            self.assertEqual(where, body["where"])
            self.assertEqual(20, body["limit"])
            self.assertEqual(fixture, body["dataSource"])
            self.assertNotIn("test-secret", str(result))

    def test_generation_requires_authorized_write_before_any_api_access(self):
        with patch.object(server.api, "request") as request:
            with self.assertRaises(server.ToolError):
                server.generate_data(1)
            request.assert_not_called()

    def test_failed_database_preflight_never_submits_generation(self):
        row = {"id": 1, "kind": "RULES", "tablesJson": "[]"}
        with patch.object(server.api, "connection", return_value={}), patch.object(server.api, "request", return_value=row) as request, patch.object(
                server, "validate_generation"), patch.object(server, "preflight_generation", return_value={"valid": False, "errors": [{"code": "COLUMN_NOT_FOUND"}]}):
            with self.assertRaises(server.ToolError):
                server.generate_data(1, confirm_write=True, request_id="preflight-test-request")
            self.assertFalse(any(call.args[0].startswith("/publish/") for call in request.call_args_list))

    def test_authorized_generation_requires_a_stable_caller_request_id(self):
        with patch.object(server.api, "request") as request:
            with self.assertRaises(server.ToolError):
                server.generate_data(1, confirm_write=True)
            request.assert_not_called()

    def test_cleanup_requires_explicit_authorization_and_cannot_escape_paths(self):
        with patch.object(server.api, "request") as request:
            with self.assertRaises(server.ToolError):
                server.execute_cleanup("batch-id", "plan-id", "a" * 64)
            with self.assertRaises(server.ToolError):
                server.execute_cleanup("../bad", "plan-id", "a" * 64, confirm_delete=True)
            with self.assertRaises(server.ToolError):
                server.get_cleanup("batch-id", "../bad")
            request.assert_not_called()

    def test_only_local_service_urls_are_accepted(self):
        for url in ("http://example.com", "https://localhost", "http://user:pass@127.0.0.1", "http://localhost?password=x"):
            with self.assertRaises(ValueError):
                server.JavaAPI(url, None)

    def test_cleanup_plan_keys_are_paginated_with_no_target_calls(self):
        with patch.object(server.api, "request", side_effect=[{"state": "READY"}, {"rows": [{"origin": "DERIVED"}]}]) as request:
            result = server.get_cleanup("batch-id", "plan-id", include_rows=True, offset=100, limit=50)
            self.assertEqual("DERIVED", result["rowPage"]["rows"][0]["origin"])
            self.assertEqual(["/batches/batch-id/cleanup/plans/plan-id", "/batches/batch-id/cleanup/plans/plan-id/rows?offset=100&limit=50"], [c.args[0] for c in request.call_args_list])
        with patch.object(server.api, "request") as request:
            with self.assertRaises(server.ToolError):
                server.get_cleanup("batch-id", "plan-id", include_rows=True, limit=201)
            request.assert_not_called()

    def test_preview_and_verification_limits_reject_before_api_access(self):
        with patch.object(server.api, "request") as request:
            with self.assertRaises(server.ToolError):
                server.preview_generation([], rows_per_table=5)
            with self.assertRaises(server.ToolError):
                server.preview_generation([{}], rows_per_table=21)
            with self.assertRaises(server.ToolError):
                server.verify_batch("../bad")
            with self.assertRaises(server.ToolError):
                server.verify_batch("valid-batch", checks=[{}] * 21)
            request.assert_not_called()

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

    def test_batch_reads_are_bounded_and_cannot_escape_paths(self):
        with patch.object(server.api, "request") as request:
            for batch,limit in [("../scheme",100),("valid-id",201)]:
                with self.assertRaises(server.ToolError):
                    server.get_batch(batch, include_rows=True, limit=limit)
            request.assert_not_called()

    def test_timed_out_write_is_not_retried(self):
        client = server.JavaAPI("http://127.0.0.1:8085", None)
        with patch.object(client.opener, "open", side_effect=TimeoutError()) as request:
            with self.assertRaisesRegex(server.ToolError, "Do not blindly resubmit"):
                client.request("/publish/task/GEN_DATA", {"tables": []})
            self.assertEqual(request.call_count, 1)


if __name__ == "__main__":
    unittest.main()

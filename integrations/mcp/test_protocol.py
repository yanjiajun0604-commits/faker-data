"""Opt-in real stdio MCP -> Java -> dedicated demo database integration test.

Run mysql-order-schema.sql in an EMPTY test schema first. Set FAKER_MCP_E2E=1,
FAKER_BASE_URL and FAKER_CONNECTIONS_FILE. This inserts demo rows and leaves them.
"""
import asyncio
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest
import uuid
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("orders", ROOT / "examples" / "create-order-schemes.py")
orders = importlib.util.module_from_spec(spec)
spec.loader.exec_module(orders)


@unittest.skipUnless(os.environ.get("FAKER_MCP_E2E") == "1", "opt-in: writes to a dedicated demo schema")
class ProtocolTest(unittest.IsolatedAsyncioTestCase):
    async def test_real_protocol_generation_and_failure(self):
        params = StdioServerParameters(command=sys.executable, args=[str(Path(__file__).with_name("server.py"))],
                                       env={key: os.environ[key] for key in ("FAKER_BASE_URL", "FAKER_CONNECTIONS_FILE")})
        async with stdio_client(params) as (read, write):
            async with ClientSession(read, write) as client:
                await client.initialize()
                tools = (await client.list_tools()).tools
                self.assertEqual({t.name for t in tools}, {"list_tables", "describe_table", "read_rows", "validate_generation", "preflight_generation", "preview_generation", "save_scheme", "get_scheme", "export_scheme", "import_scheme", "generate_data", "get_task", "get_batch", "verify_batch", "get_request", "reconcile_batch", "preview_cleanup", "execute_cleanup", "get_cleanup"})
                self.assertTrue(all(t.outputSchema for t in tools))
                resources = await client.list_resources()
                self.assertIn("faker://rules", [str(r.uri) for r in resources.resources])
                self.assertIn("faker://contracts", [str(r.uri) for r in resources.resources])
                guide = await client.read_resource("faker://rules")
                self.assertIn("REF.OTHER_TABLE", guide.contents[0].text)
                async def call(name, args):
                    result = await client.call_tool(name, args)
                    self.assertFalse(result.isError, str(result.content))
                    self.assertIsNotNone(result.structuredContent)
                    return result.structuredContent
                discovered = await call("list_tables", {"name_contains": "demo_order", "limit": 20})
                self.assertIn("demo_orders", [t["name"].lower() for t in discovered["tables"]])
                metadata = await call("describe_table", {"table": "demo_orders"})
                self.assertTrue(any(c["name"].lower() == "id" and c.get("javaType") for c in metadata["columns"]))
                self.assertTrue(metadata["primaryKeys"])
                self.assertTrue(metadata["foreignKeys"])
                sample = await call("read_rows", {"table": "demo_customers", "columns": ["id", "display_name"],
                                                   "where": [{"column": "enabled", "op": "eq", "value": 1}],
                                                   "order_by": [{"column": "id", "direction": "ASC"}], "limit": 2})
                self.assertEqual(2, sample["rowCount"])
                self.assertTrue((await client.call_tool("read_rows", {"table": "demo_orders; DELETE FROM demo_orders", "columns": ["id"]})).isError)
                recipe = orders.create_recipe("SAVED", 2)
                validation = await call("validate_generation", recipe)
                self.assertEqual(validation["scope"], "CONFIGURATION_ONLY")
                preflight = await call("preflight_generation", recipe)
                self.assertTrue(preflight["valid"], preflight)
                preview = await call("preview_generation", {**recipe, "rows_per_table": 2})
                self.assertTrue(preview["sampleValid"], preview)
                self.assertEqual("GENERATED_SAMPLE_NO_TARGET_WRITES", preview["scope"])
                self.assertTrue((await client.call_tool("validate_generation", {"tables": []})).isError)
                saved = await call("save_scheme", {"name": "MCP protocol demo", **recipe})
                scheme_id = saved["schemeId"]
                loaded = await call("get_scheme", {"scheme_id": scheme_id})
                self.assertEqual(len(loaded["tables"]), 5)
                self.assertNotIn("dataSourceJson", json.dumps(loaded))
                self.assertTrue((await client.call_tool("generate_data", {"scheme_id": scheme_id})).isError)
                for _ in range(2):
                    request_id = "protocol-" + str(uuid.uuid4())
                    args = {"scheme_id": scheme_id, "confirm_write": True, "request_id": request_id}
                    task = await call("generate_data", args)
                    status = await self.wait_task(client, task["taskId"])
                    self.assertEqual(status["state"], "FINISHED", status)
                    self.assertEqual(task["taskId"], (await call("generate_data", args))["taskId"])
                    self.assertEqual(task["taskId"], (await call("get_request", {"request_id": request_id}))["taskId"])
                    batch = await call("get_batch", {"batch_id": task["taskId"], "include_rows": True, "limit": 3})
                    self.assertEqual("COMMITTED", batch["state"])
                    self.assertTrue(batch["trackable"], batch)
                    self.assertEqual(12, batch["rowCount"])
                    self.assertEqual(3, len(batch["rowPage"]["rows"]))
                    verified = await call("verify_batch", {"batch_id": task["taskId"], "checks": [
                        {"kind": "ROW_COUNT", "table": "demo_orders", "expected": 2},
                        {"kind": "VALUE_SET", "table": "demo_orders", "column": "state", "values": ["SAVED"]},
                        {"kind": "REFERENCE", "table": "demo_order_items", "targetTable": "demo_orders", "columns": {"order_id": "id"}, "targetScope": "BATCH"}]})
                    self.assertTrue(verified["valid"], verified)
                    cleanup_connection = os.environ.get("FAKER_MCP_CLEANUP_CONNECTION")
                    if cleanup_connection:
                        plan = await call("preview_cleanup", {"batch_id": task["taskId"], "connection": cleanup_connection})
                        self.assertTrue(plan["eligible"], plan)
                        self.assertEqual(12, plan["rowCount"])
                        args = {"batch_id": task["taskId"], "plan_id": plan["planId"], "plan_hash": plan["planHash"], "connection": cleanup_connection}
                        self.assertTrue((await client.call_tool("execute_cleanup", args)).isError)
                        result = await call("execute_cleanup", {**args, "confirm_delete": True})
                        self.assertEqual("DONE", result["state"])
                        self.assertTrue(result["verifiedAbsent"])
                        self.assertEqual("DONE", (await call("get_cleanup", {"batch_id": task["taskId"], "plan_id": plan["planId"]}))["state"])
                        self.assertEqual("DONE", (await call("execute_cleanup", {**args, "confirm_delete": True}))["state"])
                bad = {"tables": [{"name": "demo_missing_table", "size": 1, "columns": [
                    {"name": "id", "javaType": "java.lang.String", "regs": [{"code": "FIX.UUID"}]}]}]}
                saved = await call("save_scheme", {"name": "MCP expected failure", **bad})
                preflight = await call("preflight_generation", bad)
                self.assertFalse(preflight["valid"])
                self.assertEqual("TABLE_NOT_FOUND", preflight["errors"][0]["code"])
                self.assertTrue((await client.call_tool("generate_data", {"scheme_id": saved["schemeId"], "confirm_write": True, "request_id": "bad-" + str(uuid.uuid4())})).isError)

    async def wait_task(self, client, task_id):
        for _ in range(300):
            result = await client.call_tool("get_task", {"task_id": task_id})
            self.assertFalse(result.isError)
            status = result.structuredContent
            if status["state"] in {"FINISHED", "ERROR", "CANCELED"}:
                return status
            await asyncio.sleep(0.2)
        self.fail("Task completion unknown; do not resubmit")


if __name__ == "__main__":
    unittest.main()

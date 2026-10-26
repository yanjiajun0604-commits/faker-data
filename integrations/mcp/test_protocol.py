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
                self.assertEqual({t.name for t in tools}, {"validate_generation", "save_scheme", "get_scheme", "generate_data", "get_task"})
                self.assertTrue(all(t.outputSchema for t in tools))
                resources = await client.list_resources()
                self.assertIn("faker://rules", [str(r.uri) for r in resources.resources])
                guide = await client.read_resource("faker://rules")
                self.assertIn("REF.OTHER_TABLE", guide.contents[0].text)
                async def call(name, args):
                    result = await client.call_tool(name, args)
                    self.assertFalse(result.isError, str(result.content))
                    self.assertIsNotNone(result.structuredContent)
                    return result.structuredContent
                recipe = orders.create_recipe("SAVED", 2)
                validation = await call("validate_generation", recipe)
                self.assertEqual(validation["scope"], "CONFIGURATION_ONLY")
                self.assertTrue((await client.call_tool("validate_generation", {"tables": []})).isError)
                saved = await call("save_scheme", {"name": "MCP protocol demo", **recipe})
                scheme_id = saved["schemeId"]
                loaded = await call("get_scheme", {"scheme_id": scheme_id})
                self.assertEqual(len(loaded["tables"]), 5)
                self.assertNotIn("dataSourceJson", json.dumps(loaded))
                self.assertTrue((await client.call_tool("generate_data", {"scheme_id": scheme_id})).isError)
                for _ in range(2):
                    task = await call("generate_data", {"scheme_id": scheme_id, "confirm_write": True})
                    status = await self.wait_task(client, task["taskId"])
                    self.assertEqual(status["state"], "FINISHED", status)
                bad = {"tables": [{"name": "demo_missing_table", "size": 1, "columns": [
                    {"name": "id", "javaType": "java.lang.String", "regs": [{"code": "FIX.UUID"}]}]}]}
                saved = await call("save_scheme", {"name": "MCP expected failure", **bad})
                task = await call("generate_data", {"scheme_id": saved["schemeId"], "confirm_write": True})
                status = await self.wait_task(client, task["taskId"])
                self.assertEqual(status["state"], "ERROR")
                self.assertTrue(status.get("errorMsg"))

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

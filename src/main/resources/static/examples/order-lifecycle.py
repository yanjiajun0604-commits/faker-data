"""Public MCP case: ordinary rules -> generate -> verify -> explicit batch cleanup.

Requires the launcher setup, a dedicated schema initialized with
mysql-order-schema.sql, and private connection profiles. No direct SQL writes.
"""
import argparse
import asyncio
import importlib.util
import json
from pathlib import Path
import sys
import time
import uuid

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("orders", ROOT / "examples/create-order-schemes.py")
orders = importlib.util.module_from_spec(spec)
spec.loader.exec_module(orders)


def checks(state, count):
    rules = [{"kind": "ROW_COUNT", "table": table, "expected": count * multiplier}
             for table, multiplier in (("demo_orders", 1), ("demo_order_items", 2),
                                        ("demo_order_contents", 2), ("demo_order_tasks", 1))]
    rules.append({"kind": "VALUE_SET", "table": "demo_orders", "column": "state", "values": [state]})
    for table, column, target, scope in (
        ("demo_orders", "customer_id", "demo_customers", "DATABASE"),
        ("demo_order_items", "order_id", "demo_orders", "BATCH"),
        ("demo_order_contents", "order_id", "demo_orders", "BATCH"),
        ("demo_order_tasks", "order_id", "demo_orders", "BATCH"),
        ("demo_order_tasks", "input_content_id", "demo_order_contents", "BATCH"),
        ("demo_order_tasks", "output_content_id", "demo_order_contents", "BATCH")):
        rules.append({"kind": "REFERENCE", "table": table, "targetTable": target,
                      "columns": {column: "id"}, "targetScope": scope, "allowNull": False})
    return rules


def save(path, receipt):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    temporary.replace(path)


def new_receipt(path, counts, binary, connection):
    path.parent.mkdir(parents=True, exist_ok=True)
    run = "orders-" + uuid.uuid4().hex
    value = {"format": "faker-data.order-lifecycle/1", "runId": run, "connection": connection,
             "binary": binary, "cases": [{"state": state, "count": count,
                 "requestId": run + "-" + state.lower()} for state, count in zip(("SAVED", "SUBMITTED", "APPROVED"), counts)]}
    # A pre-existing receipt must never be replaced by a new generation request.
    with path.open("x", encoding="utf-8") as output:
        json.dump(value, output, indent=2)
    return value


async def execute(args):
    if args.action == "run":
        if not args.confirm_write:
            raise ValueError("run requires --confirm-write for your dedicated test database")
        receipt = new_receipt(args.receipt, args.counts, args.with_binary, args.connection)
    else:
        receipt = json.loads(args.receipt.read_text(encoding="utf-8"))
        if receipt.get("format") != "faker-data.order-lifecycle/1":
            raise ValueError("Unsupported receipt")
        if args.action == "cleanup" and not args.confirm_delete:
            raise ValueError("cleanup requires --confirm-delete for these reviewed demo batches")
    config = [str(ROOT / "faker.py"), "--home", str(args.home.expanduser().resolve()), "mcp"]
    if args.attach:
        config.append("--attach")
    params = StdioServerParameters(command=sys.executable, args=config)
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as client:
            await client.initialize()
            async def call(tool, values):
                response = await client.call_tool(tool, values)
                if response.isError:
                    raise RuntimeError(f"{tool} failed. Inspect the receipt and original request/task; do not blindly rerun generation. Details: {response.content}")
                return response.structuredContent
            # The public example follows the same rules-first MCP workflow as an AI.
            await client.read_resource("faker://rules")
            for case in receipt["cases"]:
                connection = receipt["connection"]
                if args.action == "run":
                    recipe = orders.create_recipe(case["state"], case["count"], receipt["binary"])
                    validation = await call("validate_generation", recipe)
                    if not validation["valid"]:
                        raise RuntimeError("Configuration validation failed")
                    metadata = await call("preflight_generation", {**recipe, "connection": connection})
                    if not metadata["valid"]:
                        raise RuntimeError("Database preflight failed; no generation submitted for this case")
                    scheme = await call("save_scheme", {**recipe, "name": case["requestId"], "connection": connection})
                    case["schemeId"] = scheme["schemeId"]
                    save(args.receipt, receipt)
                    task = await call("generate_data", {"scheme_id": case["schemeId"], "connection": connection,
                                      "request_id": case["requestId"], "confirm_write": True})
                    case["batchId"] = task["batchId"]
                    save(args.receipt, receipt)
                if not case.get("batchId"):
                    original = await call("get_request", {"request_id": case["requestId"]})
                    if not original["found"]:
                        raise RuntimeError("No task found for this case. Resume never creates a new task; inspect this partial receipt.")
                    case["batchId"] = original["taskId"]
                    save(args.receipt, receipt)
                batch = case["batchId"]
                if args.action != "cleanup":
                    deadline = time.monotonic() + 180
                    while True:
                        task = await call("get_task", {"task_id": batch})
                        if task["state"] == "FINISHED":
                            break
                        if task["state"] == "ERROR" or time.monotonic() > deadline:
                            raise RuntimeError("Task not finished; receipt retained. Resume only observes the original request.")
                        await asyncio.sleep(.25)
                    case["verification"] = await call("verify_batch", {"batch_id": batch, "connection": connection,
                                                       "checks": checks(case["state"], case["count"])})
                    save(args.receipt, receipt)
                    if not case["verification"]["valid"]:
                        raise RuntimeError("Batch verification failed; data retained for inspection")
                else:
                    # Persist the exact plan before attempting deletion. A later invocation
                    # only inspects that receipt; it never creates a new plan to bypass UNKNOWN.
                    if case.get("cleanupPlan"):
                        case["cleanup"] = await call("get_cleanup", {"batch_id": batch, "plan_id": case["cleanupPlan"]["planId"]})
                    else:
                        plan = await call("preview_cleanup", {"batch_id": batch, "connection": connection})
                        if not plan["eligible"]:
                            raise RuntimeError("Cleanup blocked; inspect target changes/permissions/references, no deletion attempted")
                        case["cleanupPlan"] = {"planId": plan["planId"], "planHash": plan["planHash"]}
                        save(args.receipt, receipt)
                        case["cleanup"] = await call("execute_cleanup", {"batch_id": batch, "connection": connection,
                                                     **{"plan_id": plan["planId"], "plan_hash": plan["planHash"]}, "confirm_delete": True})
                    save(args.receipt, receipt)
                    if case["cleanup"].get("state") != "DONE" or not case["cleanup"].get("verifiedAbsent"):
                        raise RuntimeError("Cleanup not confirmed DONE; no replay performed. Inspect get_cleanup with original plan")
                print(case["state"], case["count"], batch, "verified" if args.action != "cleanup" else "cleaned")
    print("Receipt:", args.receipt.resolve())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["run", "resume", "cleanup"])
    parser.add_argument("--home", type=Path, default=Path.home() / ".faker-data")
    parser.add_argument("--attach", action="store_true")
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--connection", default="default")
    parser.add_argument("--counts", type=int, nargs=3, default=[50, 20, 100])
    parser.add_argument("--with-binary", action="store_true")
    parser.add_argument("--confirm-write", action="store_true")
    parser.add_argument("--confirm-delete", action="store_true")
    args = parser.parse_args()
    if any(count < 1 or count > 1000 for count in args.counts):
        parser.error("Each example count must be 1..1000 (verification supports at most 10000 rows per batch)")
    try:
        asyncio.run(execute(args))
    except (ValueError, RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())

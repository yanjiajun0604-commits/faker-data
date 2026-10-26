"""Emit portable ordinary-rule recipes; no DB connection or writes. Python 3 stdlib."""
import argparse
import json
from pathlib import Path


def rule(code, **kwargs):
    return {"code": code, **kwargs}


def literal(value):
    return rule("RES.INPUTAREA", inputs=[{"value": str(value), "weight": 1}])


def column(name, java_type="java.lang.String", regs=None):
    return {"name": name, "javaType": java_type, "nullable": 1, "regs": [] if regs is None else regs}


def ref(table, name):
    return rule("REF.OTHER_TABLE", refOtherTableColumn=[table, name])


def create_recipe(state="SAVED", count=50, binary=False):
    if state not in {"SAVED", "SUBMITTED", "APPROVED"} or not isinstance(count, int) or count < 0:
        raise ValueError("Choose a supported state and a non-negative order count")
    def uuid():
        return column("id", regs=[rule("FIX.UUID")])
    def child(name, rows, columns):
        return {"name": name, "size": 0, "parentRows": {"table": "demo_orders", "rowsPerParent": rows}, "columns": columns}
    def content_reference(role):
        return rule("REF.OTHER_TABLE", refOtherTableColumn=["demo_order_contents", "id"],
                    refMatch={"order_id": {"field": "order_id"}, "role": {"value": role}})
    payload = [rule("EXT.FIELD", extension="orders-demo-codec", extensionParameters={"sourceField": "order_id", "binaryEncoding": "base64"})] if binary else []
    tables = [
        {"name": "demo_customers", "existingData": {"where": {"enabled": 1}, "orderBy": ["id"], "maxRows": 10000},
         "columns": [column("id"), column("display_name")]},
        {"name": "demo_orders", "size": count, "columns": [uuid(),
         column("customer_id", regs=[ref("demo_customers", "id")]),
         column("customer_name", regs=[ref("demo_customers", "display_name")]),
         column("state", regs=[literal(state)]),
         column("created_at", "java.sql.Timestamp", [rule("REG.DATE", dateRange=[1735689600000, 1767225599000])])]},
        child("demo_order_items", 2, [uuid(), column("order_id", regs=[ref("demo_orders", "id")]),
              column("quantity", "java.lang.Integer", [rule("REG.RANGE_NUM", minSize=1, maxSize=10)]),
              column("unit_price", "java.math.BigDecimal", [rule("REG.RANGE_NUM", minSize=10, maxSize=500)])]),
        child("demo_order_contents", 2, [uuid(), column("order_id", regs=[ref("demo_orders", "id")]),
              column("role", regs=[rule("COND.EXPR", condition="#__rowIndex % 2 == 0", onTrue=[literal("INPUT")], onFalse=[literal("OUTPUT")])]),
              column("payload", "byte[]", payload)]),
        child("demo_order_tasks", 1, [uuid(), column("input_content_id", regs=[content_reference("INPUT")]),
              column("output_content_id", regs=[content_reference("OUTPUT")]), column("order_id", regs=[ref("demo_orders", "id")])])
    ]
    return {"tables": tables}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("order-schemes"))
    parser.add_argument("--counts", nargs=3, type=int, default=[50, 20, 100], metavar=("SAVED", "SUBMITTED", "APPROVED"))
    parser.add_argument("--with-binary", action="store_true")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for state, count in zip(("SAVED", "SUBMITTED", "APPROVED"), args.counts):
        (args.output / (state.lower() + ".json")).write_text(json.dumps(create_recipe(state, count, args.with_binary), indent=2), encoding="utf-8")
    print("Created three ordinary recipes in", args.output)

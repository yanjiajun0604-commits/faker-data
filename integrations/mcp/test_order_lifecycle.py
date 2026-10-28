import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("lifecycle", Path(__file__).resolve().parents[2] / "examples/order-lifecycle.py")
example = importlib.util.module_from_spec(spec)
spec.loader.exec_module(example)


class OrderLifecycleTest(unittest.TestCase):
    def test_receipt_keeps_three_stable_request_ids_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "receipt.json"
            result = example.new_receipt(file, [50, 20, 100], True, "demo")
            self.assertEqual([50, 20, 100], [row["count"] for row in result["cases"]])
            self.assertEqual(3, len({row["requestId"] for row in result["cases"]}))
            with self.assertRaises(FileExistsError):
                example.new_receipt(file, [1, 1, 1], False, "demo")
            self.assertEqual(result, json.loads(file.read_text()))
            self.assertNotIn("password", json.dumps(result))

    def test_each_case_checks_real_batch_counts_states_and_complete_references(self):
        checks = example.checks("APPROVED", 100)
        self.assertEqual([100, 200, 200, 100], [check["expected"] for check in checks if check["kind"] == "ROW_COUNT"])
        refs = [check for check in checks if check["kind"] == "REFERENCE"]
        self.assertEqual(6, len(refs))
        self.assertEqual(1, sum(check["targetScope"] == "DATABASE" for check in refs))
        self.assertTrue(all(not check["allowNull"] for check in refs))

    def test_ordinary_fields_do_not_use_extensions_and_name_matches_chosen_customer(self):
        recipe = example.orders.create_recipe("SAVED", 1, True)
        extension_fields = [(table["name"], col["name"]) for table in recipe["tables"] for col in table["columns"]
                            if any(reg["code"] == "EXT.FIELD" for reg in col["regs"])]
        self.assertEqual([("demo_order_contents", "payload")], extension_fields)
        name = next(col for col in recipe["tables"][1]["columns"] if col["name"] == "customer_name")
        self.assertEqual({"id": {"field": "customer_id"}}, name["regs"][0]["refMatch"])


if __name__ == "__main__":
    unittest.main()

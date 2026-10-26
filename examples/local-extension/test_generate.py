"""Portable binary demo contracts; no database or external service needed."""
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("demo_codec", Path(__file__).with_name("generate.py"))
codec = importlib.util.module_from_spec(spec)
spec.loader.exec_module(codec)


class DemoCodecTest(unittest.TestCase):
    def generate(self, folder, table, row, parameters):
        input_path, output_path = Path(folder) / "input.json", Path(folder) / "output.json"
        context = {"protocol": "faker-field-extension/1", "tables": {table: [row]},
                   "fields": [{"table": table, "rowIndex": 0, "column": "payload", "parameters": parameters}]}
        input_path.write_text(json.dumps(context), encoding="utf-8")
        codec.generate(input_path, output_path)
        return json.loads(output_path.read_text(encoding="utf-8"))["values"][0]["value"]

    def test_legacy_document_demo_preserves_file_payload_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            value = self.generate(folder, "demo_extension_payload", {"document_id": "doc-1"}, {})
            expected = b'FAKER-DEMO\x00\x01{"documentId": "doc-1"}'
            self.assertEqual((Path(folder) / value["$binary"]).read_bytes(), expected)
            self.assertEqual(value["sha256"], hashlib.sha256(expected).hexdigest())

    def test_inline_order_demo_binds_to_generated_id_and_role_without_binary_files(self):
        with tempfile.TemporaryDirectory() as folder:
            value = self.generate(folder, "demo_order_contents", {"order_id": "order-2", "role": "INPUT"},
                                  {"sourceField": "order_id", "binaryEncoding": "base64"})
            data = base64.b64decode(value["$base64"])
            self.assertIn(b'"documentId": "order-2"', data)
            self.assertIn(b'"role": "INPUT"', data)
            self.assertEqual(value["sha256"], hashlib.sha256(data).hexdigest())
            self.assertEqual(list(Path(folder).glob("*.bin")), [])

    def test_unexpected_fields_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, "Unexpected demo field"):
                self.generate(folder, "ordinary_business_table", {"id": "x"}, {})


if __name__ == "__main__":
    unittest.main()

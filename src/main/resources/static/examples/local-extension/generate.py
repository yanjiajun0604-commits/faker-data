"""Field-only extension demo, Python 3 standard library. No SQL or HTTP calls."""
from pathlib import Path
import hashlib
import base64
import json
import sys


def generate(input_path, output_path):
    context = json.loads(Path(input_path).read_text(encoding="utf-8"))
    if context.get("protocol") != "faker-field-extension/1":
        raise ValueError("Unsupported field extension protocol")
    output_path = Path(output_path)
    values = []
    for index, field in enumerate(context["fields"]):
        if field["table"] not in {"demo_extension_payload", "demo_order_contents"} or field["column"] != "payload":
            raise ValueError("Unexpected demo field")
        ordinary = context["tables"][field["table"]][field["rowIndex"]]
        source_field = field.get("parameters", {}).get("sourceField", "document_id")
        document_id = ordinary[source_field]
        body = {"documentId": document_id}
        if "role" in ordinary:
            body["role"] = ordinary["role"]
        content = b"FAKER-DEMO\x00\x01" + json.dumps(body).encode("utf-8")
        encoding = field.get("parameters", {}).get("binaryEncoding", "file")
        if encoding == "base64":
            encoded = {"$base64": base64.b64encode(content).decode("ascii")}
        elif encoding == "file":
            filename = "payload-" + str(index) + ".bin"
            (output_path.parent / filename).write_bytes(content)
            encoded = {"$binary": filename}
        else:
            raise ValueError("Unknown binaryEncoding")
        encoded["sha256"] = hashlib.sha256(content).hexdigest()
        values.append({"table": field["table"], "rowIndex": field["rowIndex"], "column": field["column"],
                       "value": encoded})
    output_path.write_text(json.dumps({"values": values}), encoding="utf-8")


if __name__ == "__main__":
    generate(sys.argv[1], sys.argv[2])

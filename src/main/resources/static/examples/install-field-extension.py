"""Install a public field demo into a chosen local root. Does not enable or run it."""
import argparse
import json
from pathlib import Path
import shutil
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--root", type=Path, required=True, help="Same directory as faker.extensions.root")
parser.add_argument("--demo", choices=["documents", "orders"], default="orders")
args = parser.parse_args()
name, table = ("orders-demo-codec", "demo_order_contents") if args.demo == "orders" else ("demo-field-codec", "demo_extension_payload")
folder = args.root.resolve() / name
folder.mkdir(parents=True, exist_ok=True)
script = folder / "generate.py"
if script.exists() or (folder / "extension.json").exists():
    raise SystemExit("Extension already exists; choose another root or inspect it before replacing.")
shutil.copyfile(Path(__file__).parent / "local-extension" / "generate.py", script)
manifest = {"protocol": "faker-field-extension/1", "command": [sys.executable, str(script), "${input}", "${output}"],
            "allowedFields": {table: ["payload"]}, "timeoutSeconds": 60}
(folder / "extension.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print("Installed", name, "in", folder, "using Python", sys.version.split()[0])

"""Launcher behavior: explicit ownership, portable config and actionable checks."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("bootstrap", ROOT / "faker.py")
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class BootstrapTest(unittest.TestCase):
    def test_old_or_unrelated_jar_rejected_before_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "old.jar"
            with zipfile.ZipFile(jar, "w") as output:
                output.writestr("META-INF/MANIFEST.MF", "Implementation-Version: 0.1.1\n")
            with self.assertRaisesRegex(bootstrap.LaunchError, "contracts"):
                bootstrap.jar_info(jar)

    def test_current_jar_reports_version_and_checks_contracts(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "new.jar"
            with zipfile.ZipFile(jar, "w") as output:
                output.writestr("META-INF/MANIFEST.MF", "Implementation-Version: 0.1.1\n")
                output.writestr("BOOT-INF/classes/contracts/manifest-v1.json",
                                (ROOT / "contracts/manifest-v1.json").read_text(encoding="utf-8"))
            self.assertEqual("0.1.1", bootstrap.jar_info(jar)["version"])

    def test_config_only_contains_local_paths_not_profile_contents(self):
        settings = {"port": 18988, "connections": "C:/private/connections.json"}
        output = bootstrap.client_config(Path("C:/private folder"), attach=True)
        server = output["mcpServers"]["faker-data"]
        self.assertIn("--attach", server["args"])
        self.assertIn("mcp", server["args"])
        self.assertNotIn("password", json.dumps(output))
        self.assertNotIn("env", server)

    def test_creating_private_files_never_overwrites_existing_values(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "connections.json"
            bootstrap.create_private(path, {"default": "original"})
            bootstrap.create_private(path, {"default": "replacement"})
            self.assertEqual({"default": "original"}, json.loads(path.read_text()))

    def test_occupied_port_refused_without_adopting_or_killing_process(self):
        with patch.object(bootstrap, "port_busy", return_value=True):
            with self.assertRaisesRegex(bootstrap.LaunchError, "--attach"):
                bootstrap.require_free_port(8085)

    def test_child_shutdown_only_targets_owned_process_and_escalates_timeout(self):
        class Owned:
            def __init__(self): self.calls = []
            def poll(self): return None
            def terminate(self): self.calls.append("terminate")
            def wait(self, timeout):
                self.calls.append("wait")
                if self.calls.count("wait") == 1: raise subprocess.TimeoutExpired("owned", timeout)
            def kill(self): self.calls.append("kill")
        child = Owned()
        bootstrap.stop_owned(child)
        self.assertEqual(["terminate", "wait", "kill", "wait"], child.calls)
        bootstrap.stop_owned(None)

    def test_import_has_no_sdk_warning_with_warnings_as_errors(self):
        result = subprocess.run([sys.executable, "-W", "error", "-c",
                                 "import sys; sys.path.insert(0, sys.argv[1]); import server",
                                 str(ROOT / "integrations/mcp")], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()

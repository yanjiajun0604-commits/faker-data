#!/usr/bin/env python3
"""Standard-library client for the local faker-data API; no automatic submit retries."""
import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request


class FakerClient:
    def __init__(self, base_url="http://127.0.0.1:8085", request_timeout=15):
        self.base_url = base_url.rstrip("/") + "/"
        self.request_timeout = request_timeout
        # Local API traffic should not be sent to an environment HTTP proxy.
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(self, method, path, payload=None):
        body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.base_url + path.lstrip("/"), body,
                                         {"Content-Type": "application/json"}, method=method)
        try:
            with self.opener.open(request, timeout=self.request_timeout) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError("HTTP %s for %s" % (error.code, path)) from error
        if not isinstance(result, dict) or result.get("code") != 0:
            raise RuntimeError(result.get("msg", "Invalid API response") if isinstance(result, dict) else "Invalid API response")
        return result.get("data")

    def check(self):
        return self.request("GET", "asyncTask/list")

    def connect(self, data_source):
        self.request("POST", "db/connect/test", data_source)

    def tables(self, data_source, with_columns=False):
        result = self.request("POST", "db/showTables?withSub=" + ("1" if with_columns else "0"), data_source)
        if result is None:
            raise RuntimeError("Metadata unavailable; check the target database connection")
        return result

    def columns(self, data_source, table):
        result = self.request("POST", "db/showColumns/" + urllib.parse.quote(table, safe=""), data_source)
        if result is None:
            raise RuntimeError("Column metadata unavailable")
        return result

    def submit(self, generation_request, request_id=None):
        payload = dict(generation_request)
        if request_id is not None:
            payload["requestId"] = request_id
        if not payload.get("requestId"):
            raise ValueError("Choose and retain requestId before submitting; reuse it for the same intended request")
        return self.request("POST", "publish/task/GEN_DATA", payload)

    def request_status(self, request_id):
        return self.request("GET", "generation/requests/" + urllib.parse.quote(request_id, safe=""))

    def status(self, task_id):
        return self.request("GET", "asyncTask/queryProcess/" + urllib.parse.quote(task_id, safe=""))

    def wait(self, task_id, timeout=300, interval=1):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            info = self.status(task_id)
            state = info.get("state")
            if state == "FINISHED":
                if info.get("progress", 0) != 100:
                    raise RuntimeError("Inconsistent FINISHED response")
                return info
            if state in ("ERROR", "CANCELED", "OVERTIME", "OUTOFQUEUE", "NONE"):
                raise RuntimeError(info.get("errorMsg") or info.get("result") or state)
            if state not in ("WAITING", "PROCESSING", "CANCELING"):
                raise RuntimeError("Unknown task state: " + str(state))
            time.sleep(interval)
        raise TimeoutError("Task %s is not confirmed finished; continue querying this ID, do not resubmit" % task_id)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8085")
    parser.add_argument("--request", required=True, help="JSON containing dataSource and tables")
    parser.add_argument("--submit", action="store_true", help="Actually submit one generation task")
    parser.add_argument("--request-id", help="Stable ID for this intended generation; retain it after timeouts/restarts")
    parser.add_argument("--timeout", type=float, default=300)
    args = parser.parse_args()
    with open(args.request, encoding="utf-8") as file:
        payload = json.load(file)
    source = payload["dataSource"]
    if "FAKER_TARGET_PASSWORD" in os.environ:
        source["password"] = os.environ["FAKER_TARGET_PASSWORD"]
    if not source.get("password"):
        parser.error("Provide target credentials through FAKER_TARGET_PASSWORD or the private request file")
    client = FakerClient(args.base_url)
    client.check()
    client.connect(source)
    if not args.submit:
        print(json.dumps(client.tables(source, with_columns=True), ensure_ascii=False, indent=2))
        return
    if not (args.request_id or payload.get("requestId")):
        parser.error("--submit requires --request-id or requestId in the private JSON; reuse it for the same intended generation")
    task_id = client.submit(payload, args.request_id)
    print(json.dumps({"taskId": task_id}), flush=True)
    print(json.dumps(client.wait(task_id, timeout=args.timeout), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

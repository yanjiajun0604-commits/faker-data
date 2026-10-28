"""Independent schema checks; uses jsonschema already required by the MCP SDK."""
import importlib.util
import json
from pathlib import Path
import unittest
import ast
import jsonschema

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent


def load_recipe(relative, name):
    spec=importlib.util.spec_from_file_location(name, ROOT/relative)
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ContractTest(unittest.TestCase):
    def envelope(self, tables):
        return {"format":"faker-data.scheme","formatVersion":1,"name":"Public fixture","connectionRef":"local","tables":tables,
                "requiredLocalValues":[],"requiredExtensions":[],"trustedCodeRequired":False}

    def test_all_published_schemas_are_valid_draft7(self):
        for path in HERE.glob("*.schema.json"):
            with self.subTest(path=path.name):
                jsonschema.Draft7Validator.check_schema(json.loads(path.read_text(encoding="utf-8")))

    def test_public_order_and_virtual_examples_match_portable_shape(self):
        schema=json.loads((HERE/"scheme-v1.schema.json").read_text(encoding="utf-8"))
        orders=load_recipe("examples/create-order-schemes.py","contract_orders")
        virtual=load_recipe("examples/virtual-fields/create_recipe.py","contract_virtual")
        for tables in [orders.create_recipe("SAVED",2)["tables"],orders.create_recipe("APPROVED",2,binary=True)["tables"],virtual.create_recipe()["tables"]]:
            jsonschema.validate(self.envelope(tables),schema)

    def test_future_versions_raw_connections_unknown_fields_and_bad_types_fail(self):
        schema=json.loads((HERE/"scheme-v1.schema.json").read_text(encoding="utf-8"))
        base=self.envelope([{"name":"rows","size":1,"columns":[{"name":"id","javaType":"java.lang.String","regs":[{"code":"FIX.UUID"}]}]}])
        cases=[]
        for key,value in [("formatVersion",2),("dataSource",{"password":"not-portable"})]:
            document=json.loads(json.dumps(base));document[key]=value;cases.append(document)
        document=json.loads(json.dumps(base));document["tables"][0]["sql"]="SELECT 1";cases.append(document)
        document=json.loads(json.dumps(base));document["tables"][0]["columns"][0]["virtual"]="true";cases.append(document)
        for document in cases:
            with self.subTest(document=document):
                with self.assertRaises(jsonschema.ValidationError):
                    jsonschema.validate(document,schema)

    def test_redacted_placeholders_and_current_field_protocol_shapes(self):
        schema=json.loads((HERE/"scheme-v1.schema.json").read_text(encoding="utf-8"))
        document=self.envelope([{"name":"rows","size":1,"columns":[{"name":"password","javaType":"java.lang.String","regs":None}]}])
        document["requiredLocalValues"]=[{"pointer":"/tables/0/columns/0/regs","reason":"CREDENTIAL_COLUMN"}]
        jsonschema.validate(document,schema)
        request={"protocol":"faker-field-extension/1","taskId":"fixture","fields":[{"table":"rows","rowIndex":0,"column":"payload","parameters":{}}],"tables":{"rows":[{"id":"generated","payload":None}]}}
        jsonschema.validate(request,json.loads((HERE/"field-extension-request-v1.schema.json").read_text(encoding="utf-8")))
        response={"values":[{"table":"rows","rowIndex":0,"column":"payload","value":{"$base64":"","sha256":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}}]}
        jsonschema.validate(response,json.loads((HERE/"field-extension-response-v1.schema.json").read_text(encoding="utf-8")))

    def test_rule_catalog_matches_adapter_public_code_metadata(self):
        catalog=json.loads((HERE/"rules-v1.json").read_text(encoding="utf-8"))
        tree=ast.parse((ROOT/"integrations/mcp/server.py").read_text(encoding="utf-8"))
        declared=next(ast.literal_eval(node.value) for node in tree.body if isinstance(node,ast.Assign) and any(isinstance(target,ast.Name) and target.id=="PUBLIC_RULE_CODES" for target in node.targets))
        self.assertEqual({r["code"] for r in catalog["rules"]},declared)


if __name__=="__main__":unittest.main()

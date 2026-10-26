"""Emit ordinary rules with task-local markers. No database access or credentials."""
import argparse
import json


def rule(code, **parameters):
    return {"code": code, **parameters}


def constant(value):
    return rule("RES.INPUTAREA", inputs=[{"value": str(value), "weight": 1}])


def column(name, *rules, virtual=False):
    return {"name": name, "javaType": "java.lang.String", "virtual": virtual, "regs": list(rules)}


def reference(table, field):
    return rule("REF.OTHER_TABLE", refOtherTableColumn=[table, field])


def create_recipe(count=3, selected=2):
    if not isinstance(count, int) or not 1 <= count <= 10000 or not isinstance(selected, int) or not 0 <= selected <= count:
        raise ValueError("count must be 1-10000 and selected 0-count")
    documents = {"name": "demo_virtual_documents", "size": count, "columns": [
        column("id", rule("FIX.UUID")),
        column("selected_marker", rule("COND.EXPR", condition=f"#__rowIndex < {selected}",
                                      onTrue=[constant("YES")], onFalse=[constant("NO")]), virtual=True)]}
    contents = {"name": "demo_virtual_contents", "size": 0,
                "parentRows": {"table": "demo_virtual_documents", "rowsPerParent": 2}, "columns": [
        column("owner_marker", reference("demo_virtual_documents", "id"), virtual=True),
        column("id", rule("REF.SELF_COLUMN", refSelfColumn="owner_marker"), constant("-"),
               rule("REF.SELF_COLUMN", refSelfColumn="role_marker")),
        column("role_marker", rule("COND.EXPR", condition="#__rowIndex % 2 == 0",
                                  onTrue=[constant("INPUT")], onFalse=[constant("OUTPUT")]), virtual=True)]}

    def matched(role):
        return rule("REF.OTHER_TABLE", refOtherTableColumn=["demo_virtual_contents", "id"],
                    refMatch={"owner_marker": {"field": "document_id"}, "role_marker": {"value": role}})

    tasks = {"name": "demo_virtual_tasks", "size": 0,
             "parentRows": {"table": "demo_virtual_documents", "rowsPerParent": 1, "condition": "#selected_marker == 'YES'"},
             "columns": [column("id", rule("FIX.UUID")), column("input_id", matched("INPUT")),
                         column("output_id", matched("OUTPUT")), column("document_id", reference("demo_virtual_documents", "id"))]}
    return {"tables": [tasks, contents, documents]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=3)
    parser.add_argument("--selected", type=int, default=2)
    options = parser.parse_args()
    print(json.dumps(create_recipe(options.count, options.selected), ensure_ascii=False, indent=2))

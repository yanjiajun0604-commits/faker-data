"""Pure configuration checks for the optional public workflow case; no engine/DB."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('flowable_recipe', ROOT / 'examples/flowable-approval/create_recipe.py')
recipe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recipe)


def reference():
    return {'SUBMITTED': {'processId': '10', 'idTokens': ['10', '11', '12', '13'], 'tables': {
        'ACT_RU_EXECUTION': {'columns': [{'name': 'ID_', 'javaType': 'java.lang.String'},
            {'name': 'PARENT_ID_', 'javaType': 'java.lang.String'},
            {'name': 'BUSINESS_KEY_', 'javaType': 'java.lang.String'}],
            'rows': [{'ID_': '11', 'PARENT_ID_': '10', 'BUSINESS_KEY_': None},
                     {'ID_': '10', 'PARENT_ID_': None, 'BUSINESS_KEY_': 'template-document'}]},
        'ACT_GE_BYTEARRAY': {'columns': [{'name': 'ID_', 'javaType': 'java.lang.String'},
            {'name': 'BYTES_', 'javaType': '[B'}], 'rows': [{'ID_': '12', 'BYTES_': 'reference bytes'}]},
        'ACT_RU_VARIABLE': {'columns': [{'name': 'ID_', 'javaType': 'java.lang.String'},
            {'name': 'EXECUTION_ID_', 'javaType': 'java.lang.String'},
            {'name': 'BYTEARRAY_ID_', 'javaType': 'java.lang.String'}],
            'rows': [{'ID_': '13', 'EXECUTION_ID_': '11', 'BYTEARRAY_ID_': '12'}],
            'foreignKeys': [{'table': 'ACT_GE_BYTEARRAY', 'column': 'BYTEARRAY_ID_'},
                            {'table': 'ACT_RU_EXECUTION', 'column': 'EXECUTION_ID_'}]}}}}


class FlowableRecipeTest(unittest.TestCase):
    def test_saved_has_only_ordinary_master_and_detail_fields(self):
        tables = recipe.create_recipe({}, 'SAVED', 50)['tables']
        self.assertEqual(['FD_DOCUMENTS', 'FD_LINES'], [table['name'] for table in tables])
        self.assertEqual(50, tables[0]['size'])
        self.assertEqual(2, tables[1]['parentRows']['rowsPerParent'])
        self.assertFalse(next(col for col in tables[0]['columns'] if col['name'] == 'PROCESS_ID')['regs'])

    def test_engine_ids_are_new_ordinary_virtual_uuid_fields(self):
        tables = recipe.create_recipe(reference(), 'SUBMITTED', 20)['tables']
        virtual = [col for col in tables[0]['columns'] if col['virtual']]
        self.assertEqual(4, len(virtual))
        self.assertTrue(all(col['regs'][0]['code'] == 'FIX.UUID' for col in virtual))
        process = next(col for col in tables[0]['columns'] if col['name'] == 'PROCESS_ID')
        self.assertEqual('REF.SELF_COLUMN', process['regs'][0]['code'])

    def test_only_serialized_bytes_use_an_extension(self):
        tables = recipe.create_recipe(reference(), 'SUBMITTED', 1)['tables']
        extension_fields = [(t['name'], c['name']) for t in tables for c in t['columns']
                            if any(r['code'] == 'EXT.FIELD' for r in c['regs'])]
        self.assertEqual([('ACT_GE_BYTEARRAY', 'BYTES_')], extension_fields)
        binary = next(t for t in tables if t['name'] == 'ACT_GE_BYTEARRAY')
        self.assertTrue(next(c for c in binary['columns'] if c['name'] == 'fixture_document')['virtual'])

    def test_physical_fk_dependencies_are_explicit_virtual_rules(self):
        tables = recipe.create_recipe(reference(), 'SUBMITTED', 1)['tables']
        variable = next(t for t in tables if t['name'] == 'ACT_RU_VARIABLE')
        dependencies = [c for c in variable['columns'] if c['name'].startswith('fixture_dependency_')]
        self.assertEqual({'ACT_GE_BYTEARRAY', 'ACT_RU_EXECUTION'},
                         {c['regs'][0]['refOtherTableColumn'][0] for c in dependencies})
        self.assertTrue(all(c['virtual'] for c in dependencies))

    def test_tokens_and_counts_are_bounded(self):
        for state, count in [('INVALID', 1), ('SAVED', 0), ('SAVED', True)]:
            with self.assertRaises(ValueError): recipe.create_recipe({}, state, count)
        source = reference()
        source['SUBMITTED']['idTokens'] = [str(i) for i in range(46)]
        with self.assertRaises(ValueError): recipe.create_recipe(source, 'SUBMITTED', 1)


if __name__ == '__main__':
    unittest.main()

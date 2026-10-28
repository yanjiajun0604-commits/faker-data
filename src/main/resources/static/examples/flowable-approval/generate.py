"""Generate public Flowable documents using MCP rules, not engine start APIs.

Use the isolated MCP Python environment from faker.py setup. Reference templates
and codec installation are explicit prerequisites; see README.md.
"""
import argparse
import asyncio
import json
from pathlib import Path
import sys
import time
import uuid

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from create_recipe import create_recipe

ROOT = Path(__file__).resolve().parents[2]


def persist(path, receipt):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    temporary.replace(path)


async def generate(args):
    templates = json.loads(args.templates.read_text(encoding='utf-8'))
    if args.resume:
        receipt = json.loads(args.receipt.read_text(encoding='utf-8'))
        if receipt.get('format') != 'faker-data.flowable-generation/1':
            raise ValueError('Unsupported receipt')
    else:
        if not args.confirm_write:
            raise ValueError('Generation requires --confirm-write for your dedicated public demo database')
        run = 'flowable-' + uuid.uuid4().hex
        receipt = {'format': 'faker-data.flowable-generation/1', 'cases': [
            {'state': state, 'count': count, 'requestId': run + '-' + state.lower()}
            for state, count in zip(('SAVED', 'SUBMITTED', 'APPROVED'), args.counts)]}
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        with args.receipt.open('x', encoding='utf-8') as file:
            json.dump(receipt, file, indent=2)
    params = StdioServerParameters(command=sys.executable, args=[str(ROOT / 'faker.py'), '--home',
                                    str(args.home.expanduser().resolve()), 'mcp', *(['--attach'] if args.attach else [])])
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as client:
            await client.initialize()
            async def call(name, arguments):
                result = await client.call_tool(name, arguments)
                if result.isError:
                    raise RuntimeError(f'{name} failed; inspect original request/batch in receipt before doing anything else: {result.content}')
                return result.structuredContent
            await client.read_resource('faker://rules')
            if not args.resume:
                sample = await call('read_rows', {'table': 'FD_DOCUMENTS', 'columns': ['ID'], 'limit': 1})
                if sample['rowCount']:
                    raise ValueError('Use an empty public demo document set; existing documents were preserved')
            for case in receipt['cases']:
                if not args.resume:
                    recipe = create_recipe(templates, case['state'], case['count'])
                    checked = await call('validate_generation', recipe)
                    if not checked['valid']: raise ValueError('Invalid recipe')
                    checked = await call('preflight_generation', recipe)
                    if not checked['valid']: raise ValueError('Database preflight failed; inspect metadata/configuration')
                    scheme = await call('save_scheme', {**recipe, 'name': case['requestId']})
                    case['schemeId'] = scheme['schemeId']
                    persist(args.receipt, receipt)
                    result = await call('generate_data', {'scheme_id': case['schemeId'], 'request_id': case['requestId'], 'confirm_write': True})
                    case['batchId'] = result['batchId']
                    persist(args.receipt, receipt)
                elif not case.get('batchId'):
                    original = await call('get_request', {'request_id': case['requestId']})
                    if not original['found']:
                        raise ValueError('No original task for this case; resume never submits missing tasks')
                    case['batchId'] = original['taskId']
                    persist(args.receipt, receipt)
                deadline = time.monotonic() + 180
                while True:
                    task = await call('get_task', {'task_id': case['batchId']})
                    if task['state'] == 'FINISHED': break
                    if task['state'] == 'ERROR' or time.monotonic() >= deadline:
                        raise ValueError('Original task did not finish; receipt retained, never blindly rerun')
                    await asyncio.sleep(.25)
                checks = [{'kind': 'ROW_COUNT', 'table': 'FD_DOCUMENTS', 'expected': case['count']},
                          {'kind': 'ROW_COUNT', 'table': 'FD_LINES', 'expected': 2 * case['count']},
                          {'kind': 'VALUE_SET', 'table': 'FD_DOCUMENTS', 'column': 'STATE', 'values': [case['state']]}]
                verified = await call('verify_batch', {'batch_id': case['batchId'], 'checks': checks})
                case['verification'] = verified
                persist(args.receipt, receipt)
                if not verified['valid']: raise ValueError('Snapshot or declared invariant failed; data retained')
                print(case['state'], case['count'], case['batchId'], 'verified')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--templates', type=Path, required=True)
    parser.add_argument('--home', type=Path, default=Path.home() / '.faker-data')
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--counts', type=int, nargs=3, default=[1, 1, 1])
    parser.add_argument('--confirm-write', action='store_true')
    parser.add_argument('--resume', action='store_true', help='observe original requests only; never create or replay generation')
    parser.add_argument('--attach', action='store_true')
    args = parser.parse_args()
    if any(count < 1 or count > 200 for count in args.counts): parser.error('Each public example count must be 1..200')
    try:
        asyncio.run(generate(args))
        return 0
    except (ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())

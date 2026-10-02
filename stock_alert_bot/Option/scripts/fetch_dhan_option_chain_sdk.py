import os, traceback

try:
    token = open('data/dhan_access_token.txt').read().strip()
except Exception as e:
    print('Token file missing:', e)
    raise SystemExit(1)

import dhanhq
print('dhanhq members snapshot OK')

try:
    client = dhanhq.dhanhq(os.environ.get('DHAN_CLIENT_ID','1100062523'), token, disable_ssl=True)
    print('Client created')
except Exception as e:
    print('Client creation failed:', e)
    traceback.print_exc()
    raise SystemExit(1)

symbol = 'NIFTY'
segment = 'NSE_FNO'

# Query available expiries for this symbol+segment
expiries = None
import traceback
try:
    expiries = client.expiry_list(symbol, segment)
    print('client.expiry_list(symbol, segment) returned:', type(expiries))
    try:
        print('sample:', expiries[:5])
    except Exception:
        print('cannot slice/sample; repr:', repr(expiries))
except Exception as e:
    print('client.expiry_list(symbol, segment) raised:', repr(e))
    traceback.print_exc()
    # try reversed args
    try:
        expiries = client.expiry_list(segment, symbol)
        print('client.expiry_list(segment, symbol) returned:', type(expiries))
        try:
            print('sample:', expiries[:5])
        except Exception:
            print('cannot slice/sample; repr:', repr(expiries))
    except Exception as e2:
        print('client.expiry_list(segment, symbol) raised:', repr(e2))
        traceback.print_exc()

if not expiries:
    print('No expiries available; aborting')
    raise SystemExit(1)

expiry = expiries[0]
print('Using expiry:', expiry)

try:
    oc = client.option_chain(symbol, segment, expiry)
    import json
    print('option_chain type:', type(oc))
    # print small snippet
    try:
        print(json.dumps(oc, indent=2)[:2000])
    except Exception:
        print('Could not JSON-dump result; repr type:', repr(type(oc)))
except Exception as e:
    print('option_chain call failed:', e)
    traceback.print_exc()

import os
try:
    import dhanhq
    from dhanhq import dhanhq as dh
    symbol='NIFTY'
    print('Calling dhanhq.dhanhq.option_chain...')
    try:
        res = dh.option_chain(symbol)
        print('Returned type:', type(res))
        import json
        s = json.dumps(res, indent=2)
        print(s[:2000])
    except Exception as e:
        print('SDK call raised:', repr(e))
except Exception as e:
    print('Import error:', repr(e))

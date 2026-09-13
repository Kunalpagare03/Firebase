import os
import json
try:
    import dhanhq
except Exception as e:
    print('dhanhq import failed:', e)
    raise

client_id = os.environ.get('DHAN_CLIENT_ID') or os.environ.get('DHAN_API_KEY')
access_token = os.environ.get('DHAN_ACCESS_TOKEN')
if not access_token:
    try:
        with open('data/dhan_access_token.txt') as f:
            access_token = f.read().strip()
    except Exception:
        pass

if not client_id:
    # try to extract from token
    try:
        if access_token:
            import base64
            parts = access_token.split('.')
            b = parts[1] + '=' * (-len(parts[1]) % 4)
            payload = json.loads(base64.urlsafe_b64decode(b.encode()))
            client_id = payload.get('dhanClientId')
    except Exception:
        pass

print('client_id:', client_id)
print('token present:', bool(access_token))

if not client_id or not access_token:
    print('missing credentials')
    raise SystemExit(1)

try:
    client = dhanhq.dhanhq.dhanhq(client_id, access_token, disable_ssl=True)
    print('client constructed')
    if hasattr(client, 'quote_data'):
        print('calling quote_data')
        res = client.quote_data('NIFTY')
        print('quote_data result:', res)
    else:
        print('client has no quote_data')
except Exception as e:
    print('SDK call failed:', e)
    raise

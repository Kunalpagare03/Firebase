import dhanhq, inspect

print('module members:', [n for n in dir(dhanhq) if not n.startswith('_')])
if hasattr(dhanhq, 'expiry_list'):
    print('expiry_list signature:', inspect.signature(dhanhq.expiry_list))
else:
    print('module has no expiry_list')

if hasattr(dhanhq, 'dhanhq'):
    print('dhanhq.dhanhq signature:', inspect.signature(dhanhq.dhanhq))

client = None
try:
    token = open('data/dhan_access_token.txt').read().strip()
    client = dhanhq.dhanhq('1100062523', token, disable_ssl=True)
    print('client members sample:', [n for n in dir(client) if not n.startswith('_')][:40])
    if hasattr(client, 'expiry_list'):
        print('client.expiry_list signature:', inspect.signature(client.expiry_list))
    if hasattr(client, 'option_chain'):
        print('client.option_chain signature:', inspect.signature(client.option_chain))
except Exception as e:
    print('failed to create client or inspect:', e)

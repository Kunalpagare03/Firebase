import inspect
try:
    import dhanhq.marketfeed as mf
except Exception as e:
    print('Import error:', e)
    raise

print('DhanFeed:', mf.DhanFeed)
import inspect
print('\nConstructor signature:')
try:
    print(inspect.signature(mf.DhanFeed.__init__))
except Exception as e:
    print('signature error:', e)

print('\nPublic members:')
members = [n for n, _ in inspect.getmembers(mf.DhanFeed) if not n.startswith('_')]
for m in members:
    print(m)

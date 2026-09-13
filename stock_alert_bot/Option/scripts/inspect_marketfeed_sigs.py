import inspect
import dhanhq.marketfeed as mf

print('authorize sig:', inspect.signature(mf.DhanFeed.authorize))
print('connect sig:', inspect.signature(mf.DhanFeed.connect))
print('subscribe_symbols sig:', inspect.signature(mf.DhanFeed.subscribe_symbols))
print('run_forever sig:', inspect.signature(mf.DhanFeed.run_forever))

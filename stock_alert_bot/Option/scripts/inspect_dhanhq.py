import dhanhq
print('dhanhq members:')
print(dir(dhanhq))
try:
    import inspect
    print('\nrepr:', repr(dhanhq))
    if hasattr(dhanhq, '__file__'):
        print('file:', dhanhq.__file__)
except Exception as e:
    print('inspect error:', e)

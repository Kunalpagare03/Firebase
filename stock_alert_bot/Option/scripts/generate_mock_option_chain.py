import importlib.util
import os


def load_generator():
    path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "step1_fetch_dhan.py")
    spec = importlib.util.spec_from_file_location("step1_fetch_dhan", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.generate_mock_option_chain


def main():
    import sys
    symbol = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("SYMBOL", "^NSEI")
    gen = load_generator()
    chain = gen(symbol)
    safe = symbol.replace("^", "").replace("/", "_")
    print(f"Generated mock option chain for {chain['underlying']} at spot {chain['spot_price']} -> data/option_chain_{safe}.json")


if __name__ == "__main__":
    main()

import os
import subprocess


def test_generate_and_analyze(tmp_path):
    root = os.path.dirname(os.path.dirname(__file__))
    gen = os.path.join(root, "scripts", "generate_mock_option_chain.py")
    ana = os.path.join(root, "scripts", "option_analysis.py")

    # Generate for NIFTY
    p = subprocess.run(["python", gen, "^NSEI"], cwd=root, capture_output=True, text=True)
    assert p.returncode == 0

    # Run analysis
    p2 = subprocess.run(["python", ana,], cwd=root, capture_output=True, text=True)
    assert p2.returncode == 0
    assert "Market Read" in p2.stdout

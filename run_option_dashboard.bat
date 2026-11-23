@echo off
echo Starting Option App v8 - Streamlit Dashboard...
python -m streamlit run optionapp/app/ui.py --server.port 8501 --server.headless false
pause

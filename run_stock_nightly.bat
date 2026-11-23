@echo off
title Stock App (v4) - Nightly Scanner
echo Running Stock App Nightly Quantitative Scan...
python -m stockapp.app.nightly %*
pause

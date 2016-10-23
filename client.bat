@echo off
if exist ClientRunner.class (
  java ClientRunner
) else (
  echo Program not compiled.
  pause
)

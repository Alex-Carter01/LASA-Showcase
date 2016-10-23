@echo off
if exist ServerRunner.class (
  java ServerRunner
) else (
  echo Program not compiled.
  pause
)

import os
import sys

print("Start Debugging...", flush=True)
try:
    import google.generativeai as genai
    print("Imported genai", flush=True)
except Exception as e:
    print(f"Failed to import genai: {e}", flush=True)

try:
    from github import Github, Auth
    print("Imported Github", flush=True)
except Exception as e:
    print(f"Failed to import Github: {e}", flush=True)

print("End Debugging.", flush=True)

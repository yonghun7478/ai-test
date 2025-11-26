import os
import sys
import argparse
import subprocess
import re
import google.generativeai as genai
from github import Github
from github import Auth

# --- Configuration ---
MODEL_3_0 = "gemini-1.5-pro-latest" # Updated to latest stable
MODEL_1_5 = "gemini-1.5-pro"

def get_model(api_key):
    genai.configure(api_key=api_key)
    return MODEL_3_0

def run_command(command):
    """Runs a shell command and returns (stdout, stderr, returncode)."""
    print(f"Running: {command}", flush=True)
    process = subprocess.Popen(
        command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
    )
    stdout, stderr = process.communicate()
    return stdout, stderr, process.returncode

def read_file(path):
    try:
        with open(path, 'r') as f:
            return f.read()
    except FileNotFoundError:
        return None

def write_file(path, content):
    dirname = os.path.dirname(path)
    if dirname:
        os.makedirs(dirname, exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)
    print(f"Wrote to {path}", flush=True)

def generate_content_safe(model_name, prompt, api_key):
    genai.configure(api_key=api_key)
    try:
        print(f"Attempting to generate with model: {model_name}", flush=True)
        model = genai.GenerativeModel(model_name)
        response = model.generate_content(prompt)
        return response
    except Exception as e:
        print(f"Error with model {model_name}: {e}", flush=True)
        # Fallback logic could be added here
        raise e

# --- AI Logic ---

def generate_spec(issue_body, api_key):
    prompt = f"""
    You are a Senior Android Architect.
    Create a detailed technical specification based on the following feature request (GitHub Issue).
    
    Format the output as Markdown.
    
    Template:
    # Specification: [Feature Name]

    ### Objective
    ...

    ### User Story
    ...

    ### Acceptance Criteria
    - [ ] ...

    ### Technical Details
    - **Files:** ...
    
    ### Implementation Plan
    1. ...
    2. ...

    Issue Description:
    {issue_body}
    """
    response = generate_content_safe(MODEL_3_0, prompt, api_key)
    return response.text

def implement_feature(spec_content, api_key):
    # 1. Generate Stubs
    print("--- Phase 1: Generating Stubs ---", flush=True)
    stub_prompt = f"""
    Based on this specification, generate the MINIMAL STUB CODE (interfaces, empty classes, method signatures).
    
    Specification:
    {spec_content}
    
    Output format:
    ### FILE: path/to/file.kt
    (Content)
    """
    stub_response = generate_content_safe(MODEL_3_0, stub_prompt, api_key)
    parse_and_write_files(stub_response.text)

    # 2. Generate Tests
    print("--- Phase 2: Generating Tests ---", flush=True)
    test_prompt = f"""
    Based on the specification and stubs, write Unit Tests.
    The tests should compile but FAIL (Red state).
    
    Specification:
    {spec_content}
    
    Output format:
    ### FILE: path/to/test/file.kt
    (Content)
    """
    test_response = generate_content_safe(MODEL_3_0, test_prompt, api_key)
    parse_and_write_files(test_response.text)

    # 3. Implementation Loop
    print("--- Phase 3: Implementation & Auto-fix Loop ---", flush=True)
    max_retries = 3
    test_result = "UNKNOWN"
    final_error_log = ""

    for attempt in range(1, max_retries + 1):
        print(f"Attempt {attempt}/{max_retries}: Running tests...", flush=True)
        stdout, stderr, returncode = run_command("./gradlew testDebugUnitTest")
        
        if returncode == 0:
            print("Tests PASSED!", flush=True)
            test_result = "PASSED"
            break
        
        print(f"Tests FAILED. Analyzing...", flush=True)
        final_error_log = (stderr + stdout)[-5000:]
        
        if attempt == max_retries:
            test_result = "FAILED"
            break

        fix_prompt = f"""
        The tests failed. Fix the implementation.
        
        Specification:
        {spec_content}
        
        Error Log:
        {final_error_log}
        
        Output format:
        ### FILE: path/to/file.kt
        (Content)
        """
        fix_response = generate_content_safe(MODEL_3_0, fix_prompt, api_key)
        parse_and_write_files(fix_response.text)
    
    # Write Report
    report = f"""
# Implementation Result

**Status:** {test_result}

## Details
- Max Retries: {max_retries}

## Last Error Log (if failed)
```
{final_error_log}
```
    """
    write_file("implementation_result.md", report)
    return True # Always return true to allow PR creation

def parse_and_write_files(llm_output):
    parts = llm_output.split("### FILE: ")
    for part in parts[1:]:
        lines = part.splitlines()
        filepath = lines[0].strip()
        content = "\n".join(lines[1:])
        # Basic cleanup of markdown code blocks
        content = re.sub(r'^```\w*\n', '', content)
        content = re.sub(r'\n```$', '', content)
        write_file(filepath.strip(), content)

# --- Main ---

def main():
    API_KEY = os.environ.get("GEMINI_API_KEY")
    GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
    REPO_NAME = os.environ.get("GITHUB_REPOSITORY")
    ISSUE_NUMBER = os.environ.get("ISSUE_NUMBER")

    if not API_KEY:
        print("Error: GEMINI_API_KEY is missing.", flush=True)
        sys.exit(1)

    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["spec", "implement"])
    args = parser.parse_args()

    if args.mode == "spec":
        if not GITHUB_TOKEN or not REPO_NAME or not ISSUE_NUMBER:
            print("Error: Github context missing.", flush=True)
            sys.exit(1)
            
        g = Github(auth=Auth.Token(GITHUB_TOKEN))
        repo = g.get_repo(REPO_NAME)
        issue = repo.get_issue(int(ISSUE_NUMBER))
        
        print(f"Generating spec for Issue #{ISSUE_NUMBER}", flush=True)
        spec = generate_spec(issue.body, API_KEY)
        
        # Write spec to file
        file_path = f"docs/specs/issue-{ISSUE_NUMBER}.md"
        write_file(file_path, spec)
        print(f"::set-output name=spec_path::{file_path}") # For Github Actions

    elif args.mode == "implement":
        # In this flow, we assume the spec is already in the repo (merged)
        # We look for docs/specs/issue-{ISSUE_NUMBER}.md
        
        if not ISSUE_NUMBER:
             print("Error: ISSUE_NUMBER env var required to find the spec.", flush=True)
             sys.exit(1)

        spec_path = f"docs/specs/issue-{ISSUE_NUMBER}.md"
        spec_content = read_file(spec_path)
        
        if not spec_content:
            print(f"Error: Spec file not found at {spec_path}", flush=True)
            # Try to fail gracefully or exit? 
            # The user wants a PR regardless, but without a spec we can't do anything.
            sys.exit(1)

        implement_feature(spec_content, API_KEY)

if __name__ == "__main__":
    main()
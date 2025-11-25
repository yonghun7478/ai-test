import os
import sys
import argparse
import subprocess
import re
import google.generativeai as genai
from github import Github
from github import Auth

# --- Configuration ---
# Move configuration reading inside functions or main to avoid global scope issues
# API_KEY = os.environ.get("GEMINI_API_KEY") 

MODEL_3_0 = "gemini-3-pro-preview"
MODEL_1_5 = "gemini-1.5-pro"

def get_model(api_key):
    genai.configure(api_key=api_key)
    # Return the model name string, instantiation happens later to handle fallbacks
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
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)
    print(f"Wrote to {path}", flush=True)

def generate_content_safe(model_name, prompt, api_key):
    """
    Attempts to generate content with the primary model.
    If it fails (e.g., 404 Not Found), falls back to the stable 1.5 model.
    """
    genai.configure(api_key=api_key)
    
    try:
        print(f"Attempting to generate with model: {model_name}", flush=True)
        model = genai.GenerativeModel(model_name)
        response = model.generate_content(prompt)
        return response
    except Exception as e:
        print(f"Error with model {model_name}: {e}", flush=True)
        if model_name != MODEL_1_5:
            print(f"Falling back to stable model: {MODEL_1_5}", flush=True)
            try:
                fallback_model = genai.GenerativeModel(MODEL_1_5)
                response = fallback_model.generate_content(prompt)
                return response
            except Exception as fallback_error:
                print(f"Fallback model also failed: {fallback_error}", flush=True)
                raise fallback_error
        raise e

# --- AI Logic ---

def generate_spec(issue_body, api_key):
    """Generates a technical specification from an issue description."""
    prompt = f"""
    You are a Senior Android Architect.
    Create a detailed technical specification based on the following feature request (GitHub Issue).
    Follow the 'Specification Template' strictly.
    
    Template:
    ### Feature/Bug Name: ...
    ### Objective: ...
    ### User Story / Scenario: ...
    ### Acceptance Criteria: ...
    ### Technical Details / Constraints: ...
    ### Deep Impact Analysis & Mitigation: ...
    ### Sub-task Plan: ...

    Issue Description:
    {issue_body}
    """
    response = generate_content_safe(MODEL_3_0, prompt, api_key)
    return response.text

def implement_feature(spec_content, task_id, api_key):
    """
    Orchestrates the Stub -> Test -> Implement loop.
    If task_id is provided, focuses ONLY on that sub-task.
    """
    
    focus_instruction = ""
    if task_id:
        focus_instruction = f"""
        IMPORTANT: You are implementing ONLY Sub-task #{task_id} from the 'Sub-task Plan' section of the specification.
        Ignore other sub-tasks for now. Focus only on the requirements for Sub-task #{task_id}.
        """
        print(f"--- Implementing Sub-task #{task_id} ---", flush=True)

    # 1. Generate Stubs
    print("--- Phase 1: Generating Stubs ---", flush=True)
    stub_prompt = f"""
    Based on this specification, generate the MINIMAL STUB CODE (interfaces, empty classes, method signatures) 
    required to write a compiling test. Do not implement logic yet.
    
    {focus_instruction}
    
    Specification:
    {spec_content}
    
    Output format:
    Please provide the code for each file separated by:
    ### FILE: path/to/file.kt
    (Content)
    """
    stub_response = generate_content_safe(MODEL_3_0, stub_prompt, api_key)
    parse_and_write_files(stub_response.text)

    # 2. Generate Tests
    print("--- Phase 2: Generating Tests ---", flush=True)
    test_prompt = f"""
    Based on the specification and the stubs you just created, write Unit Tests.
    The tests should compile but FAIL (Red state) because logic is missing.
    
    {focus_instruction}
    
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
    max_retries = 5
    for attempt in range(1, max_retries + 1):
        print(f"Attempt {attempt}/{max_retries}: Running tests...", flush=True)
        
        stdout, stderr, returncode = run_command("./gradlew testDebugUnitTest")
        
        if returncode == 0:
            print("Tests PASSED! Implementation complete.", flush=True)
            return True
        
        print(f"Tests FAILED. Analyzing error...", flush=True)
        error_log = (stderr + stdout)[-5000:] 
        
        fix_prompt = f"""
        The tests failed. Please fix the implementation code.
        
        {focus_instruction}
        
        Specification:
        {spec_content}
        
        Error Log:
        {error_log}
        
        Provide the corrected full code for the modified files.
        Output format:
        ### FILE: path/to/file.kt
        (Content)
        """
        
        fix_response = generate_content_safe(MODEL_3_0, fix_prompt, api_key)
        parse_and_write_files(fix_response.text)
    
    print("Max retries reached. Implementation failed.", flush=True)
    return False

def parse_and_write_files(llm_output):
    """Parses custom delimited output from LLM and writes files."""
    parts = llm_output.split("### FILE: ")
    for part in parts[1:]: # Skip preamble
        lines = part.splitlines()
        filepath = lines[0].strip()
        content = "\n".join(lines[1:])
        content = content.replace("```kotlin", "").replace("```", "")
        write_file(filepath.strip(), content)

# --- Main Entry Point ---

def main():
    print("Script started.", flush=True)
    
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
            print("Error: Github credentials/context missing.", flush=True)
            sys.exit(1)
            
        g = Github(auth=Auth.Token(GITHUB_TOKEN))
        repo = g.get_repo(REPO_NAME)
        issue = repo.get_issue(int(ISSUE_NUMBER))
        
        print(f"Generating spec for Issue #{ISSUE_NUMBER}: {issue.title}", flush=True)
        spec = generate_spec(issue.body, API_KEY)
        
        issue.create_comment(f"## Generated Specification\n\n{spec}\n\nplease review and approve with /implement")
        print("Spec posted to issue.", flush=True)

    elif args.mode == "implement":
        print(f"Starting implementation mode for Issue #{ISSUE_NUMBER}", flush=True)
        
        spec_content = read_file("CURRENT_SPEC.md")
        if not spec_content and GITHUB_TOKEN and ISSUE_NUMBER:
             g = Github(auth=Auth.Token(GITHUB_TOKEN))
             repo = g.get_repo(REPO_NAME)
             issue = repo.get_issue(int(ISSUE_NUMBER))
             
             print("Searching for specification in comments...", flush=True)
             comments = list(issue.get_comments())
             for comment in reversed(comments):
                 print(f"Checking comment ID {comment.id}: {comment.body[:50]}...", flush=True)
                 if "### Feature/Bug Name" in comment.body or "## Generated Specification" in comment.body:
                     spec_content = comment.body
                     print(f"Found specification in comment ID {comment.id}", flush=True)
                     break
        
        if not spec_content:
            print("Error: No valid specification found in issue comments.", flush=True)
            sys.exit(1)

        comment_body = os.environ.get("COMMENT_BODY", "")
        print(f"Received comment body: {comment_body}", flush=True)
        
        task_id = None
        match = re.search(r'/implement\s+(\d+)', comment_body)
        if match:
            task_id = match.group(1)
            print(f"Detected request for Sub-task #{task_id}", flush=True)
        else:
            print("No sub-task ID detected, implementing full spec.", flush=True)

        success = implement_feature(spec_content, task_id, API_KEY)
        if not success:
            sys.exit(1)

if __name__ == "__main__":
    main()
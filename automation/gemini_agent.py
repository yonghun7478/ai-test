import os
import sys
import argparse
import subprocess
import re
import google.generativeai as genai
from github import Github
from github import Auth

# --- Configuration ---
API_KEY = os.environ.get("GEMINI_API_KEY")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
REPO_NAME = os.environ.get("GITHUB_REPOSITORY") # e.g., "user/repo"
ISSUE_NUMBER = os.environ.get("ISSUE_NUMBER")

if API_KEY:
    genai.configure(api_key=API_KEY)

MODEL_NAME = "gemini-3-pro-preview"

def get_model():
    return genai.GenerativeModel(MODEL_NAME)

def run_command(command):
    """Runs a shell command and returns (stdout, stderr, returncode)."""
    print(f"Running: {command}")
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
    print(f"Wrote to {path}")

# --- AI Logic ---

def generate_spec(issue_body):
    """Generates a technical specification from an issue description."""
    model = get_model()
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
    response = model.generate_content(prompt)
    return response.text

def implement_feature(spec_content, task_id=None):
    """
    Orchestrates the Stub -> Test -> Implement loop.
    If task_id is provided, focuses ONLY on that sub-task.
    """
    model = get_model()
    
    focus_instruction = ""
    if task_id:
        focus_instruction = f"""
        IMPORTANT: You are implementing ONLY Sub-task #{task_id} from the 'Sub-task Plan' section of the specification.
        Ignore other sub-tasks for now. Focus only on the requirements for Sub-task #{task_id}.
        """
        print(f"--- Implementing Sub-task #{task_id} ---")

    # 1. Generate Stubs
    print("--- Phase 1: Generating Stubs ---")
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
    stub_response = model.generate_content(stub_prompt)
    parse_and_write_files(stub_response.text)

    # 2. Generate Tests
    print("--- Phase 2: Generating Tests ---")
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
    test_response = model.generate_content(test_prompt)
    parse_and_write_files(test_response.text)

    # 3. Implementation Loop
    print("--- Phase 3: Implementation & Auto-fix Loop ---")
    max_retries = 5
    for attempt in range(1, max_retries + 1):
        print(f"Attempt {attempt}/{max_retries}: Running tests...")
        
        stdout, stderr, returncode = run_command("./gradlew testDebugUnitTest")
        
        if returncode == 0:
            print("Tests PASSED! Implementation complete.")
            return True
        
        print(f"Tests FAILED. Analyzing error...")
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
        
        fix_response = model.generate_content(fix_prompt)
        parse_and_write_files(fix_response.text)
    
    print("Max retries reached. Implementation failed.")
    return False

def parse_and_write_files(llm_output):
    """Parses custom delimited output from LLM and writes files."""
    # This is a simple parser. In production, use more robust parsing or JSON mode.
    parts = llm_output.split("### FILE: ")
    for part in parts[1:]: # Skip preamble
        lines = part.splitlines()
        filepath = lines[0].strip()
        content = "\n".join(lines[1:])
        # Remove markdown code fences if present
        content = content.replace("```kotlin", "").replace("```", "")
        write_file(filepath.strip(), content)

# --- Main Entry Point ---

def main():
    print("Script started.", flush=True) # Immediate log
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["spec", "implement"])
    args = parser.parse_args()

    if args.mode == "spec":
        if not GITHUB_TOKEN or not REPO_NAME or not ISSUE_NUMBER:
            print("Error: Github credentials/context missing.")
            sys.exit(1)
            
        g = Github(GITHUB_TOKEN)
        repo = g.get_repo(REPO_NAME)
        issue = repo.get_issue(int(ISSUE_NUMBER))
        
        print(f"Generating spec for Issue #{ISSUE_NUMBER}: {issue.title}")
        spec = generate_spec(issue.body)
        
        # Post spec as comment
        issue.create_comment(f"## Generated Specification\n\n{spec}\n\nplease review and approve with /implement")
        print("Spec posted to issue.")

    elif args.mode == "implement":
        print(f"Starting implementation mode for Issue #{ISSUE_NUMBER}")
        
        # In a real workflow, we might read the approved spec from a file or the issue comment.
        # For this POC, let's assume spec is passed via a file 'CURRENT_SPEC.md' 
        # or we read the last comment from the issue.
        
        spec_content = read_file("CURRENT_SPEC.md")
        if not spec_content and GITHUB_TOKEN and ISSUE_NUMBER:
             # Try fetching from issue
             g = Github(auth=Auth.Token(GITHUB_TOKEN))
             repo = g.get_repo(REPO_NAME)
             issue = repo.get_issue(int(ISSUE_NUMBER))
             
             print("Searching for specification in comments...")
             # Find the latest comment that looks like a spec
             comments = list(issue.get_comments()) # Convert to list to reverse easily
             for comment in reversed(comments):
                 print(f"Checking comment ID {comment.id}: {comment.body[:50]}...")
                 if "### Feature/Bug Name" in comment.body or "## Generated Specification" in comment.body:
                     spec_content = comment.body
                     print(f"Found specification in comment ID {comment.id}")
                     break
        
        if not spec_content:
            print("Error: No valid specification found in issue comments.")
            sys.exit(1)

        # Parse task_id from comment body if available
        comment_body = os.environ.get("COMMENT_BODY", "")
        print(f"Received comment body: {comment_body}")
        
        task_id = None
        match = re.search(r'/implement\s+(\d+)', comment_body)
        if match:
            task_id = match.group(1)
            print(f"Detected request for Sub-task #{task_id}")
        else:
            print("No sub-task ID detected, implementing full spec.")

        success = implement_feature(spec_content, task_id)
        if not success:
            sys.exit(1) # Fail the workflow
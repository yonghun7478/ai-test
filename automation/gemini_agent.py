import os
import sys
import argparse
import subprocess
import re
import json
import google.generativeai as genai
from github import Github
from github import Auth

# --- Configuration ---
MODEL_NAME = "gemini-3-pro-preview" # or gemini-pro if 1.5 is not available

def get_model(api_key):
    genai.configure(api_key=api_key)
    return genai.GenerativeModel(MODEL_NAME)

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

# --- AI Logic ---

def plan_epic(issue, api_key):
    """
    Analyzes the Epic issue and suggests a breakdown of tasks.
    """
    model = get_model(api_key)
    
    prompt = f"""
    You are a Senior Android Architect.
    The user has created an Epic Issue. Your goal is to analyze the requirements and propose a breakdown of Tasks (Sub-issues).

    Epic Title: {issue.title}
    Epic Description:
    {issue.body}

    Please provide a detailed plan in the following JSON format embedded in a Markdown comment: 
    
    Start your response with a brief analysis (text), then provide the tasks inside a JSON block.

    Example Output:
    Based on the requirements, here is the breakdown...

    ```json
    [
      {{
        "title": "[Task] Setup Project Structure",
        "body": "Initialize the repository and basic Gradle setup."
      }},
      {{
        "title": "[Task] Implement Login Feature",
        "body": "Create LoginActivity and handle authentication logic."
      }}
    ]
    ```
    """
    
    response = model.generate_content(prompt)
    
    # Post the plan as a comment on the issue
    issue.create_comment(response.text)
    print("Posted plan to issue.")

def create_subtasks(issue, api_key):
    """
    Reads the last AI comment (containing JSON plan) and creates sub-issues.
    """
    # 1. Find the last comment by the bot (or check recent comments for JSON)
    comments = issue.get_comments()
    target_comment = None
    
    # Iterate backwards to find the latest plan
    for comment in reversed(list(comments)):
        if "```json" in comment.body:
            target_comment = comment.body
            break
    
    if not target_comment:
        print("No JSON plan found in recent comments.")
        return

    # 2. Extract JSON
    try:
        json_match = re.search(r'```json\n(.*?)\n```', target_comment, re.DOTALL)
        if json_match:
            json_str = json_match.group(1)
            tasks = json.loads(json_str)
            
            created_issues_list = []
            
            for task in tasks:
                print(f"Creating issue: {task['title']}")
                new_issue = issue.repository.create_issue(
                    title=task['title'],
                    body=f"{task['body']}\n\nParent Epic: #{issue.number}"
                )
                created_issues_list.append(f"- [ ] #{new_issue.number} : {task['title']}")
            
            # 3. Update the Epic issue body with the task list
            current_body = issue.body or ""
            updated_body = current_body + "\n\n## Tasks\n" + "\n".join(created_issues_list)
            issue.edit(body=updated_body)
            
            issue.create_comment(f"✅ Created {len(tasks)} sub-issues and linked them to this Epic.")
            
        else:
            print("Could not parse JSON from the comment.")
            
    except Exception as e:
        print(f"Error creating tasks: {e}")
        issue.create_comment(f"❌ Error creating sub-issues: {e}")


def implement_task(issue, context_comment, api_key):
    """
    Implements a sub-task based on the issue body and the trigger comment (context).
    """
    model = get_model(api_key)
    
    print(f"Implementing Task #{issue.number} with context: {context_comment}")

    # 1. Generate Implementation Plan & Code
    prompt = f"""
    You are a Senior Android Architect.
    You are tasked to implement a specific feature (Sub-issue).
    
    Task Title: {issue.title}
    Task Description:
    {issue.body}
    
    IMPORTANT CONTEXT from User:
    "{context_comment}"
    (Use this context to understand dependencies, pre-merged PRs, or specific constraints.)
    
    Please generate the implementation code. 
    
    Output format:
    ### FILE: path/to/file.kt
    (Content)
    ...
    
    Start with a brief summary of what you are going to do.
    """
    
    response = model.generate_content(prompt)
    
    # Parse and write files
    files_created = parse_and_write_files(response.text)
    
    if not files_created:
        print("No files were generated.")
        return

    # 2. Verify (Optional: Run tests)
    # print("Running tests...")
    # run_command("./gradlew testDebugUnitTest")

    # 3. Create PR (handled by GitHub Actions via file system, or we can do it here via API?)
    # The current workflow prefers creating PR via the Action step using `peter-evans/create-pull-request`
    # because it handles git auth and branching nicely. 
    # So we just leave the changes in the file system.
    
    # We will save the report to be used in the PR body
    report = f"""
    # Implementation Report for #{issue.number}
    
    ## Context
    {context_comment}
    
    ## AI Analysis
    {response.text[:500]}... (truncated)
    """
    write_file("implementation_report.md", report)


def parse_and_write_files(llm_output):
    parts = llm_output.split("### FILE: ")
    files_written = []
    for part in parts[1:]:
        lines = part.splitlines()
        filepath = lines[0].strip()
        content = "\n".join(lines[1:])
        # Cleanup markdown code blocks
        content = re.sub(r'^```\w*\n', '', content)
        content = re.sub(r'\n```$', '', content)
        write_file(filepath.strip(), content)
        files_written.append(filepath)
    return files_written

# --- Main ---

def main():
    API_KEY = os.environ.get("GEMINI_API_KEY")
    GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
    REPO_NAME = os.environ.get("GITHUB_REPOSITORY")
    ISSUE_NUMBER = os.environ.get("ISSUE_NUMBER")
    COMMENT_BODY = os.environ.get("COMMENT_BODY") # Passed from workflow

    if not API_KEY:
        print("Error: GEMINI_API_KEY is missing.", flush=True)
        sys.exit(1)

    if not GITHUB_TOKEN or not REPO_NAME or not ISSUE_NUMBER:
        print("Error: Github context missing.", flush=True)
        sys.exit(1)

    g = Github(auth=Auth.Token(GITHUB_TOKEN))
    repo = g.get_repo(REPO_NAME)
    issue = repo.get_issue(int(ISSUE_NUMBER))

    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["plan", "create-tasks", "implement"])
    args = parser.parse_args()

    if args.mode == "plan":
        print(f"Planning for Issue #{ISSUE_NUMBER}")
        plan_epic(issue, API_KEY)

    elif args.mode == "create-tasks":
        print(f"Creating tasks for Issue #{ISSUE_NUMBER}")
        create_subtasks(issue, API_KEY)

    elif args.mode == "implement":
        print(f"Implementing Issue #{ISSUE_NUMBER}")
        implement_task(issue, COMMENT_BODY, API_KEY)

if __name__ == "__main__":
    main()

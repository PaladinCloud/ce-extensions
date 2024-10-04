import os
import subprocess
import json

def run_aws_command(command):
    """Run an AWS CLI command and return the output or error message."""
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        return result.stdout, None  # Return stdout as result and None for error
    except subprocess.CalledProcessError as e:
        print(f"Error: {e.stderr}")  # Print any errors
        return None, e.stderr  # Return None for output and the error message

def stack_exists(stack_name):
    """Check if the CloudFormation stack exists."""
    command = [
        "aws", "cloudformation", "describe-stacks",
        "--stack-name", stack_name
    ]
    
    result, error = run_aws_command(command)
    
    # If result is None, an error occurred. Check the error message.
    if result is None:
        if "does not exist" in error:
            return False  # Stack does not exist
        else:
            print("Unexpected error occurred.")
            return None

    # If we have valid output, we can check if the stack exists
    stacks_info = json.loads(result)
    return 'Stacks' in stacks_info and len(stacks_info['Stacks']) > 0

def validate_parameters(parameters):
    """Ensure all required parameters are provided."""
    for param in parameters:
        if not param['ParameterValue']:
            print(f"Error: ParameterValue for ParameterKey {param['ParameterKey']} is required.")
            exit(1)

def create_stack(stack_name, template_body, parameters, tags):
    """Create a new CloudFormation stack."""
    command = [
        "aws", "cloudformation", "create-stack",
        "--stack-name", stack_name,
        "--template-body", template_body,
        "--parameters",
    ]
    
    # Prepare parameters as a single list without extra commas
    param_strings = [f"ParameterKey={param['ParameterKey']},ParameterValue={param['ParameterValue']}" for param in parameters]
    command.extend(param_strings)

    # Correctly formatting tags for AWS CLI
    if tags:
        command.append("--tags")
        tag_strings = [f"Key={tag['Key']},Value={tag['Value']}" for tag in tags]
        command.extend(tag_strings)

    command.extend(["--capabilities", "CAPABILITY_IAM", "CAPABILITY_AUTO_EXPAND", "CAPABILITY_NAMED_IAM"])
    
    print(f"Creating stack with command: {' '.join(command)}")  # Debugging line
    run_aws_command(command)

def update_stack(stack_name, template_body, parameters, tags):
    """Update an existing CloudFormation stack."""
    command = [
        "aws", "cloudformation", "update-stack",
        "--stack-name", stack_name,
        "--template-body", template_body,
        "--parameters",
    ]
    
    # Prepare parameters as a single list without extra commas
    param_strings = [f"ParameterKey={param['ParameterKey']},ParameterValue={param['ParameterValue']}" for param in parameters]
    command.extend(param_strings)

    # Correctly formatting tags for AWS CLI
    if tags:
        command.append("--tags")
        tag_strings = [f"Key={tag['Key']},Value={tag['Value']}" for tag in tags]
        command.extend(tag_strings)

    command.extend(["--capabilities", "CAPABILITY_IAM", "CAPABILITY_AUTO_EXPAND", "CAPABILITY_NAMED_IAM"])
    
    print(f"Updating stack with command: {' '.join(command)}")  # Debugging line
    run_aws_command(command)

def main():
    # Environment Variables
    stack_name = "svc-dev-delta-engine"
    template_body = "file://delta-engine.yaml"
    
    # Log environment variables for debugging
    print("Environment Variables:")
    print(f"ENVIRONMENT: {os.getenv('ENVIRONMENT')}")
    print(f"SUBNET1: {os.getenv('SUBNET1')}")
    print(f"SUBNET2: {os.getenv('SUBNET2')}")
    print(f"VPCID: {os.getenv('VPCID')}")
    print(f"CIDRIP: {os.getenv('CIDRIP')}")
    print(f"S3BUCKETNAME: {os.getenv('S3BUCKETNAME')}")
    print(f"S3BUCKETKEY: {os.getenv('S3BUCKETKEY')}")

    parameters = [
        {"ParameterKey": "Environment", "ParameterValue": os.getenv("ENVIRONMENT", "dev")},
        {"ParameterKey": "Subnet1", "ParameterValue": os.getenv("SUBNET1", "subnet-0f49b019a9505f7fc")},
        {"ParameterKey": "Subnet2", "ParameterValue": os.getenv("SUBNET2", "subnet-03d6c8d6b71327b54")},
        {"ParameterKey": "VpcId", "ParameterValue": os.getenv("VPCID", "vpc-012335987c4c5ef8e")},
        {"ParameterKey": "CidrIp", "ParameterValue": os.getenv("CIDRIP", "10.0.0.0/20")},
        {"ParameterKey": "S3BUCKETNAME", "ParameterValue": os.getenv("S3BUCKETNAME","paladincloud-dev-builds")},
        {"ParameterKey": "S3BUCKETKEY", "ParameterValue": os.getenv("S3BUCKETKEY","vlatest/dev/lambda/asset-sender-1.0-SNAPSHOT.jar")},
    ]

    # Log parameters for debugging
    for param in parameters:
        print(f"{param['ParameterKey']}: {param['ParameterValue']}")

    # Validate parameters
    validate_parameters(parameters)

    tags = [
        {"Key": "env", "Value": os.getenv("ENVTAG", "dev")},
        {"Key": "tenant", "Value": "saas"},
        {"Key": "component", "Value": "DeltaEngineSubsystem"},
    ]
    
    # Check if the stack exists
    exists = stack_exists(stack_name)
    if exists is None:
        print(f"Failed to check stack existence for {stack_name}. Exiting...")
        exit(1)
    
    if exists:
        print(f"Stack {stack_name} exists. Updating...")
        update_stack(stack_name, template_body, parameters, tags)
    else:
        print(f"Stack {stack_name} does not exist. Creating...")
        create_stack(stack_name, template_body, parameters, tags)

if __name__ == "__main__":
    main()

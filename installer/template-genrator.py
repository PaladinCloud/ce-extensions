import os
import yaml  # Ensure you have PyYAML installed
import re  # For removing unwanted characters

def load_configurations(config_folder):
    configurations = []
    for filename in os.listdir(config_folder):
        if filename.endswith('.yml') or filename.endswith('.yaml'):
            with open(os.path.join(config_folder, filename), 'r') as file:
                config = yaml.safe_load(file)
                configurations.extend(config)  # Flatten the list of services
    return configurations

def sanitize_logical_id(logical_id):
    """Remove hyphens and other invalid characters for CloudFormation Logical IDs."""
    return re.sub(r'[^a-zA-Z0-9]', '', logical_id)

def generate_iam_policy(service):
    permissions = service.get('serviceConfig', {}).get('lambdaConfig', {}).get('permissions', {})
    
    statements = []
    
    # Append statements based on required permissions
    if permissions.get('requireS3', False):
        statements.append({
            "Effect": "Allow",
            "Action": "s3:*",
            "Resource": "*"
        })
    
    if permissions.get('requireDynamoDB', False):
        statements.append({
            "Effect": "Allow",
            "Action": "dynamodb:*",
            "Resource": "*"
        })
    
    if permissions.get('requireSecretsManager', False):
        statements.append({
            "Effect": "Allow",
            "Action": "secretsmanager:*",
            "Resource": "*"
        })
    
    if permissions.get('requireEC2', False):
        statements.append({
            "Effect": "Allow",
            "Action": "ec2:*",
            "Resource": "*"
        })

    if statements:
        return {
            "Version": "2012-10-17",
            "Statement": statements
        }
    
    return None

def ref_to_cfn_format(key):
    """Return a reference formatted as a string for YAML output."""
    return f"!Ref {key}"

def create_resource_block(resource_type, properties):
    return {
        'Type': resource_type,
        'Properties': properties
    }

def generate_cloudformation_template(environment, service):
    resources = {}
    iam_roles = {}
    api_gateway = {}
    
    service_name = service.get('serviceName')
    if not service_name:
        print("Warning: Service missing 'serviceName'. Skipping.")
        return None

    sanitized_service_name = sanitize_logical_id(service_name)

    # Generate IAM Role
    role_name = f"svc{environment.capitalize()}{sanitized_service_name.capitalize()}Role"
    policy_document = generate_iam_policy(service)

    iam_roles[role_name] = create_resource_block(
        "AWS::IAM::Role",
        {
            "RoleName": role_name,
            "AssumeRolePolicyDocument": {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Principal": {
                            "Service": "lambda.amazonaws.com"
                        },
                        "Action": "sts:AssumeRole"
                    }
                ]
            },
            "Policies": [{
                "PolicyName": f"svc{environment.capitalize()}Policy{sanitized_service_name.capitalize()}",
                "PolicyDocument": policy_document
            }] if policy_document else []
        }
    )

    # Lambda Function Definition
    lambda_function_name = f"svc{environment.capitalize()}{sanitized_service_name.capitalize()}"
    resources[lambda_function_name] = create_resource_block(
        "AWS::Lambda::Function",
        {
            "FunctionName": lambda_function_name,
            "Handler": service['serviceConfig']['lambdaConfig']['handler'],
            "Runtime": service['serviceConfig']['lambdaConfig']['runtime'],
            "Role": {"Fn::GetAtt": [role_name, "Arn"]},
            "MemorySize": 512,
            "Timeout": service['serviceConfig']['lambdaConfig'].get('timeout', 10),
            "Environment": {
                "Variables": service.get('serviceConfig', {}).get('environmentalVariables', {})
            },
            "Layers": service['serviceConfig']['lambdaConfig'].get('layers', []),
            "VpcConfig": {
                "SubnetIds": [
                    ref_to_cfn_format("PrivateSubnet1"),
                    ref_to_cfn_format("PrivateSubnet2")
                ],
                "SecurityGroupIds": [
                    ref_to_cfn_format("PluginLambdaSecurityGroup")
                ]
            }
        }
    )

    # API Gateway Integration
    if 'apiConfig' in service:
        api_method = service['apiConfig']['method']
        api_path = service['apiConfig']['path']
        require_auth = service['apiConfig'].get('requireAuth', False)
        require_options_method = service['apiConfig'].get('requireOptionsMethod', False)

        # Create API Gateway
        api_gateway_id = f"ApiGateway{environment.capitalize()}"
        api_gateway[api_gateway_id] = create_resource_block(
            "AWS::ApiGatewayV2::Api",
            {
                "Name": ref_to_cfn_format("ApiGatewayId"),
                "ProtocolType": "HTTP",
                "Description": "HTTP API Gateway for Lambda functions."
            }
        )

        # Create API Gateway Integration
        integration_id = f"{sanitized_service_name}Integration"
        resources[integration_id] = create_resource_block(
            "AWS::ApiGatewayV2::Integration",
            {
                "ApiId": ref_to_cfn_format(api_gateway_id),
                "IntegrationType": "AWS_PROXY",
                "IntegrationUri": {
                    "Fn::Sub": f"arn:aws:apigateway:${{AWS::Region}}:lambda:path/2015-03-31/functions/${{ {lambda_function_name} }}/invocations"
                },
                "PayloadFormatVersion": "2.0",
                "TimeoutInMillis": 29000,
            }
        )
        
        # Create Route for the main API method
        api_route_id = f"{sanitized_service_name}Route"
        resources[api_route_id] = create_resource_block(
            "AWS::ApiGatewayV2::Route",
            {
                "ApiId": ref_to_cfn_format(api_gateway_id),
                "RouteKey": f"{api_method} {api_path}",
                "Target": f"integrations/{integration_id}",
                "AuthorizationType": "JWT" if require_auth else "NONE"
            }
        )

        # If OPTIONS method is required
        if require_options_method:
            options_route_id = f"{sanitized_service_name}OptionsRoute"
            resources[options_route_id] = create_resource_block(
                "AWS::ApiGatewayV2::Route",
                {
                    "ApiId": ref_to_cfn_format(api_gateway_id),
                    "RouteKey": f"OPTIONS {api_path}",
                    "Target": f"integrations/{integration_id}",
                    "AuthorizationType": "NONE"
                }
            )

        # Grant API Gateway permission to invoke the Lambda function
        resources[f"{lambda_function_name}InvokePermission"] = create_resource_block(
            "AWS::Lambda::Permission",
            {
                "Action": "lambda:InvokeFunction",
                "FunctionName": ref_to_cfn_format(lambda_function_name),
                "Principal": "apigateway.amazonaws.com"
            }
        )

    # SQS Queue Creation
    if 'sqsQueues' in service['serviceConfig']:
        sqs_queue_name = f"svc{environment.capitalize()}{sanitized_service_name.capitalize()}done.fifo"
        resources[sqs_queue_name] = create_resource_block(
            "AWS::SQS::Queue",
            {
                "QueueName": sqs_queue_name,
                "VisibilityTimeout": 30,
                "MessageRetentionPeriod": 1209600,
                "FifoQueue": True,
                "Tags": [
                    {
                        "Key": "Environment",
                        "Value": environment
                    }
                ]
            }
        )

        # Lambda Trigger from SQS for each queue
        lambda_event_source_mapping_name = f"EventSourceMapping{sanitized_service_name}"
        resources[lambda_event_source_mapping_name] = create_resource_block(
            "AWS::Lambda::EventSourceMapping",
            {
                "BatchSize": 10,
                "EventSourceArn": ref_to_cfn_format(sqs_queue_name),
                "FunctionName": ref_to_cfn_format(lambda_function_name),
                "Enabled": True
            }
        )

    # Combine IAM roles, API Gateway, and Lambda functions into the CloudFormation template
    return {
        "AWSTemplateFormatVersion": "2010-09-09",
        "Description": f"CloudFormation template for {sanitized_service_name}.",
        "Parameters": {
            "TenantName": {
                "Type": "String",
                "Description": "The tenant identifier to be used in the Lambda function name."
            },
            "Environment": {
                "Type": "String",
                "Description": "The environment identifier (e.g., dev, prod)."
            },
            "VpcId": {
                "Type": "AWS::EC2::VPC::Id",
                "Description": "The ID of the VPC where the Lambda function and SQS queues will be deployed."
            },
            "PrivateSubnet1": {
                "Type": "AWS::EC2::Subnet::Id",
                "Description": "The ID of the first private subnet for the Lambda function."
            },
            "PrivateSubnet2": {
                "Type": "AWS::EC2::Subnet::Id",
                "Description": "The ID of the second private subnet for the Lambda function."
            },
            "PluginLambdaSecurityGroup": {
                "Type": "AWS::EC2::SecurityGroup::Id",
                "Description": "The ID of the security group for the Lambda function."
            },
            "ApiGatewayId": {
                "Type": "String",
                "Description": "The ID of the API Gateway."
            }
        },
        "Resources": {**resources, **iam_roles, **api_gateway}
    }

def save_template(template, output_folder, service_name):
    """Save the CloudFormation template to a YAML file."""
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)
        
    template_path = os.path.join(output_folder, f"{service_name}.yml")
    with open(template_path, 'w') as file:
        yaml.dump(template, file, sort_keys=False, default_flow_style=False)

def main(env):
    config_folder = './config'
    output_folder = './output_folder'
    configurations = load_configurations(config_folder)

    for service in configurations:
        service_name = service.get('serviceName', 'UnknownService')
        template = generate_cloudformation_template(env, service)
        if template:
            save_template(template, output_folder, service_name)

if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Usage: python3 template-generator.py <env>")
        sys.exit(1)
    
    environment = sys.argv[1]  # Retrieve the environment argument
    main(environment)

import os
import yaml
from aws_cdk import (
    aws_codepipeline as codepipeline,
    aws_codepipeline_actions as codepipeline_actions,
    aws_codebuild as codebuild,
    aws_s3 as s3,
    aws_lambda as _lambda,
    core,
)

class MultiSubsystemPipeline(core.Stack):
    def __init__(self, scope: core.Construct, id: str, **kwargs) -> None:
        super().__init__(scope, id, **kwargs)

        # S3 bucket to store artifacts (shared among all pipelines)
        artifact_bucket = s3.Bucket(self, "ArtifactBucket")

        # Dynamically find all subsystems that have config.yaml
        base_path = "path_to_repo_root"  # Replace with actual base path where subsystems exist
        subsystems = []
        for root, dirs, files in os.walk(base_path):
            if "config.yaml" in files:
                subsystems.append(root)

        # Create a separate pipeline for each subsystem
        for subsystem in subsystems:
            # Load the config.yaml from each subsystem
            with open(os.path.join(subsystem, "config.yaml"), 'r') as stream:
                config = yaml.safe_load(stream)

            # Create a pipeline for each subsystem
            pipeline = codepipeline.Pipeline(self, f"{subsystem}-Pipeline",
                                             pipeline_name=f"{subsystem}-Pipeline",
                                             artifact_bucket=artifact_bucket)

            # Source Stage (GitHub)
            source_output = codepipeline.Artifact()
            pipeline.add_stage(
                stage_name="Source",
                actions=[
                    codepipeline_actions.GitHubSourceAction(
                        action_name="GitHub_Source",
                        owner="my-github-account",
                        repo="my-service-repo",
                        branch="main",
                        oauth_token=core.SecretValue.secrets_manager("my-github-token"),
                        output=source_output
                    )
                ]
            )

            # Build Stage
            build_output = codepipeline.Artifact(f"{subsystem}-BuildOutput")
            build_project = codebuild.PipelineProject(self, f"{subsystem}-BuildProject",
                build_spec=codebuild.BuildSpec.from_object({
                    "version": "0.2",
                    "phases": {
                        "install": {
                            "commands": [f"cd {subsystem}", config['services'][0]['build']]
                        },
                        "build": {
                            "commands": [f"cd {subsystem}", config['services'][0]['build']]
                        }
                    },
                    "artifacts": {
                        "files": "**/*",
                        "base-directory": f"{subsystem}/build"
                    }
                })
            )

            pipeline.add_stage(
                stage_name="Build",
                actions=[
                    codepipeline_actions.CodeBuildAction(
                        action_name="CodeBuild",
                        project=build_project,
                        input=source_output,
                        outputs=[build_output]
                    )
                ]
            )

            # Deploy Stage (based on config.yaml)
            if config['services'][0]['deploy'] == 'lambda':
                lambda_function = _lambda.Function(self, f"{subsystem}-LambdaFunction",
                    runtime=_lambda.Runtime.PYTHON_3_8,
                    handler="index.handler",
                    code=_lambda.Code.from_asset(f"{subsystem}/build")
                )

                pipeline.add_stage(
                    stage_name="Deploy",
                    actions=[
                        codepipeline_actions.LambdaInvokeAction(
                            action_name=f"{subsystem}-Deploy_Lambda",
                            lambda_function=lambda_function
                        )
                    ]
                )
            elif config['services'][0]['deploy'] == 'ecs':
                # ECS deployment code here
                pass

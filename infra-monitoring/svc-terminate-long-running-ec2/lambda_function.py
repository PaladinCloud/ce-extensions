import boto3
from datetime import datetime
import os

def send_sns_notification(topic_arn, message):
    sns_client = boto3.client('sns')
    sns_client.publish(
        TopicArn=topic_arn,
        Message=message
    )

def lambda_handler(event, context):
    # Initialize the AWS Batch client
    batch_client = boto3.client('batch')

    # Specify the SNS topic ARN where you want to send notifications
    sns_topic_arn = os.environ['OUTPUT_TOPIC_ARN']

    # List all job queues
    response = batch_client.describe_job_queues()

    # Check if the response contains job queues
    if 'jobQueues' in response:
        job_queues = response['jobQueues']

        if job_queues:
            output = "Job queues with running jobs:\n"

            for queue in job_queues:
                job_queue_name = queue['jobQueueName']

                output += f"\n{job_queue_name}:\n"  # Include the job queue name

                # Describe the jobs in the current job queue
                jobs_response = batch_client.list_jobs(jobQueue=job_queue_name, jobStatus='RUNNING')

                if 'jobSummaryList' in jobs_response:
                    running_jobs = jobs_response['jobSummaryList']
                    if running_jobs:
                        for job in running_jobs:
                            job_id = job['jobId']
                            status = job['status']
                            started_at_ms = job['startedAt'] if 'startedAt' in job else None
                            job_name = job['jobName'] if 'jobName' in job else "N/A"  # Get the job name

                            if started_at_ms:
                                started_at = datetime.fromtimestamp(started_at_ms / 1000.0)
                                current_time = datetime.now()
                                duration = current_time - started_at
                                # Check if duration is more than 3 hour for other queues
                                if (duration.total_seconds() > 18000):
                                    # Terminate the job
                                    batch_client.terminate_job(jobId=job_id, reason='Job exceeded duration')

                                    # Send notification for termination
                                    message = "- Job Queue: {}, Job Name: {}, ID: {}, Status: {}, Start Time: {}, Duration: {} (Terminated)".format(
                                        job_queue_name, job_name, job_id, status, started_at, duration)
                                    send_sns_notification(sns_topic_arn, message)
                            else:
                                output += "- Job Queue: {}, Job Name: {}, ID: {}, Status: {}, Start Time: Not available\n".format(
                                    job_queue_name, job_name, job_id, status)
                    else:
                        output += f"{job_queue_name}: No running jobs.\n"
                else:
                    output += f"{job_queue_name}: No running jobs.\n"
        else:
            output = "No job queues found.\n"
    else:
        output = "No job queues found.\n"

    return output

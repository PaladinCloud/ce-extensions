
# CE Extensions

This repository contains standalone extensions for CE.

## Local Setup for Paladin Cloud Integration

To run the projects locally using your personal AWS account, follow the steps below to assume the `PaladinCloudIntegrationRole` in the SaaS AWS account.

### Prerequisites
- Access to the SaaS AWS account.
- AWS CLI installed and configured on your local machine.
- IDE set up with this project.

### Steps

1. **Get your AWS Account ARN**

    - Open a terminal.
    - Run the following AWS CLI command to retrieve your AWS account ARN:
      '''bash
      aws sts get-caller-identity
      '''
      This will return your account details, including the ARN you’ll need for the next steps.

2. **Modify the Trust Policy in the SaaS Account**

    - Open your browser and log in to the SaaS AWS console.
    - In the console, navigate to **IAM** and find the role `PaladinCloudIntegrationRole`.
    - Follow these steps:
        1. Click on the **Trust relationships** tab.
        2. Click on **Edit trust policy**.
        3. In the `Statement` array, add the following entry, replacing `<your personal account arn>` with the ARN you retrieved earlier:
           '''json
           {
           "Effect": "Allow",
           "Principal": {
           "AWS": "<your personal account arn goes here>"
           },
           "Action": "sts:AssumeRole"
           }
           '''
        4. Click **Update policy** to save the changes.

3. **Run the Project Locally**

    - After updating the trust policy, you can run the project locally.
    - You will now be able to assume the `PaladinCloudIntegrationRole` in the SaaS account using your personal AWS account.

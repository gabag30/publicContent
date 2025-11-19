# CDK for ECS deployment

This CDK app (Python) provisions an ECS (EC2) cluster and deploys containers roughly matching your `docker-compose.yml`:

- n8n
- postgres (pgvector)
- open-webui
- pgadmin
- ollama (CPU)

Storage is backed by a single EFS filesystem with separate directories for each service's persistent data. Services discover each other via Cloud Map DNS (e.g., `postgres.local`).

Notes:

- Instances are `t2.micro` by default (very small; expect tight CPU/memory). Increase if tasks fail to place.
- No public load balancers are created. Endpoints are reachable inside the VPC using the Cloud Map names (e.g., `n8n.local:5678`). To expose publicly, add an ALB/NLB per service as desired.
- Helper one-off init containers from compose (e.g., data copy, pgadmin restore, ollama pull) are not included in this initial version.

## Prereqs

- Python 3.10+
- AWS CDK v2 CLI installed: `npm i -g aws-cdk`
- Bootstrapped environment: `cdk bootstrap` (once per account/region)

## Install, synth, deploy

```
cd cdk
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cdk synth
cdk deploy
```

Outputs will display the Cloud Map endpoints. From tasks inside the VPC you can reach, for example, `postgres.local:5432`.

## Configure Account/Region and Capacity

Adjust `cdk/config.json` to set your AWS account, region, and ECS capacity:

```
{
  "account": "123456789012",
  "region": "us-east-1",
  "ecs": { "instanceType": "t2.micro", "desiredCapacity": 1, "maxCapacity": 2 },
  "persistence": { "useEfs": true },
  "namespace": "local"
}
```

If `account`/`region` are blank, CDK uses your environment (`CDK_DEFAULT_ACCOUNT` / `CDK_DEFAULT_REGION`).

## Tweaks

- To change instance size or count, update `AsgCapacity` in `lib/sample-docker-n8n-ecs-stack.ts`.
- To add public access, wrap services with ALBs (`ecs-patterns` `ApplicationLoadBalancedEc2Service`) or attach Target Groups and Listeners.
- Secrets: Postgres password and n8n encryption key are generated and stored in Secrets Manager.

## SSM Port Forwarding (no public ALB)

This stack enables SSM on the ECS instances. You can forward local ports to task ENIs via Session Manager.

1) Find the EC2 instance ID (container instance) and task ENI IP

```
CLUSTER=SampleDockerN8nEcsStack-Cluster* # adjust to your stack

# Get the container instance backing the service
aws ecs list-container-instances --cluster $CLUSTER \
  --query 'containerInstanceArns[0]' --output text | \
  xargs -I {} aws ecs describe-container-instances --cluster $CLUSTER \
    --container-instances {} \
    --query 'containerInstances[0].ec2InstanceId' --output text

# Get a task ENI IP for n8n (similar for others: postgres, open-webui, pgadmin, ollama)
TASK_ARN=$(aws ecs list-tasks --cluster $CLUSTER --service-name N8nService \
  --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster $CLUSTER --tasks $TASK_ARN \
  --query 'tasks[0].attachments[?type==`ElasticNetworkInterface`].details[?name==`networkInterfaceId`].value' \
  --output text)
aws ec2 describe-network-interfaces --network-interface-ids $ENI \
  --query 'NetworkInterfaces[0].PrivateIpAddress' --output text
```

2) Start an SSM port forward to the task ENI IP

Use the SSM document `AWS-StartPortForwardingSessionToRemoteHost` to forward a local port to the task ENI IP and target port.

```
INSTANCE_ID=i-xxxxxxxxxxxxxxxxx   # from step 1
TARGET_IP=10.0.1.xx               # from step 1

# n8n (5678)
aws ssm start-session \
  --target $INSTANCE_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters host=$TARGET_IP,portNumber=5678,localPortNumber=5678

# postgres (5432)
aws ssm start-session \
  --target $INSTANCE_ID \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters host=$TARGET_IP,portNumber=5432,localPortNumber=5433

# open-webui (8080), pgadmin (80), ollama (11434) follow the same pattern
```

You can open multiple sessions in separate terminals, mapping each service’s port to your local machine.

## Data Persistence (EFS vs EBS)

- By default, persistent directories are stored on an EFS filesystem and mounted into containers (recommended). This keeps data available across EC2 instances and cluster restarts.
- Set `persistence.useEfs` to `false` to fall back to ephemeral empty volumes (data will not persist).
- EBS is per-instance. To use EBS, you would attach a volume to the Auto Scaling Group instances and mount it in user data, then map ECS task volumes to host bind mounts. This couples data to a single EC2 instance and is not shared across the cluster. If you want this, say the word and I can add an EBS-backed host path option, but EFS is the safer default for shared persistence.

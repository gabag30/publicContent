#!/usr/bin/env python3
import os
import json
import aws_cdk as cdk

from sample_docker_n8n_ecs.sample_docker_n8n_ecs_stack import SampleDockerN8nEcsStack


def load_config(path: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


config = load_config(os.path.join(os.path.dirname(__file__), "config.json"))

account = (config.get("account") or os.environ.get("CDK_DEFAULT_ACCOUNT"))
region = (config.get("region") or os.environ.get("CDK_DEFAULT_REGION"))

ecs_cfg = config.get("ecs", {})
instance_type = ecs_cfg.get("instanceType", "t2.micro")
desired_capacity = int(ecs_cfg.get("desiredCapacity", 1))
max_capacity = int(ecs_cfg.get("maxCapacity", 2))

namespace = config.get("namespace", "local")
use_efs = bool(config.get("persistence", {}).get("useEfs", True))

app = cdk.App()

SampleDockerN8nEcsStack(
    app,
    "SampleDockerN8nEcsStack",
    env=cdk.Environment(account=account, region=region),
    instance_type_str=instance_type,
    desired_capacity=desired_capacity,
    max_capacity=max_capacity,
    namespace=namespace,
    use_efs=use_efs,
)

app.synth()

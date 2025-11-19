from aws_cdk import (
    Stack,
    Duration,
    RemovalPolicy,
    CfnOutput,
)
from constructs import Construct
from aws_cdk import aws_ec2 as ec2
from aws_cdk import aws_ecs as ecs
from aws_cdk import aws_efs as efs
from aws_cdk import aws_servicediscovery as servicediscovery
from aws_cdk import aws_secretsmanager as secretsmanager
from aws_cdk import aws_autoscaling as autoscaling
from aws_cdk import aws_iam as iam


class SampleDockerN8nEcsStack(Stack):
    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        instance_type_str: str = "t2.micro",
        desired_capacity: int = 1,
        max_capacity: int = 2,
        namespace: str = "local",
        use_efs: bool = True,
        **kwargs,
    ) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # VPC with only public subnets
        vpc = ec2.Vpc(
            self,
            "Vpc",
            nat_gateways=0,
            max_azs=2,
            subnet_configuration=[
                ec2.SubnetConfiguration(name="Public", subnet_type=ec2.SubnetType.PUBLIC)
            ],
        )

        # SGs
        ecs_sg = ec2.SecurityGroup(
            self,
            "EcsInstancesSg",
            vpc=vpc,
            description="Security group for ECS EC2 instances",
            allow_all_outbound=True,
        )

        efs_sg = ec2.SecurityGroup(
            self,
            "EfsSg",
            vpc=vpc,
            description="EFS SG allowing NFS from ECS instances",
        )

        # EFS filesystem
        fs = None
        if use_efs:
            fs = efs.FileSystem(
                self,
                "AppEfs",
                vpc=vpc,
                security_group=efs_sg,
                removal_policy=RemovalPolicy.RETAIN,
                performance_mode=efs.PerformanceMode.GENERAL_PURPOSE,
                throughput_mode=efs.ThroughputMode.BURSTING,
            )
            fs.connections.allow_default_port_from(ecs_sg, "Allow NFS from ECS instances")

        # ECS cluster with Cloud Map
        cluster = ecs.Cluster(
            self,
            "Cluster",
            vpc=vpc,
            container_insights=True,
            default_cloud_map_namespace=ecs.CloudMapNamespaceOptions(
                name=namespace, type=servicediscovery.NamespaceType.DNS_PRIVATE
            ),
        )

        # Instance role for ASG (SSM + ECS)
        instance_role = iam.Role(
            self, "AsgInstanceRole", assumed_by=iam.ServicePrincipal("ec2.amazonaws.com")
        )
        instance_role.add_managed_policy(
            iam.ManagedPolicy.from_aws_managed_policy_name("AmazonSSMManagedInstanceCore")
        )
        instance_role.add_managed_policy(
            iam.ManagedPolicy.from_aws_managed_policy_name(
                "AmazonEC2ContainerServiceforEC2Role"
            )
        )

        user_data = ec2.UserData.for_linux()
        user_data.add_commands(
            "yum install -y amazon-ssm-agent amazon-efs-utils || true",
            "systemctl enable amazon-ssm-agent || true",
            "systemctl start amazon-ssm-agent || true",
        )

        # ASG and Capacity Provider (t2.micro)
        asg = autoscaling.AutoScalingGroup(
            self,
            "ClusterAsg",
            vpc=vpc,
            instance_type=ec2.InstanceType(instance_type_str),
            machine_image=ecs.EcsOptimizedImage.amazon_linux2(),
            min_capacity=1,
            max_capacity=max_capacity,
            desired_capacity=desired_capacity,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
            associate_public_ip_address=True,
            security_group=ecs_sg,
            role=instance_role,
            user_data=user_data,
        )

        capacity_provider = ecs.AsgCapacityProvider(
            self, "AsgCapacityProvider", auto_scaling_group=asg, enable_managed_termination_protection=False
        )
        cluster.add_asg_capacity_provider(capacity_provider)

        # Secrets
        postgres_password = secretsmanager.Secret(
            self,
            "PostgresPassword",
            secret_name=f"{self.stack_name}/postgres/password",
            generate_secret_string=secretsmanager.SecretStringGenerator(
                secret_string_template='{"username":"postgres"}',
                generate_string_key="password",
                exclude_punctuation=True,
            ),
        )

        n8n_encryption_key = secretsmanager.Secret(
            self,
            "N8nEncryptionKey",
            secret_name=f"{self.stack_name}/n8n/encryptionKey",
            generate_secret_string=secretsmanager.SecretStringGenerator(
                password_length=32, exclude_punctuation=True
            ),
        )

        # Helper to create an EFS-backed volume config pointing at a folder
        def data_volume(name: str, root_dir: str) -> ecs.Volume:
            if fs is None:
                # Fallback to ephemeral empty volumes if EFS disabled
                return ecs.Volume(name=name)
            return ecs.Volume(
                name=name,
                efs_volume_configuration=ecs.EfsVolumeConfiguration(
                    file_system_id=fs.file_system_id,
                    root_directory=root_dir,
                    transit_encryption="ENABLED",
                ),
            )

        # Postgres service (pgvector)
        pg_task = ecs.Ec2TaskDefinition(self, "PgTask", network_mode=ecs.NetworkMode.AWS_VPC)
        pg_vol = data_volume("pgdata", "/postgres")
        pg_task.add_volume(pg_vol)
        pg_container = pg_task.add_container(
            "Postgres",
            image=ecs.ContainerImage.from_registry("pgvector/pgvector:0.8.1-pg18"),
            cpu=128,
            memory_reservation_mib=256,
            command=["-c", "max_connections=500", "-c", "idle_session_timeout=5000"],
            environment={
                "POSTGRES_USER": "postgres",
                "POSTGRES_DB": "postgres",
            },
            secrets={
                "POSTGRES_PASSWORD": ecs.Secret.from_secrets_manager(
                    postgres_password, field="password"
                )
            },
            health_check=ecs.HealthCheck(
                command=["CMD-SHELL", "pg_isready -U postgres"],
                interval=Duration.seconds(10),
                start_period=Duration.seconds(30),
                timeout=Duration.seconds(5),
                retries=3,
            ),
            logging=ecs.LogDrivers.aws_logs(stream_prefix="postgres"),
        )
        pg_container.add_port_mappings(ecs.PortMapping(container_port=5432))
        pg_container.add_mount_points(
            ecs.MountPoint(container_path="/var/lib/postgresql", source_volume=pg_vol.name, read_only=False)
        )
        pg_service = ecs.Ec2Service(
            self,
            "PgService",
            cluster=cluster,
            task_definition=pg_task,
            desired_count=1,
            cloud_map_options=ecs.CloudMapOptions(name="postgres"),
            security_groups=[ecs_sg],
            assign_public_ip=True,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
        )

        # n8n service
        n8n_task = ecs.Ec2TaskDefinition(self, "N8nTask", network_mode=ecs.NetworkMode.AWS_VPC)
        n8n_data_vol = data_volume("n8ndata", "/n8n")
        n8n_task.add_volume(n8n_data_vol)
        shared_vol = data_volume("shared", "/shared")
        n8n_task.add_volume(shared_vol)

        n8n_container = n8n_task.add_container(
            "N8n",
            image=ecs.ContainerImage.from_registry("n8nio/n8n:latest"),
            cpu=128,
            memory_reservation_mib=256,
            environment={
                "DB_TYPE": "postgresdb",
                "DB_POSTGRESDB_HOST": "postgres.local",
                "DB_POSTGRESDB_USER": "postgres",
                "DB_POSTGRESDB_DATABASE": "postgres",
                "N8N_DIAGNOSTICS_ENABLED": "false",
                "N8N_PERSONALIZATION_ENABLED": "false",
                "N8N_ALLOW_EXTERNAL_MODULES": "*",
                "N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS": "true",
                "WEBHOOK_URL": "http://localhost:5678",
            },
            secrets={
                "DB_POSTGRESDB_PASSWORD": ecs.Secret.from_secrets_manager(
                    postgres_password, field="password"
                ),
                "N8N_ENCRYPTION_KEY": ecs.Secret.from_secrets_manager(n8n_encryption_key),
            },
            logging=ecs.LogDrivers.aws_logs(stream_prefix="n8n"),
        )
        n8n_container.add_port_mappings(ecs.PortMapping(container_port=5678))
        n8n_container.add_mount_points(
            ecs.MountPoint(container_path="/home/node/.n8n", source_volume=n8n_data_vol.name, read_only=False),
            ecs.MountPoint(container_path="/data/shared", source_volume=shared_vol.name, read_only=False),
        )
        n8n_service = ecs.Ec2Service(
            self,
            "N8nService",
            cluster=cluster,
            task_definition=n8n_task,
            desired_count=1,
            cloud_map_options=ecs.CloudMapOptions(name="n8n"),
            security_groups=[ecs_sg],
            assign_public_ip=True,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
        )
        n8n_service.node.add_dependency(pg_service)

        # open-webui service
        webui_task = ecs.Ec2TaskDefinition(self, "OpenWebuiTask", network_mode=ecs.NetworkMode.AWS_VPC)
        webui_vol = data_volume("openwebui", "/open-webui")
        webui_task.add_volume(webui_vol)
        webui_container = webui_task.add_container(
            "OpenWebui",
            image=ecs.ContainerImage.from_registry("ghcr.io/open-webui/open-webui:main"),
            cpu=128,
            memory_reservation_mib=192,
            logging=ecs.LogDrivers.aws_logs(stream_prefix="open-webui"),
        )
        webui_container.add_port_mappings(ecs.PortMapping(container_port=8080))
        webui_container.add_mount_points(
            ecs.MountPoint(container_path="/app/backend/data", source_volume=webui_vol.name, read_only=False)
        )
        webui_service = ecs.Ec2Service(
            self,
            "OpenWebuiService",
            cluster=cluster,
            task_definition=webui_task,
            desired_count=1,
            cloud_map_options=ecs.CloudMapOptions(name="open-webui"),
            security_groups=[ecs_sg],
            assign_public_ip=True,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
        )

        # pgadmin service
        pgadmin_task = ecs.Ec2TaskDefinition(self, "PgAdminTask", network_mode=ecs.NetworkMode.AWS_VPC)
        pgadmin_vol = data_volume("pgadmin", "/pgadmin")
        pgadmin_task.add_volume(pgadmin_vol)
        pgadmin_container = pgadmin_task.add_container(
            "PgAdmin",
            image=ecs.ContainerImage.from_registry("dpage/pgadmin4:latest"),
            cpu=128,
            memory_reservation_mib=128,
            environment={
                "PGADMIN_DEFAULT_EMAIL": "admin@admin.com",
                "PGADMIN_DEFAULT_PASSWORD": "admin",
            },
            logging=ecs.LogDrivers.aws_logs(stream_prefix="pgadmin"),
        )
        pgadmin_container.add_port_mappings(ecs.PortMapping(container_port=80))
        pgadmin_container.add_mount_points(
            ecs.MountPoint(container_path="/var/lib/pgadmin", source_volume=pgadmin_vol.name, read_only=False)
        )
        pgadmin_service = ecs.Ec2Service(
            self,
            "PgAdminService",
            cluster=cluster,
            task_definition=pgadmin_task,
            desired_count=1,
            cloud_map_options=ecs.CloudMapOptions(name="pgadmin"),
            security_groups=[ecs_sg],
            assign_public_ip=True,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
        )

        # Ollama (CPU)
        ollama_task = ecs.Ec2TaskDefinition(self, "OllamaTask", network_mode=ecs.NetworkMode.AWS_VPC)
        ollama_vol = data_volume("ollama", "/ollama")
        ollama_task.add_volume(ollama_vol)
        ollama_container = ollama_task.add_container(
            "Ollama",
            image=ecs.ContainerImage.from_registry("ollama/ollama:latest"),
            cpu=128,
            memory_reservation_mib=128,
            environment={
                "OLLAMA_CONTEXT_LENGTH": "8192",
                "OLLAMA_FLASH_ATTENTION": "1",
                "OLLAMA_KV_CACHE_TYPE": "q8_0",
                "OLLAMA_MAX_LOADED_MODELS": "2",
                "GODEBUG": "http2client=0",
            },
            logging=ecs.LogDrivers.aws_logs(stream_prefix="ollama"),
        )
        ollama_container.add_port_mappings(ecs.PortMapping(container_port=11434))
        ollama_container.add_mount_points(
            ecs.MountPoint(container_path="/root/.ollama", source_volume=ollama_vol.name, read_only=False)
        )
        ollama_service = ecs.Ec2Service(
            self,
            "OllamaService",
            cluster=cluster,
            task_definition=ollama_task,
            desired_count=1,
            cloud_map_options=ecs.CloudMapOptions(name="ollama"),
            security_groups=[ecs_sg],
            assign_public_ip=True,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
        )

        # Outputs
        CfnOutput(self, "ServiceDiscoveryNamespace", value=namespace)
        CfnOutput(self, "PostgresEndpoint", value="postgres.local:5432")
        CfnOutput(self, "N8nEndpoint", value="n8n.local:5678")
        CfnOutput(self, "OpenWebUIEndpoint", value="open-webui.local:8080")
        CfnOutput(self, "PgAdminEndpoint", value="pgadmin.local:80")
        CfnOutput(self, "OllamaEndpoint", value="ollama.local:11434")
        if fs is not None:
            CfnOutput(self, "EfsFileSystemId", value=fs.file_system_id)

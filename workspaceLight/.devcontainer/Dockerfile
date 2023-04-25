
#-------------------------------------------------------------------------------------------------------------
# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License. See https://go.microsoft.com/fwlink/?linkid=2090316 for license information.
#-------------------------------------------------------------------------------------------------------------

# Note: You can use any Debian/Ubuntu based image you want. 
# the --platform=linux/amd64 is to deal with the Apple M1 chip and the issues of docker
FROM --platform=linux/amd64 alpine:3.15
# Use a minimal Alpine image

# Avoid warnings by switching to noninteractive
ENV DEBIAN_FRONTEND=noninteractive

# This Dockerfile adds a non-root user with sudo access. Use the "remoteUser"
# property in devcontainer.json to use it.
ARG USERNAME=vscode
ARG USER_UID=1000
ARG USER_GID=$USER_UID

# Install required packages
RUN apk add --no-cache \
	--allow-untrusted \
  	--repository http://dl-cdn.alpinelinux.org/alpine/v3.15/main \
	--repository http://dl-cdn.alpinelinux.org/alpine/v3.15/community \
    bash \
    sudo \
    curl \
    jq \
    nodejs \
    npm \
    openjdk11 \
    maven 

# Create a non-root user to use if preferred
RUN addgroup --gid $USER_GID $USERNAME \
    && adduser --uid $USER_UID --ingroup $USERNAME --home /home/$USERNAME --shell /bin/bash --disabled-password $USERNAME \
    && echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME \
    && mkdir -p /home/$USERNAME/.vscode-server/extensions \
        /home/$USERNAME/.vscode-server-insiders/extensions \
    && chown -R $USERNAME:$USERNAME /home/$USERNAME

USER $USERNAME

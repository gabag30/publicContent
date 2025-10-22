#!/bin/bash

#### update git
#cd <git folder>
git pull --all
git add --all
git commit -m "saving changes $(date +'%m_%d_%Y_%H_%M')"
git push --all

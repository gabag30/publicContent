#!/bin/bash

# check if code-insiders update was already performed today
if [[ -f "/usr/share/code-insiders/code-insiders" ]]; then
  update_date=$(date -r /usr/share/code-insiders/code\-insiders +%F)
  today_date=$(date +%F)
  if [[ $update_date == $today_date ]]; then
    echo "Code Insiders update already performed today. Skipping update."
  else
    echo "Code Insiders update not performed today. Updating now..."
    # update visual code insiders
    wget --no-check-certificate "https://code.visualstudio.com/sha/download?build=insider&os=linux-rpm-x64" -cO insiders.rpm
    sudo yum localinstall ./insiders.rpm -y
    sudo chown <user>:<group> /usr/share/code-insiders
    rm insiders.rpm -rf
  fi
else
  echo "Code Insiders not installed. Installing now..."
  # install visual code insiders
  wget --no-check-certificate "https://code.visualstudio.com/sha/download?build=insider&os=linux-rpm-x64" -cO insiders.rpm
  sudo yum localinstall ./insiders.rpm -y
  sudo chown <user>:<group> /usr/share/code-insiders
  rm insiders.rpm -rf
fi

read -p "Do you want to upgrade the remaining software? (y/n)" choice
case "$choice" in
  y|Y )
    # update postman
    wget --no-check-certificate "https://dl.pstmn.io/download/latest/linux64" -cO postman.tar.gz
    sudo tar -xf postman.tar.gz --directory /opt
    sudo chown <user>:<group> /opt/Postman -R
    rm postman.tar.gz
    
    # update dbeaver
    wget --no-check-certificate "https://dbeaver.io/files/dbeaver-ce-latest-stable.x86_64.rpm" -cO dbeaver.rpm
    sudo yum localinstall ./dbeaver.rpm -y
    rm dbeaver.rpm -rf
    sudo chown <user>:<group> /usr/share/dbeaver-ce -R
    
    # update hyper command line
    wget --no-check-certificate "https://releases.hyper.is/download/rpm" -cO hyper.rpm
    sudo yum localinstall ./hyper.rpm -y
    rm hyper.rpm -rf
    sudo chown <user>:<group> /opt/Hyper -R
    
    # install and update jd-gui
    wget --no-check-certificate "https://github.com/java-decompiler/jd-gui/releases/download/v1.6.6/jd-gui-1.6.6.rpm" -cO jd-gui.rpm
    sudo yum localinstall ./jd-gui.rpm -y
    rm jd-gui.rpm -rf
    sudo chown <user>:<group> /opt/jd-gui -R
    ;;
  n|N )
    echo "Software upgrade skipped."
    ;;
  * )
    echo "Invalid input. Software upgrade skipped."
    ;;
esac

# update git
cd <git folder>
git pull --all
git add --all
git commit -m "saving changes $(date +'%m_%d_%Y_%H_%M')"
git push --all



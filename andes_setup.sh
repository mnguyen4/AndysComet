#!/bin/sh

# Easy setup for Andes on Ubuntu
# Be sure to run this on the Desktop

# Getting all the required stuff
sudo apt-get install git make subversion g++ libmysqlclient-dev mysql-server \
                apache2 libapache2-mod-proxy-html wget php5 php5-mysql

echo Installing andes at: $(pwd)/andes

git clone git://github.com/bvds/andes.git andes

echo $(pwd)/andes/ created.

cd andes

git checkout --track -b stable origin/stable

# At this point, we have the stable branch downloaded. Time to install


# Install sbcl
cd lisp-site-install
sudo make get-binary
sudo make get-source
sudo make source-install
cd ..
sudo make sbclrc

# Configure the database
# Need to configure MySQL with proper user/pass
#/usr/bin/mysqladmin -u root password 'mysql-password'
#/sbin/service mysqld restart
sudo make install-database

# Configure apache2
sudo make configure-httpd

# Install dojo
make install-dojo

echo Copy the problems and solutions to /andes and do make install-solver

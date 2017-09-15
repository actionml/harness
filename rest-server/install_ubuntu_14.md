# Install Harness on Ubuntu 14.04 From Source

##Warning: Vowpal Wabbit is no longer buildable on Ubuntu 14.04 so see installation.md in the harnes directory##

Due to a limitation in the Java JNI build of VW the Contextual Bandit is not easy to get running on newer versions of Ubuntu but Harness requires newer versions of tools than are standard on Ubuntu 14.04. So here is how to install on 14.04. Many steps would be required on any OS

We start from a clean new machine logged into the user account `aml`

- Setup user permissions and login

    ```
    cd ~
    ssh-keygen # get the ~/.ssh directory setup
    nano .ssh/authorized_keys # add your public key to the file
    sudo nano /etc/sudoers # make the sudo passwordless based on being in sudo group
    chmod 600 .ssh/authorized_keys 
    ```
- Install tools needed

    ```
    sudo apt-get install git
    sudo ln -s -f /usr/bin/python3 /usr/bin/python # make python3 the default
    sudo easy_install3 pip
    sudo pip install harness # get the harness python sdk
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10
    echo "deb http://repo.mongodb.org/apt/ubuntu "$(lsb_release -sc)"/mongodb-org/3.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-3.0.list
    sudo apt-get update
    sudo apt-get install -y mongodb-org
    sudo add-apt-repository ppa:openjdk-r/ppa
    sudo apt-get update 
    sudo apt-get install openjdk-8-jdk
    java -version # should be 1.8
    # java won't run without trustAnchors configured
    sudo /var/lib/dpkg/info/ca-certificates-java.postinst configure
    ```
- Get Harness, build, startup, attach a Template, watch logs

    ```
    git clone https://github.com/actionml/harness.git 
    cd harness/rest-server
    ./make-distribution.sh 
    tar zxvf Harness-0.1.0-SNAPSHOT.tar.gz
    cd Harness-0.1.0-SNAPSHOT/dist/bin
    nano harness-env # change listening to 0.0.0.0 from localhost
    nano ../../drivers/engine.json # put model file in desired location
    ./harness start
    ./harness add ../../drivers/engine.json 
    ./harness status
    curl localhost:9090 # should get "OK"
    tail -f -n 200 /home/aml/harness/rest-server/Harness-0.1.0-SNAPSHOT/dist/logs/main.log
    ```
